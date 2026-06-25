import 'dotenv/config';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import format from 'pg-format';
import { pool } from './db.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const API = 'https://db.ygoprodeck.com/api/v7';
// Base de traducciones de la comunidad (YGOrganization): índices nombre -> [konamiId].
const YGORESOURCES = 'https://db.ygoresources.com';

/** Divide un array en trozos de tamaño n (para insertar por lotes). */
function chunk(arr, n) {
    const out = [];
    for (let i = 0; i < arr.length; i += n) out.push(arr.slice(i, i + n));
    return out;
}

/** Crea las tablas/índices si no existen. */
async function ensureSchema() {
    const sql = await readFile(join(__dirname, '..', 'db', 'schema.sql'), 'utf8');
    await pool.query(sql);
    console.log('Esquema verificado.');
}

async function importCards() {
    console.log('Descargando todas las cartas de YGOPRODeck (puede tardar)...');
    // misc=yes añade misc_info[0].konami_id, que usamos para cruzar las traducciones.
    const res = await fetch(`${API}/cardinfo.php?misc=yes`);
    if (!res.ok) throw new Error(`cardinfo.php devolvió ${res.status}`);
    const { data } = await res.json();
    console.log(`Recibidas ${data.length} cartas.`);

    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        // Re-importación limpia.
        await client.query('TRUNCATE card_sets, cards RESTART IDENTITY CASCADE');

        for (const part of chunk(data, 500)) {
            const filas = part.map((c) => [
                c.id,
                c.name,
                c.misc_info?.[0]?.konami_id ?? null,
                c.type ?? null,
                c.frameType ?? null,
                c.desc ?? null,
                c.atk ?? null,
                c.def ?? null,
                c.level ?? null,
                c.race ?? null,
                c.attribute ?? null,
                c.archetype ?? null,
                c.card_images?.[0]?.image_url ?? null,
                c.card_images?.[0]?.image_url_small ?? null,
            ]);
            await client.query(
                format(
                    `INSERT INTO cards
                     (id,name,konami_id,type,frame_type,description,atk,def,level,race,attribute,archetype,image_url,image_url_small)
                     VALUES %L ON CONFLICT (id) DO NOTHING`,
                    filas
                )
            );
        }

        // Relaciones carta-set.
        const setRows = [];
        for (const c of data) {
            for (const s of c.card_sets ?? []) {
                setRows.push([c.id, s.set_name, s.set_code ?? null, s.set_rarity ?? null]);
            }
        }
        for (const part of chunk(setRows, 1000)) {
            await client.query(
                format(`INSERT INTO card_sets (card_id,set_name,set_code,set_rarity) VALUES %L`, part)
            );
        }

        await client.query('COMMIT');
        console.log(`Insertadas ${data.length} cartas y ${setRows.length} relaciones de set.`);
    } catch (e) {
        await client.query('ROLLBACK');
        throw e;
    } finally {
        client.release();
    }
}

async function importSets() {
    console.log('Descargando catálogo de sets...');
    const res = await fetch(`${API}/cardsets.php`);
    if (!res.ok) throw new Error(`cardsets.php devolvió ${res.status}`);
    const sets = await res.json();

    const filas = sets.map((s) => [
        s.set_name,
        s.set_code ?? null,
        Number(s.num_of_cards) || 0,
        s.tcg_date || null,
    ]);

    const client = await pool.connect();
    try {
        await client.query('BEGIN');
        await client.query('TRUNCATE sets');
        for (const part of chunk(filas, 1000)) {
            await client.query(
                format(
                    `INSERT INTO sets (set_name,set_code,num_of_cards,tcg_date) VALUES %L
                     ON CONFLICT (set_name) DO UPDATE SET num_of_cards = EXCLUDED.num_of_cards`,
                    part
                )
            );
        }
        await client.query('COMMIT');
        console.log(`Insertados ${filas.length} sets.`);
    } catch (e) {
        await client.query('ROLLBACK');
        throw e;
    } finally {
        client.release();
    }
}

/**
 * Rellena la columna name_es con el nombre español oficial de cada carta.
 * Cruza los índices EN/ES de YGOResources con nuestras cartas en dos pasos:
 *   1) por konami_id (id oficial de Konami): el más fiable, no depende de la ortografía.
 *   2) por nombre en inglés: respaldo para las que aún no tengan traducción.
 */
async function importSpanishNames() {
    console.log('Descargando nombres EN/ES de YGOResources...');
    const [enRes, esRes] = await Promise.all([
        fetch(`${YGORESOURCES}/data/idx/card/name/en`),
        fetch(`${YGORESOURCES}/data/idx/card/name/es`),
    ]);
    if (!enRes.ok || !esRes.ok) throw new Error('YGOResources no respondió');
    const enIdx = await enRes.json(); // { "English Name": [konamiId, ...] }
    const esIdx = await esRes.json(); // { "Nombre Español": [konamiId, ...] }

    // konamiId -> nombre español.
    const esPorKid = new Map();
    for (const [nombre, kids] of Object.entries(esIdx)) {
        for (const kid of kids) if (!esPorKid.has(kid)) esPorKid.set(kid, nombre);
    }

    const client = await pool.connect();
    try {
        await client.query('BEGIN');

        // PASO 1: cruce por konami_id.
        const paresKid = [...esPorKid.entries()]; // [[konamiId, nombreEs], ...]
        let porKid = 0;
        for (const part of chunk(paresKid, 1000)) {
            const r = await client.query(
                format(
                    `UPDATE cards c SET name_es = v.es
                     FROM (VALUES %L) AS v(kid, es)
                     WHERE c.konami_id = v.kid::int AND c.name_es IS NULL`,
                    part
                )
            );
            porKid += r.rowCount;
        }

        // PASO 2: respaldo por nombre inglés para las que sigan sin traducción.
        const paresNombre = [];
        for (const [nombreEn, kids] of Object.entries(enIdx)) {
            const nombreEs = kids.map((k) => esPorKid.get(k)).find(Boolean);
            if (nombreEs) paresNombre.push([nombreEn, nombreEs]);
        }
        let porNombre = 0;
        for (const part of chunk(paresNombre, 1000)) {
            const r = await client.query(
                format(
                    `UPDATE cards c SET name_es = v.es
                     FROM (VALUES %L) AS v(en, es)
                     WHERE c.name = v.en AND c.name_es IS NULL`,
                    part
                )
            );
            porNombre += r.rowCount;
        }

        await client.query('COMMIT');
        console.log(`name_es: ${porKid} por konami_id + ${porNombre} por nombre = ${porKid + porNombre} cartas.`);
    } catch (e) {
        await client.query('ROLLBACK');
        throw e;
    } finally {
        client.release();
    }
}

async function main() {
    const inicio = Date.now();
    await ensureSchema();
    await importCards();
    await importSets();
    await importSpanishNames();
    await pool.end();
    console.log(`✅ Importación completa en ${((Date.now() - inicio) / 1000).toFixed(1)}s.`);
}

main().catch((e) => {
    console.error('❌ Error en la importación:', e);
    process.exit(1);
});
