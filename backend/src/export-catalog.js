/**
 * Genera el CATÁLOGO OFFLINE de la app: descarga todas las cartas de YGOPRODeck (con sus
 * nombres en español de YGOResources) y las vuelca a un único JSON dentro de los assets de
 * Android: `app/src/main/assets/database/catalog.json`.
 *
 * NO necesita PostgreSQL ni el backend encendido. Es una herramienta de BUILD: se ejecuta de
 * vez en cuando para regenerar el catálogo que viaja dentro del APK (estrategia B del
 * roadmap). La app lo importa a Room en el primer arranque (ver CatalogImporter.kt).
 *
 * Uso:   cd backend  &&  npm run export-catalog
 */
import { writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const API = 'https://db.ygoprodeck.com/api/v7';
// Índices nombre -> [konamiId] de la comunidad (YGOResources), para las traducciones.
const YGORESOURCES = 'https://db.ygoresources.com';

const __dirname = dirname(fileURLToPath(import.meta.url));
// backend/src -> raíz del repo -> app/src/main/assets/database/catalog.json
const OUT_DIR = join(__dirname, '..', '..', 'app', 'src', 'main', 'assets', 'database');
const OUT_FILE = join(OUT_DIR, 'catalog.json');
const MANIFEST_FILE = join(OUT_DIR, 'manifest.json');

/**
 * URL pública desde donde la app descargará catalog.json. En el GitHub Action se construye sola
 * a partir de GITHUB_REPOSITORY (owner/repo) y la rama; en local puedes fijarla con CATALOG_URL.
 */
function urlCatalogo() {
    if (process.env.CATALOG_URL) return process.env.CATALOG_URL;
    const repo = process.env.GITHUB_REPOSITORY; // "owner/repo" dentro del Action
    const rama = process.env.GITHUB_REF_NAME || 'main';
    if (repo) {
        return `https://raw.githubusercontent.com/${repo}/${rama}/app/src/main/assets/database/catalog.json`;
    }
    return 'https://raw.githubusercontent.com/OWNER/REPO/main/app/src/main/assets/database/catalog.json';
}

/** Descarga los índices EN/ES y devuelve konamiId -> nombreEs y nombreEn -> nombreEs. */
async function cargarTraducciones() {
    console.log('Descargando nombres EN/ES de YGOResources...');
    const [enRes, esRes] = await Promise.all([
        fetch(`${YGORESOURCES}/data/idx/card/name/en`),
        fetch(`${YGORESOURCES}/data/idx/card/name/es`),
    ]);
    if (!enRes.ok || !esRes.ok) throw new Error('YGOResources no respondió');
    const enIdx = await enRes.json(); // { "English Name": [konamiId, ...] }
    const esIdx = await esRes.json(); // { "Nombre Español": [konamiId, ...] }

    const esPorKid = new Map(); // konamiId -> nombreEs
    for (const [nombre, kids] of Object.entries(esIdx)) {
        for (const kid of kids) if (!esPorKid.has(kid)) esPorKid.set(kid, nombre);
    }
    const esPorEn = new Map(); // nombreEn -> nombreEs (respaldo)
    for (const [nombreEn, kids] of Object.entries(enIdx)) {
        const nombreEs = kids.map((k) => esPorKid.get(k)).find(Boolean);
        if (nombreEs) esPorEn.set(nombreEn, nombreEs);
    }
    return { esPorKid, esPorEn };
}

/** Resuelve el nombre español de una carta: primero por konami_id, luego por nombre inglés. */
function nombreEspanol(carta, esPorKid, esPorEn) {
    const kid = carta.misc_info?.[0]?.konami_id;
    return (kid != null ? esPorKid.get(kid) : null) ?? esPorEn.get(carta.name) ?? null;
}

/** Normaliza un precio de la API: devuelve el string solo si es un número > 0; si no, null. */
function precioValido(v) {
    if (v == null) return null;
    const n = parseFloat(v);
    return Number.isFinite(n) && n > 0 ? String(v) : null;
}

async function main() {
    const inicio = Date.now();
    const { esPorKid, esPorEn } = await cargarTraducciones();

    console.log('Descargando todas las cartas de YGOPRODeck (puede tardar)...');
    const cardsRes = await fetch(`${API}/cardinfo.php?misc=yes`);
    if (!cardsRes.ok) throw new Error(`cardinfo.php devolvió ${cardsRes.status}`);
    const { data } = await cardsRes.json();
    console.log(`Recibidas ${data.length} cartas.`);

    console.log('Descargando catálogo de sets...');
    const setsRes = await fetch(`${API}/cardsets.php`);
    if (!setsRes.ok) throw new Error(`cardsets.php devolvió ${setsRes.status}`);
    const setsRaw = await setsRes.json();

    // --- Construir el catálogo (campos cortos para reducir el tamaño del JSON) ---
    const sets = setsRaw.map((s) => ({
        setName: s.set_name,
        setCode: s.set_code ?? null,
        numOfCards: Number(s.num_of_cards) || 0,
    }));

    let conEs = 0;
    let totalPrints = 0;
    const cards = data.map((c) => {
        const nameEs = nombreEspanol(c, esPorKid, esPorEn);
        if (nameEs) conEs++;
        const prints = (c.card_sets ?? []).map((s) => {
            totalPrints++;
            return {
                code: s.set_code ?? '',
                set: s.set_name,
                rarity: s.set_rarity ?? null,
                price: precioValido(s.set_price), // TCGPlayer USD de esa impresión concreta
            };
        });
        // Precios promedio a nivel carta (un único objeto en card_prices).
        const cp = c.card_prices?.[0];
        // Todos los artes de la carta. Cada arte tiene su propio id (artId) en YGOPRODeck;
        // lo usamos para el pHash (CardHash) y para que el usuario elija el arte (chosenArtId).
        const images = (c.card_images ?? []).map((im) => ({
            artId: im.id,
            url: im.image_url ?? null,
            urlSmall: im.image_url_small ?? im.image_url ?? null,
        }));
        return {
            id: c.id,
            nameEn: c.name,
            nameEs,
            desc: c.desc ?? '',
            type: c.type ?? '',
            frameType: c.frameType ?? null,
            attribute: c.attribute ?? null,
            race: c.race ?? null,
            level: c.level ?? null,
            atk: c.atk ?? null,
            def: c.def ?? null,
            archetype: c.archetype ?? null,
            img: c.card_images?.[0]?.image_url ?? '',
            imgSmall: c.card_images?.[0]?.image_url_small ?? null,
            priceCm: precioValido(cp?.cardmarket_price), // CardMarket EUR (promedio)
            priceTcg: precioValido(cp?.tcgplayer_price), // TCGPlayer USD (promedio)
            images,
            prints,
        };
    });

    // La "versión" es la fecha de generación: la app la compara para decidir si actualiza.
    const generatedAt = new Date().toISOString();
    const catalogo = { version: generatedAt, generatedAt, sets, cards };

    await mkdir(OUT_DIR, { recursive: true });
    await writeFile(OUT_FILE, JSON.stringify(catalogo));

    // Manifiesto pequeño que la app descarga primero para saber si hay versión nueva.
    const manifest = {
        version: generatedAt,
        generatedAt,
        cards: cards.length,
        sets: sets.length,
        url: urlCatalogo(),
    };
    await writeFile(MANIFEST_FILE, JSON.stringify(manifest, null, 2));

    const mb = (Buffer.byteLength(JSON.stringify(catalogo)) / (1024 * 1024)).toFixed(1);
    console.log(`\n✅ Catálogo escrito en:\n   ${OUT_FILE}`);
    console.log(`   ${cards.length} cartas (${conEs} con nombre ES), ${sets.length} sets, ${totalPrints} impresiones, ~${mb} MB.`);
    console.log(`✅ Manifiesto escrito en:\n   ${MANIFEST_FILE}  (url: ${manifest.url})`);
    console.log(`   Hecho en ${((Date.now() - inicio) / 1000).toFixed(1)}s.`);
}

main().catch((e) => {
    console.error('❌ Error generando el catálogo:', e);
    process.exit(1);
});
