---
name: project_rediseno_passcode
description: "Rediseño 2026-06-24 \"binder cálido\" + escaneo por passcode/pHash, adaptado a lo existente y offline; progreso por fases"
metadata: 
  node_type: memory
  type: project
  originSessionId: bc8f3eff-913b-4485-93a4-fe7c9372f18f
---

El 2026-06-24 el usuario pegó un spec nuevo de 7 fases (escaneo por **passcode 8 dígitos** + set
code + **pHash** visual, precios, sugerencias de mazos por arquetipo, tema "binder cálido"
cuero/pergamino/granate con Fraunces/Inter/Caveat, detalle animado "arena de invocación", legal).

**Dos decisiones del usuario que rigen TODO el rediseño:**
1. **Adaptar a lo existente** (NO reconstruir, NO proyecto aparte): reusar catálogo, BDs Room,
   Firebase y escáner ya verificados; del spec aplicar solo lo nuevo. Ver [[project-rediseno-ui]]
   y [[project_redesign_offline]].
2. **Passcode pero OFFLINE**: NO se añade Retrofit en runtime. El passcode (= `Card.id` de
   YGOPRODeck) se resuelve contra el catálogo local. El pHash se precalcula en build-time.

**Why:** conserva el offline-first validado (publicable sin servidor) y reaprovecha lo hecho.

**Fase 0 CERRADA (compila):** Gradle ya cumplía (Kotlin 2.1.0, KSP 2.1.0-1.0.29,
pluginManagement sin filtros). Único cambio: `YuGiOhApplication.kt` (nuevo) = `ImageLoaderFactory`
de Coil con caché de disco persistente (`image_cache`, 10% disco, memoria 25% RAM,
`respectCacheHeaders(false)`), registrada en AndroidManifest `android:name=".YuGiOhApplication"`.
Desviaciones aprobadas: Retrofit NO se añade; `domain/usecase` y `ui/components` se crean en su
fase, no vacíos.

**Fase 1 EN CURSO — pHash todos los artes (~20k+):**
- Backend: `export-catalog.js` ahora emite `images:[{artId,urlSmall}]` por carta. Nuevo
  `generate-phash.js` (`npm run phash`, dep **sharp ^0.33.5**): descarga cada imagen pequeña,
  pHash DCT 64 bits (16 hex), escribe `assets/database/phashes.json` `[{artId,passcode,pHash}]`.
  Resumable (checkpoint cada 500), concurrencia 10, reintentos. **PENDIENTE que el usuario
  ejecute:** `cd backend && npm install && npm run export-catalog && npm run phash`.
- Android: entidad `CardHash`(artId PK, passcode, pHash) en catalog.db **subida a v2**
  (fallbackToDestructiveMigration reconstruye desde JSON, sin migración manual). `CatalogDao`:
  contarHashes/insertarHashes/obtenerTodosLosHashes. `CatalogImporter` refactor: importa
  catálogo Y hashes (cada uno con guarda idempotente propia). El matching por Hamming es Fase 2.
- DAOs spec de "valor total"/"staples" aplazados: dependen de precios (Fase 3) y flag staple
  (Fase 4), datos que el catálogo aún no trae.

**Fase 2 ESCRITA (compila assembleDebug) — escaneo passcode-first + selector de arte:**
Decisiones del usuario: selector de arte INCLUIDO en Fase 2; passcode-first AÑADIDO ENCIMA sin
quitar el OCR de nombre (queda como último respaldo). Reusa `DetectorCarta.detectarYRectificar`.
- Datos: entidad `CardArt`(artId PK, passcode, url, urlSmall) en catalog.db **v3**, importada del
  array `images` de catalog.json. `CartaGuardada.chosenArtId: Long?` + **MIGRATION_7_8** (user.db
  **v8**). `export-catalog.js` images ahora incluye url+urlSmall. SyncRepository respalda chosenArtId.
- Escaneo: `PHash.kt` (DCT 64-bit, ESPEJO EXACTO del backend; ojo: grayscale sharp vs Android puede
  diferir, umbral Hamming ajustable). `DetectorCarta`: rectificado a resolución configurable
  (`CARD_W_ALTA`=1000 para el passcode) + `recortarPasscodeDe`/`recortarSetCodeDe`/`recortarNombreDe`
  (fracciones, AJUSTAR en dispositivo). `IdentificadorCarta`: passcode (regex \d{7,8} → Card.id) →
  pHash (Hamming vs CardHash, UMBRAL_HAMMING=10) → set code (regex) → si nada, NINGUNO (huboCarta
  distingue "no había carta" de "no identificada"). `CardRepository`: buscarPorPasscode/buscarPorHash/
  obtenerArtesDeCarta + caché de hashes.
- UI: `CameraScreen` ahora pide identificar primero y solo cae a OCR-nombre si falla; nuevos params
  onIdentificar/onCartaIdentificada/onTextReconocido. `ScannerViewModel`: identificarFrame + cargarArtes.
  `ScannerScreen` abre el detalle de la carta identificada con arte sugerido. `DetalleCartaScreen`:
  selector de arte (Row de miniaturas si artes>1) + onGuardar ahora (cond,rar,chosenArtId,urlArte);
  call sites actualizados (ScannerScreen, ColeccionScreen, ColeccionViewModel.guardarCarta).
- PENDIENTE de probar en dispositivo: ajustar fracciones de recorte passcode/setcode y UMBRAL_HAMMING;
  requiere `npm run phash` para el fallback visual. Set code se lee pero aún NO se usa para fijar
  edición/rareza (futuro).

**Fase 3 HECHA (compila) — precios offline:**
Realidad de datos YGOPRODeck: card_prices = promedio a nivel CARTA (CardMarket EUR + TCGPlayer
USD, sin precio por rareza); card_sets[].set_price = precio TCGPlayer USD por impresión. Se usan
ambos.
- Backend `export-catalog.js`: helper `precioValido` (descarta 0.00); por carta `priceCm`/`priceTcg`,
  por print `price`.
- Android: `Card` gana priceCm/priceTcg, `CardPrint` gana price; catalog.db **v4** (reconstruye).
  CatalogImporter parsea ambos. `CartaYuGiOh` gana precioCmEur/precioTcgUsd, `SetCarta` gana precio.
  Mapper los rellena. `DetalleCartaScreen`: tarjeta "PRECIO" (CardMarket €  + TCGPlayer $) + precio
  por edición en la sección SETS ("CODE · $precio"). Requiere regenerar catálogo (export-catalog).

**Fase 4 HECHA (compila, 2026-06-25) — sugerencias de mazos por arquetipo:**
Reusa el Deck Builder existente (Deck/DeckCard en user.db, DeckRepository/DeckViewModel/MazosScreen).
NO se hizo el flag "staple" (sigue diferido: el catálogo no lo trae). Todo offline cruzando
colección (user.db) ↔ arquetipos del catálogo (catalog.db); SIN cambio de versión de Room.
- CatalogDao: `obtenerConteoArquetipos()` (GROUP BY archetype) + `obtenerCartasDeArquetipo()`.
  Nueva proyección `ConteoArquetipo` en NombreCarta.kt.
- DeckRepository: data classes `SugerenciaArquetipo`(arquetipo,poseidas,totalCatalogo,porcentaje) y
  `CartaArquetipo`(carta,enColeccion); `sugerenciasArquetipos()` (arquetipos que ya coleccionas,
  orden por poseídas), `cartasDeArquetipo()`, `crearMazoDesdeArquetipo(arq, soloPoseidas)`.
- DeckViewModel: StateFlows `sugerencias`/`arquetipoCartas` + cargarSugerencias/abrirArquetipo/
  cerrarArquetipo/crearMazoDesdeArquetipo.
- MazosScreen: sección "Sugerencias para ti" (SugerenciaItem con LinearProgressIndicator) arriba de
  "Mis Mazos"; al tocar abre SugerenciaArquetipoScreen (lista de cartas con badge xN/"No la tienes" +
  botones "Las que tengo"/"Todas"). NOTA: las sugerencias solo aparecen si la colección tiene
  cartas con arquetipo.

**Fase 4 — iteración tras probar en dispositivo (2026-06-25):** feedback del usuario corregido:
(1) **Reglas de tamaño**: nuevo `object ReglasMazo` en DeckRepository (PRINCIPAL_MIN 40, MAX 60,
EXTRA_MAX 15, MAX_COPIAS 3; `esExtra(type)` = Fusion/Synchro/XYZ/Link). `anadirCarta`/`cambiarCantidad`
ahora devuelven String? (error o null) y bloquean si la zona está llena; `crearMazoDesdeArquetipo`
no se pasa de los topes. `EstadisticasMazo` gana principal/extra + principalValido/extraValido;
DeckDetailScreen muestra ChipEstado "Principal X/40–60" y "Extra X/15" (verde/rojo) y un Toast con el
aviso (StateFlow `mensaje`). (2) **UX sugerencia**: SugerenciaArquetipoScreen ahora es un
LazyVerticalGrid 3-col (poseídas a color con badge xN, faltantes atenuadas alpha .35); al crear el
mazo (StateFlow `mazoCreadoId` + consumirMazoCreado) se ABRE ese mazo directamente en vez de volver
al menú. Compila (compileDebugKotlin OK). PENDIENTE: que el usuario lo vuelva a probar.

**Fase 10 HECHA (compila + assembleDebug OK, 2026-06-25) — actualización del catálogo sin republicar:**
Decisiones del usuario: estrategia=catálogo completo descargable, fuente=YGOPRODeck directo,
generación=GitHub Action automático. CLAVE: `export-catalog.js` YA tira directo de YGOPRODeck
(NO usa PostgreSQL), así que el Action solo lo ejecuta. Diseño: Action semanal regenera
catalog.json + manifest.json y los commitea; la app baja manifest del raw URL de GitHub, compara
versión y descarga catalog.json completo si cambió.
- Backend: `export-catalog.js` ahora `version`=generatedAt (ISO) y escribe también `manifest.json`
  (version, generatedAt, cards, sets, url). `urlCatalogo()` usa GITHUB_REPOSITORY/REF_NAME o CATALOG_URL.
- `.github/workflows/update-catalog.yml`: cron lunes 04:00 + manual; npm ci + npm run export-catalog;
  commit/push si cambió. (phash NO se corre en el Action: descarga 20k imágenes, es caro.)
- Android: CatalogDao gana borrarCartas/borrarPrints/borrarSets/borrarArtes. CatalogImporter:
  `reemplazarCatalogo(context,input)` (borra cards/prints/sets/arts + reimporta en transacción;
  pHash intactos) + helper parsearCatalogo. CardPrint tiene PK autogen → por eso se RECONSTRUYE
  (no upsert). `CatalogUpdateRepository` (HttpURLConnection, sin deps nuevas; versión en
  SharedPreferences "catalogo"). `CatalogUpdateViewModel`. AjustesScreen: tarjeta "Catálogo de
  cartas" (Buscar actualizaciones→Disponible→Descargar e instalar). `CardRepository.invalidarCaches()`
  tras actualizar.
- VERIFICADA de punta a punta por el usuario (2026-06-25): repo **público ernestogba3/yugioh-card-scanner**,
  MANIFEST_URL+CATALOG_URL_FALLBACK ya apuntan ahí. Hubo que arreglar 2 cosas para que el Action
  corriera: (a) `package-lock.json` no tenía `sharp` → `npm install --package-lock-only` para
  sincronizarlo (npm ci exige lock en sync); (b) `.gitignore` ampliado con backend/.env (¡credenciales
  PostgreSQL, NO subir!) y node_modules. El Action publicó manifest.json (version=ISO, 14422 cartas,
  1020 sets) + catalog.json ~16.9MB; la app actualizó OK desde Ajustes. NOTA: tras correr el Action,
  el usuario debe `git pull` (el Action commitea el catálogo). google-services.json sigue trackeado
  (aceptable para app Android; se puede ignorar si se quiere). Ver docs/ACTUALIZACION_CATALOGO.md.

**Fase 5 HECHA (compila, 2026-06-25) — tema cálido "binder":**
Viró de azul oscuro/oro a paleta cuero/pergamino/granate/oro envejecido. `Color.kt` reescrito:
nuevos tokens CueroFondo #1C140F / CueroMedio #292019 / CueroClaro #362A20 / BordeCuero #4D3B2B,
OroEnvejecido #C9A24B + OroClaroCalido #EBD08A, Granate #9B2D3A, Pergamino #F0E6D2 +
PergaminoTenue #B9A98C, RojoCalido #C75D5D. CLAVE: los nombres antiguos (AzulOscuro, OroYuGiOh,
RojoAccento, TextoPrincipal…) se MANTIENEN como alias apuntando a los cálidos → no se tocaron las
13 pantallas que los referencian. Colores por tipo de carta (ColorNormal..ColorTrampa) intactos.
`Theme.kt` darkColorScheme rehecho (añadido secondaryContainer granate). `CardStyle.kt`
FondoGradiente ahora cuero. Type.kt SIN tocar (serif≈Fraunces ya da el aire; los TTF reales
Fraunces/Inter/Caveat quedan como extra opcional: NO hay res/font/, habría que empaquetarlos).

**Detalle "carta viva" (2026-06-25, aprobado por el usuario):** se QUITÓ la "arena de invocación"
(botón ⚡Invocar + círculo mágico/partículas) por petición del usuario; se quedó SOLO la carta
arrastrable que se inclina en 3D. `ui/components/CartaInvocable.kt` borrado → nuevo
`CartaHolografica.kt`. El brillo holográfico ahora DEPENDE de la rareza seleccionada e imita el
foil real de cada una (investigado en web: Secret=cross-hatch diagonal en el arte, Starlight=rejilla
V+H en toda la carta + destellos, Collector's=huella dactilar, Ultimate/Platinum=relieve grabado,
Ghost=halo etéreo, etc.). Se dibuja con Canvas, cada patrón RECORTADO a su zona real (nombre/arte/
toda la carta vía clipRect) y en `BlendMode.Plus` (aditivo) para que parezca luz reflejada, no
pintura. Se ampliaron las RAREZAS (ScannerViewModel) con Parallel/Platinum/Mosaic/Starfoil/
Shatterfoil. El estado `rareza` se hoisteó arriba en DetalleCartaScreen (con remember(carta.id))
para alimentar el brillo. Intensidad/nº de rayos subidos a gusto del usuario. Todo parametrizado al
inicio de cada función de CartaHolografica.kt por si hay que reafinar.

ROADMAP rediseño passcode COMPLETO (Fases 0–5 + 10). Pendientes solo manuales/de prueba:
pasos de Fase 1 (`cd backend && npm run export-catalog && npm run phash`), ajustar fracciones de
recorte passcode/setcode y UMBRAL_HAMMING en dispositivo, y (opcional) fuentes reales en res/font.

**GENERACIÓN DE pHASH — COMPLETADA (2026-06-25):** `cd backend && npm install` (sharp 0.33.5 instalado/
verificado, node v24) + `npm run phash`. Resultado: **14.586/14.586 artes hasheados, 0 fallos, en 102s**.
Escrito `app/src/main/assets/database/phashes.json` (937 KB; formato `{artId,passcode,pHash}` con pHash
de 16 hex). catalog.json ya traía los 14.586 artes con urlSmall (no hizo falta export-catalog).
PENDIENTE del usuario (paso manual en Android Studio): **Build → Make Project + Run** para empaquetar
phashes.json en el APK; en el primer arranque CatalogImporter importa las huellas a catalog.db
(idempotente, guarda por contarHashes). DESPUÉS: probar el escáner visual en dispositivo y AJUSTAR
fracciones de recorte (DetectorCarta) + UMBRAL_HAMMING (IdentificadorCarta, hoy =10) según resultados.
Si en el futuro se regenera el catálogo con artes nuevos, re-ejecutar `npm run phash` (resumible: solo
hashea los que falten).

Build por terminal: JAVA_HOME a "C:\Program Files\Android\Android Studio\jbr", `./gradlew
compileDebugKotlin` (ver [[project-build-jdk]]).
