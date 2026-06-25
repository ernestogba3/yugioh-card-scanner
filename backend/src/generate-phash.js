/**
 * Precalcula el pHash (perceptual hash) de CADA arte de CADA carta, para el fallback visual
 * del escáner cuando el passcode no se lee (foils, artes alternativos, cartas viejas).
 *
 * Lee el catálogo ya generado (`catalog.json`, que ahora incluye `images: [{artId, urlSmall}]`),
 * descarga cada imagen pequeña, calcula un pHash de 64 bits (DCT) y escribe:
 *     app/src/main/assets/database/phashes.json   ->  [{ artId, passcode, pHash }]
 *
 * El pHash es independiente del tamaño y robusto a brillo, así que basta con la imagen
 * pequeña (menos ancho de banda). En Android se compara por distancia de Hamming (Fase 2).
 *
 * Es una herramienta de BUILD (no necesita PostgreSQL ni el backend). Es RESUMABLE: si lo
 * cortas, al volver a lanzarlo salta los artes ya hasheados que hay en phashes.json.
 *
 * Uso:   cd backend  &&  npm run phash
 * Requiere la dependencia `sharp` (npm install).
 */
import { readFile, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import sharp from 'sharp';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DB_DIR = join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'database');
const CATALOG_FILE = join(DB_DIR, 'catalog.json');
const OUT_FILE = join(DB_DIR, 'phashes.json');

const CONCURRENCIA = 10;     // descargas en paralelo (amables con el CDN de imágenes)
const REINTENTOS = 3;
const GUARDAR_CADA = 500;    // checkpoint: vuelca phashes.json cada N artes nuevos

// --- pHash (DCT) de 64 bits ----------------------------------------------------------------

const TAM = 32;   // la imagen se reduce a 32x32 grises
const BLOQUE = 8; // se quedan las 8x8 frecuencias más bajas (las que definen la forma)

// Coeficientes del coseno precalculados para la DCT-II 1D de longitud TAM.
const COS = Array.from({ length: TAM }, (_, u) =>
    Array.from({ length: TAM }, (_, x) => Math.cos(((2 * x + 1) * u * Math.PI) / (2 * TAM))),
);

/** DCT-II 2D separable de una matriz TAM x TAM; devuelve solo el bloque BLOQUE x BLOQUE. */
function dct8x8(pixeles) {
    // Paso 1: DCT por filas -> temp[fila][u]
    const temp = Array.from({ length: TAM }, () => new Float64Array(BLOQUE));
    for (let y = 0; y < TAM; y++) {
        const fila = pixeles[y];
        for (let u = 0; u < BLOQUE; u++) {
            let s = 0;
            const cu = COS[u];
            for (let x = 0; x < TAM; x++) s += fila[x] * cu[x];
            temp[y][u] = s;
        }
    }
    // Paso 2: DCT por columnas sobre temp -> bloque[v][u]
    const bloque = new Float64Array(BLOQUE * BLOQUE);
    for (let u = 0; u < BLOQUE; u++) {
        for (let v = 0; v < BLOQUE; v++) {
            let s = 0;
            const cv = COS[v];
            for (let y = 0; y < TAM; y++) s += temp[y][u] * cv[y];
            bloque[v * BLOQUE + u] = s;
        }
    }
    return bloque;
}

/** Calcula el pHash (16 hex = 64 bits) de un buffer de imagen. */
async function phashDeImagen(buffer) {
    const datos = await sharp(buffer)
        .grayscale()
        .resize(TAM, TAM, { fit: 'fill' })
        .raw()
        .toBuffer(); // TAM*TAM bytes (1 canal)

    const pixeles = Array.from({ length: TAM }, (_, y) =>
        Array.from({ length: TAM }, (_, x) => datos[y * TAM + x]),
    );

    const bloque = dct8x8(pixeles);

    // Mediana de los 64 coeficientes EXCLUYENDO el término DC [0] (brillo medio).
    const sinDC = Array.from(bloque).slice(1).sort((a, b) => a - b);
    const mediana = (sinDC[31] + sinDC[32]) / 2;

    // Cada bit = 1 si el coeficiente supera la mediana. El bit 0 (DC) se fija a 0.
    let hex = '';
    for (let nibble = 0; nibble < 16; nibble++) {
        let v = 0;
        for (let b = 0; b < 4; b++) {
            const i = nibble * 4 + b;
            const bit = i === 0 ? 0 : bloque[i] > mediana ? 1 : 0;
            v = (v << 1) | bit;
        }
        hex += v.toString(16);
    }
    return hex;
}

// --- Descarga con reintentos ---------------------------------------------------------------

async function descargar(url) {
    for (let intento = 1; intento <= REINTENTOS; intento++) {
        try {
            const res = await fetch(url);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return Buffer.from(await res.arrayBuffer());
        } catch (e) {
            if (intento === REINTENTOS) throw e;
            await new Promise((r) => setTimeout(r, 400 * intento)); // backoff
        }
    }
}

// --- Programa principal --------------------------------------------------------------------

async function main() {
    const inicio = Date.now();

    if (!existsSync(CATALOG_FILE)) {
        console.error(`❌ No existe ${CATALOG_FILE}. Genera primero el catálogo: npm run export-catalog`);
        process.exit(1);
    }

    const catalogo = JSON.parse(await readFile(CATALOG_FILE, 'utf8'));

    // Lista única de artes a hashear: {artId, passcode, urlSmall}.
    const artesPorId = new Map();
    for (const carta of catalogo.cards) {
        for (const im of carta.images ?? []) {
            if (im.artId != null && im.urlSmall && !artesPorId.has(im.artId)) {
                artesPorId.set(im.artId, { artId: im.artId, passcode: carta.id, url: im.urlSmall });
            }
        }
    }
    const todos = [...artesPorId.values()];

    // Reanudar: cargar lo ya hecho.
    const resultados = new Map(); // artId -> { artId, passcode, pHash }
    if (existsSync(OUT_FILE)) {
        for (const r of JSON.parse(await readFile(OUT_FILE, 'utf8'))) resultados.set(r.artId, r);
        console.log(`Reanudando: ${resultados.size} artes ya hasheados.`);
    }

    const pendientes = todos.filter((a) => !resultados.has(a.artId));
    console.log(`Artes totales: ${todos.length}. Pendientes: ${pendientes.length}.`);
    if (pendientes.length === 0) {
        console.log('Nada que hacer. ✅');
        return;
    }

    let hechos = 0;
    let fallos = 0;
    let desdeUltimoGuardado = 0;

    const guardar = async () => {
        await writeFile(OUT_FILE, JSON.stringify([...resultados.values()]));
    };

    // Pool de trabajadores: CONCURRENCIA descargas a la vez.
    let cursor = 0;
    async function trabajador() {
        while (cursor < pendientes.length) {
            const arte = pendientes[cursor++];
            try {
                const buf = await descargar(arte.url);
                const pHash = await phashDeImagen(buf);
                resultados.set(arte.artId, { artId: arte.artId, passcode: arte.passcode, pHash });
            } catch (e) {
                fallos++;
            }
            hechos++;
            desdeUltimoGuardado++;
            if (hechos % 200 === 0 || hechos === pendientes.length) {
                const pct = ((hechos / pendientes.length) * 100).toFixed(1);
                process.stdout.write(`\r  ${hechos}/${pendientes.length} (${pct}%)  fallos: ${fallos}   `);
            }
            if (desdeUltimoGuardado >= GUARDAR_CADA) {
                desdeUltimoGuardado = 0;
                await guardar();
            }
        }
    }

    await Promise.all(Array.from({ length: CONCURRENCIA }, () => trabajador()));
    await guardar();

    const seg = ((Date.now() - inicio) / 1000).toFixed(1);
    console.log(`\n\n✅ pHashes escritos en:\n   ${OUT_FILE}`);
    console.log(`   ${resultados.size} artes hasheados (${fallos} fallos) en ${seg}s.`);
    if (fallos > 0) console.log('   Vuelve a ejecutar el comando para reintentar solo los fallidos.');
}

main().catch((e) => {
    console.error('\n❌ Error generando los pHash:', e);
    process.exit(1);
});
