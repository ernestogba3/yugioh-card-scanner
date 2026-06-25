import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import { pool } from './db.js';

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;

// Columnas que devolvemos con el MISMO formato que YGOPRODeck,
// para que la app Android reutilice su modelo CartaYuGiOh sin cambios.
const SELECT_CARTA = `
    c.id, c.name, c.name_es, c.type, c.description AS "desc", c.atk, c.def, c.level, c.race, c.attribute,
    json_build_array(
        json_build_object('id', c.id, 'image_url', c.image_url, 'image_url_small', c.image_url_small)
    ) AS card_images,
    COALESCE(
        (SELECT json_agg(json_build_object('set_name', s.set_name, 'set_code', s.set_code))
         FROM card_sets s WHERE s.card_id = c.id),
        '[]'::json
    ) AS card_sets
`;

app.get('/health', (_req, res) => res.json({ ok: true }));

/**
 * GET /cards/search
 * Parámetros (todos opcionales, pero al menos uno):
 *   q          búsqueda fuzzy por nombre (tolera erratas del OCR)
 *   type       tipo exacto (ej. "Effect Monster")
 *   level      nivel exacto
 *   attribute  atributo (DARK, LIGHT, ...)
 *   atk, def   valor mínimo (>=)
 *   limit      máx. resultados (por defecto 20, tope 50)
 */
app.get('/cards/search', async (req, res) => {
    const q = (req.query.q ?? '').toString().trim();
    const limit = Math.min(Number(req.query.limit) || 20, 50);
    const { type, level, attribute, atk, def } = req.query;

    const where = [];
    const params = [];
    let scoreSelect = 'NULL::real AS score';
    let orderBy = 'c.name ASC';

    if (q) {
        params.push(q);
        const i = params.length;
        // Comparamos contra el nombre en INGLÉS y en ESPAÑOL (name_es), porque el OCR
        // puede leer la carta en cualquiera de los dos idiomas.
        // "%" usa el índice de trigramas; ILIKE cubre coincidencias por subcadena.
        where.push(`(
            c.name % $${i} OR c.name ILIKE '%' || $${i} || '%'
            OR c.name_es % $${i} OR c.name_es ILIKE '%' || $${i} || '%'
        )`);
        // GREATEST ignora los NULL, así que las cartas sin traducción no fallan.
        scoreSelect = `GREATEST(
            similarity(c.name, $${i}), word_similarity($${i}, c.name),
            similarity(c.name_es, $${i}), word_similarity($${i}, c.name_es)
        ) AS score`;
        orderBy = 'score DESC, length(c.name) ASC';
    }
    if (type) {
        params.push(type);
        where.push(`c.type = $${params.length}`);
    }
    if (attribute) {
        params.push(String(attribute).toUpperCase());
        where.push(`upper(c.attribute) = $${params.length}`);
    }
    if (level) {
        params.push(Number(level));
        where.push(`c.level = $${params.length}`);
    }
    if (atk) {
        params.push(Number(atk));
        where.push(`c.atk >= $${params.length}`);
    }
    if (def) {
        params.push(Number(def));
        where.push(`c.def >= $${params.length}`);
    }

    if (where.length === 0) {
        return res.status(400).json({ data: null, error: 'Indica al menos q o un filtro' });
    }

    params.push(limit);
    const sql = `
        SELECT ${SELECT_CARTA}, ${scoreSelect}
        FROM cards c
        WHERE ${where.join(' AND ')}
        ORDER BY ${orderBy}
        LIMIT $${params.length}
    `;

    try {
        const { rows } = await pool.query(sql, params);
        // Quitamos el "score" auxiliar antes de responder.
        const data = rows.map(({ score, ...carta }) => carta);
        res.json({ data, error: null });
    } catch (e) {
        console.error(e);
        res.status(500).json({ data: null, error: 'Error en la búsqueda' });
    }
});

/** GET /cards/:id — ficha de una carta concreta. */
app.get('/cards/:id', async (req, res) => {
    try {
        const { rows } = await pool.query(
            `SELECT ${SELECT_CARTA} FROM cards c WHERE c.id = $1`,
            [Number(req.params.id)]
        );
        if (rows.length === 0) return res.status(404).json({ data: null, error: 'No encontrada' });
        res.json({ data: rows, error: null });
    } catch (e) {
        console.error(e);
        res.status(500).json({ data: null, error: 'Error' });
    }
});

/** GET /sets — todos los sets con su número total de cartas (para porcentajes). */
app.get('/sets', async (_req, res) => {
    try {
        const { rows } = await pool.query(
            'SELECT set_name, num_of_cards FROM sets ORDER BY set_name'
        );
        res.json(rows);
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: 'Error' });
    }
});

app.listen(PORT, () => {
    console.log(`🚀 Backend escuchando en http://localhost:${PORT}`);
});
