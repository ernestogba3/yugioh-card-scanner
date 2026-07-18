---
name: project-rediseno-ui
description: Progreso del rediseño visual (spec del skill) por pantallas; qué queda pendiente
metadata: 
  node_type: memory
  type: project
  originSessionId: a4442428-a080-442c-b829-016d089a44fc
---

Rediseño visual de la app aplicando el spec del final de `references/ui-patterns.md` del
skill [[skill-yugioh-builder]]. Paleta exacta: fondo #14141C, surface #1E1E2A, elevado #262635,
borde #33333F, oro #C9A227 / claro #E8C766, texto #F0EDE3 / muted #8E8C9A.

**Hecho (2026-06-17, compila BUILD SUCCESSFUL):**
- **Tema:** `ui/theme/Color.kt` con la paleta del spec (se conservaron los nombres de variable
  OroYuGiOh/AzulOscuro/etc. para no romper imports). `Theme.kt`: `outline = BordeSutil` (#33333F).
- **ColeccionScreen.kt:** cabecera "Mi Álbum", tabs horizontales de sets (antes dropdown),
  grid de 3 columnas (aspectRatio 0.72) con badge de cantidad + nombre en degradado. Se añadió
  un botón de borrar (papelera) por celda — desviación deliberada del spec para no perder la
  función de eliminar que tenía la lista.
- **DetalleCartaScreen.kt:** imagen con borde dorado 2dp, nombre 18sp, pills tipo+edición,
  3 cajas de stats (ATK/DEF/Nivel), selector de cantidad (-/+) que añade N copias reusando
  `onGuardar()`, botón dorado "Añadir al álbum". Se conservaron secciones DATOS/EFECTO/SETS.
- **CameraScreen.kt** (la "Pantalla 1 / escáner" del spec es la cámara en vivo, NO la
  ScannerScreen, que es la pantalla de búsqueda): marco guía con esquinas reforzadas en dorado
  claro, hint debajo, banner de estado, y footer con 3 botones circulares (búsqueda manual =
  onCerrar · disparador dorado grande = capturar · flash = enciende la linterna vía
  `camera.cameraControl.enableTorch`, funcional). Las tres pantallas del spec quedan hechas.

**Hecho 2026-06-17 (2ª tanda, BUILD SUCCESSFUL):**
- **Condición de carta:** campo `condicion: String?` en `CartaGuardada` + migración Room
  **4→5** (`MIGRATION_4_5`, ALTER TABLE ADD COLUMN, no destructiva) + dropdown
  `SelectorCondicion` en `DetalleCartaScreen` (lista `CONDICIONES`). `guardarCarta(carta,
  condicion)`; `onGuardar` ahora es `(String?) -> Unit` (actualizados ColeccionScreen y
  ScannerScreen). La condición SE GUARDA pero aún NO se muestra en la lista de colección.
- **Más filtros:** filtro **raza** (CatalogDao.buscarPorFiltros + CardRepository + Filtros +
  `RAZAS` + PanelFiltros) y toggle **"Solo en mi colección"** (FilterChip local en ScannerScreen).
- **Filtros sin romper diseño:** PanelFiltros reorganizado en filas de 2 columnas
  (Tipo|Nivel, Atributo|Raza, ATK|DEF); `DropdownFiltro` acepta `Modifier` y es singleLine.
- **Mejor escaneo:** en `CameraScreen` el OCR filtra líneas por nº de letras (descarta ruido)
  y une el nombre cuando ocupa 2 líneas.
- **Funciones de mazos:** `DeckRepository.renombrarMazo/duplicarMazo`; `DeckViewModel`
  estadísticas (`EstadisticasMazo`: monstruos/mágicas/trampas/faltan), `renombrarMazo`,
  `duplicarMazo`, `textoExportable`. UI en `DeckDetailScreen`: chips de reparto, botón
  Duplicar, Compartir (Intent ACTION_SEND), e icono Editar → `DialogoEditarMazo`.

**Hecho 2026-06-19 (BUILD SUCCESSFUL `compileDebugKotlin`):**
1. **Condición visible en grid:** `CartaGridItem` recibe `condicion` y la pinta con `BadgeCondicion`
   bajo el nombre; si las copias difieren muestra "Varias" (ColeccionScreen calcula `condiciones.distinct()`).
2. **Favorito (corazón):** columna `favorito: Boolean` en `CartaGuardada` + **MIGRATION_5_6**
   (ALTER TABLE ADD COLUMN favorito INTEGER NOT NULL DEFAULT 0, AppDatabase **v5→v6**, no destructiva).
   `CartaDao.marcarFavorito(cardId, fav)` (afecta TODAS las copias). `ColeccionViewModel.favoritos:
   StateFlow<Set<Int>>` + `toggleFavorito(cardId)`. Corazón en la barra superior de
   `DetalleCartaScreen` (params opcionales `esFavorito`/`onToggleFavorito`; Scanner los omite →
   sin corazón). En el grid: borde dorado + icono corazón + las favoritas se ordenan primero.
3. **Bottom navigation** YA estaba hecho en `MainScreen.kt` (4 tabs: Escáner/Colección/Mazos/Ajustes).

**Hecho 2026-06-19 (2ª tanda, BUILD SUCCESSFUL) — OCR avanzado (parte candidatos):**
- `CameraScreen.construirCandidatos(lineas)` genera VARIAS hipótesis del nombre (línea principal,
  título a 2 líneas hacia delante y hacia atrás, top-2 líneas por nº de letras). El callback pasó
  de `(String)->Unit` a `(List<String>)->Unit`. Añadido `prepararParaOcr()` que amplía el recorte
  si salió pequeño (alto < 320px) para que ML Kit lea mejor.
- `CardRepository.buscarPorVariosNombres(candidatos)` + helper privado `rankearYRecuperar(consultas)`
  (refactor de `buscarPorNombre`): cada carta se puntúa con el MEJOR de los candidatos.
- `ScannerViewModel.buscarDesdeOcr` ahora recibe `List<String>`; `ScannerScreen` usa el 1er
  candidato como texto de la caja de búsqueda.

**Hecho 2026-06-19 (3ª tanda, BUILD SUCCESSFUL assembleDebug) — detección del rectángulo (OpenCV):**
- Dependencia **`org.opencv:opencv:4.13.0`** (Maven Central) en libs.versions.toml + build.gradle.kts.
  Init con `OpenCVLoader.initLocal()` (lazy en el detector; el AAR trae las .so, sin OpenCV Manager).
- **`data/scan/DetectorCarta.kt`** (object): bitmap→Mat, downscale a 1280px para detección, gris+
  GaussianBlur+Canny+dilate, findContours RETR_EXTERNAL, mayor cuadrilátero convexo con área >15%
  del frame, approxPolyDP. Ordena esquinas (suma/diff), descarta si sale apaisado (>1.1), corrige
  perspectiva (getPerspectiveTransform+warpPerspective) a carta 720x1050 y recorta la franja del
  nombre (top ~4-13%, ancho 80%). Libera todos los Mat. Devuelve null si no hay carta → respaldo.
- **CameraScreen**: `capturar()` hace la detección en un `Executors.newSingleThreadExecutor`
  (ocrExecutor, shutdown en onDispose); `reducirSiEsEnorme(bitmap,2400)` limita memoria; intenta
  `DetectorCarta.recortarNombre()` y si null cae a `recortarZonaNombre()` (respaldo, franja
  aproximada centrada). OCR vía nuevo `procesarOcr()` (callbacks ML Kit en hilo principal).
  Guía visual cambiada de franja-nombre a **marco con forma de carta** (CARTA_RATIO 0.686,
  MARCO_ANCHO 0.72), hint "Encuadra la carta completa dentro del marco".
- APK debug 105MB (4 ABIs, debug sin minify); se dejaron las 4 ABIs para que el emulador (x86_64)
  funcione. En Play (App Bundle) la descarga real será ~25-35MB.

**Hecho 2026-06-19 (4ª tanda, BUILD SUCCESSFUL assembleDebug) — escáner robusto + modo en vivo:**
- `DetectorCarta` más robusto a ruido/poca luz: CLAHE (createCLAHE 2.0) + filtro bilateral
  (9,75,75) + Canny AUTOMÁTICO (umbrales 0.66/1.33×brillo medio vía Core.mean) + 2ª estrategia
  `adaptiveThreshold` GAUSSIAN si Canny falla + morphologyEx CLOSE. `encontrarCuadrilatero(mat,
  rapido)`: rapido=true (modo vivo, 1 pasada ligera) / false (captura, pipeline completo).
  AREA_MINIMA bajada a 0.12.
- **Modo detección EN VIVO**: nuevo `DetectorCarta.detectarEsquinasNormalizadas()` → `CuadrilateroNorm`
  (4 PointF normalizados 0..1 + aspecto). En `CameraScreen` se añadió un `ImageAnalysis`
  (RGBA_8888, KEEP_ONLY_LATEST, analysisExecutor propio) bound junto a Preview+ImageCapture, los
  3 con `ResolutionSelector` RATIO_4_3 para que el contorno cuadre con la vista. El analizador
  pinta el cuadrilátero verde sobre la cámara (Canvas + Path, mapeo FILL_CENTER con el aspecto),
  el marco guía se pone verde, y hay **disparo automático** tras FRAMES_PARA_AUTODISPARO=10
  frames estables (toggle "Auto ●/○" arriba-derecha; on por defecto). framesEstables es
  `intArrayOf(0)` (no recompone).

**Hecho 2026-06-19 (5ª tanda, BUILD SUCCESSFUL) — anti-shaking + auto sin búsquedas falsas:**
Usuario confirmó que el contorno en vivo "se ve bien" pero (a) temblaba al costar detectar y
(b) el auto saltaba al buscador generando búsquedas falsas. Corregido en `CameraScreen`:
- **Anti-shaking**: contorno suavizado con media exponencial (`mezclarEsquinas`, ALPHA 0.35) si
  es la misma carta (`distanciaMediaEsquinas` < UMBRAL_MISMA_CARTA 0.18); histéresis al
  desaparecer (GRACIA_FRAMES 4 antes de borrar el contorno). Holders sin recomposición:
  `refSuave`, `framesSinCarta`.
- **Auto fiable**: el contador de auto solo sube si la carta está QUIETA (movimiento <
  UMBRAL_ESTABLE 0.015) durante FRAMES_PARA_AUTODISPARO=12. `capturar(exigirDeteccion=true)` en
  auto: si la captura NO detecta la carta de verdad (recortarNombre==null), NO busca (evita
  búsquedas falsas) y muestra "Ajusta la carta…". En manual sí se permite el recorte de respaldo.

**VERIFICADO EN DISPOSITIVO 2026-06-19:** el usuario confirmó que el escáner "funciona
correctamente" tras el anti-shaking + auto sin búsquedas falsas. El pilar del escaneo se da por
terminado y validado (rectángulo + perspectiva + candidatos + robustez luz/ruido + modo vivo +
suavizado + auto-disparo fiable).

**Hecho 2026-06-19 (6ª tanda, BUILD SUCCESSFUL) — reorganización de Colección + álbum por sets:**
A petición del usuario (eligió "cabos sueltos" + pidió estas pantallas):
- **Estadísticas → pantalla propia** `EstadisticasScreen.kt` (se sacó de la tarjeta inline).
- **"Cajas"/sets → pantalla propia** `SetsScreen.kt` (modelo `CajaSet`): lista de sets de la
  colección con miniatura (carta poseída del set), barra y **% de completado**.
- **Álbum por set** `SetAlbumScreen.kt`: al pulsar una caja muestra TODAS las cartas del set
  (catálogo); las poseídas a color (borde dorado + ✓), las que faltan en **gris**
  (ColorFilter saturación 0 + alpha 0.45). Cabecera con % y progreso. Tocar una carta abre su
  detalle (sirve para añadir las que faltan).
- **Datos**: `CatalogDao.obtenerCartasDeSet(setName)` (cards IN subquery sobre card_prints) +
  `CardRepository.obtenerCartasDeSet`. `ColeccionViewModel`: `EstadoAlbum`/`CartaAlbum`,
  `abrirAlbumSet`/`cerrarAlbum`, `abrirDetallePorId(cardId)`.
- **ColeccionScreen** reescrita: quita tabs/stats inline; sub-nav local `VistaColeccion`
  (Principal/Estadisticas/Sets) + álbum por estado del VM; grid principal = TODAS las cartas
  (favoritas primero). Botones de acceso 📊 Estadísticas / 📦 Mis sets.
  Orden de precedencia: detalle > álbum > sub-vista > principal.

**Hecho 2026-06-19 (7ª tanda, BUILD SUCCESSFUL assembleDebug) — % unificado + rareza + CollectionItem:**
- **% de la caja unificado** con el álbum: `CatalogDao.obtenerCardIdsDeSet` +
  `ColeccionViewModel.cajas` (StateFlow) + `calcularCajas` (poseídas = cardIds del set en la
  colección / total = distinct card_prints). `CajaSet` movido a viewmodel; SetsScreen lo importa.
- **Filtro por rareza** (Fase 7 cerrada): `Filtros.rareza` + `RAREZAS` (en ScannerViewModel) +
  `OpcionesFiltro.rarezas`; `CatalogDao.buscarPorFiltros` con subconsulta `EXISTS` sobre
  card_prints.rarity; dropdown "Rareza" en PanelFiltros.
- **CollectionItem (enfoque aditivo, NO destructivo)**: columna `rareza: String?` en
  `CartaGuardada` + **MIGRATION_6_7** (AppDatabase v6→v7). `CartaGuardada.desde(carta,condicion,
  rareza)`, `guardarCarta(...,rareza)`. Selector de rareza en Detalle (genericé SelectorCondicion
  → `SelectorOpcional(label,opciones,...)`, reusado para condición y rareza). Se muestra en el
  grid (`BadgeMini`, rareza con acento dorado). **SyncRepository** ahora respalda/restaura
  rareza + condición + favorito (antes faltaban los 3 en el backup). NO se renombró la tabla
  para conservar colección, mazos y backups; CartaGuardada cumple ya el rol de CollectionItem.

**PENDIENTE próxima sesión:**
- (Opcional) Renombrado literal de clase CartaGuardada→CollectionItem si el usuario lo pide
  (solo cosmético, mucha churn; la sustancia impresión/rareza ya está).
4. **Precios**: omitido porque el catálogo offline no tiene precios.
- Fase 8 (publicar): icono/splash/applicationId/privacidad/bundleRelease; createFromAsset.

Build por terminal: `JAVA_HOME` = "C:\Program Files\Android\Android Studio\jbr" (ver [[project-build-jdk]]),
verificar con `./gradlew compileDebugKotlin`.
