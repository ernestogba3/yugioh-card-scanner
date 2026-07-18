---
name: project_mejoras_plan
description: "Plan de mejoras del YuGiOh Scanner: P1 (tests + refactor) e icono HECHOS (2026-07-10); pendientes P0 escáner (dispositivo) y P2/P3 pulido"
metadata: 
  node_type: memory
  type: project
  originSessionId: 7197471d-8ed5-4aac-8503-37e3ad20ebef
---

Plan de mejoras aprobado el 2026-06-25 (4 bloques). **Progreso al 2026-07-10:**

**✅ P1 Tests de lógica pura — HECHO.** En `app/src/test/`: `PHashTest` (distancia Hamming),
`IdentificadorCartaTest` (regex de passcode `\d{7,8}` y set code), `SimilitudTest`
(Levenshtein/Jaro-Winkler/`puntuarNormalizado`) y `CamaraUtilTest` (`construirCandidatos`/
`limpiarNombre`), más los previos `ReglasMazoTest` y `BusquedaFuzzyTest`. Todos verdes.
Nota: para poder testear los regex del escáner se extrajeron a funciones puras
`IdentificadorCarta.extraerPasscodes`/`extraerSetCode` en el `companion object` (sin cambiar
comportamiento; los métodos privados `suspend` ahora las llaman).

**✅ P1 Partir archivos largos — HECHO** (sin cambio visual, solo mover funciones + visibilidad):
- `MazosScreen.kt` → +`DeckDetailScreen.kt` +`SugerenciaArquetipoScreen.kt`. `SeccionTitulo`
  quedó `internal` en MazosScreen (la comparten lista y detalle); `DeckDetailScreen`/
  `SugerenciaArquetipoScreen` `internal`, el resto `private`.
- `CameraScreen.kt` → helpers puros a `data/scan/CamaraUtil.kt` (`aBitmapVertical`,
  `construirCandidatos`, `prepararParaOcr`, `recortarZonaNombre`, `distanciaMediaEsquinas`,
  `mezclarEsquinas`, `reducirSiEsEnorme`, `limpiarNombre`), todos `internal`.
- `ScannerScreen.kt` → +`ui/components/PanelFiltros.kt` (con `DropdownFiltro` privado) +
  `ui/components/CartaItem.kt` (con `EtiquetaTipo`/`ChipDato` privados).
- `DetalleCartaScreen.kt` → +`ui/components/DetalleComponentes.kt` (`SelectorOpcional`, `CajaStat`,
  `SelectorCantidad`, `PillTipo`, `PillEdicion`, `TarjetaSeccion`, `FilaDato` `internal`;
  `BotonPaso` privado). También se migró la deprecación `MenuAnchorType`→`ExposedDropdownMenuAnchorType`
  (ya venía del commit 566b0c5).
- Convención al partir: funciones llamadas desde otro archivo del mismo módulo → `internal`
  (el `private` de Kotlin es por-archivo). Imports depurados; los unused solo dan warning.

**✅ Icono de la app — HECHO (2026-07-10).** Se sustituyó el androide verde por una **imagen propia
del usuario** (carta de Kuriboh, 1254×1254). Montado como adaptive icon: `ic_launcher_background.xml`
a negro, `ic_launcher_foreground.webp` = la imagen **centrada al 84% sobre negro** (para que el
marco de escáner no lo recorten las máscaras círculo/squircle), 5 densidades regeneradas con
Pillow, `<monochrome>` quitado de los dos anydpi-v26. El manifest ya apuntaba a `@mipmap/ic_launcher`.
Verificado en dispositivo real (build+install+MainActivity en foco, sin crashes). IMPORTANTE de cara
al usuario: para copiar un personaje con copyright NO se calca el arte oficial (se hace versión
propia); aquí el usuario aportó su propia imagen, así que se usó tal cual.

**⬜ P0 Escáner — calibrar + robustez (PENDIENTE, requiere el móvil).** Sigue siendo lo más
importante (la función estrella). Modo debug que muestre el recorte del passcode/set-code y la
distancia Hamming, ajustar fracciones en `DetectorCarta` y `UMBRAL_HAMMING` en `IdentificadorCarta`
con cartas reales; manejar cámara sin permiso / errores ML Kit / umbral anti falsos positivos.

**⬜ P2/P3 Pulido y producto (PENDIENTE).** Fuentes reales Fraunces/Inter/Caveat en `res/font/`;
sección legal (atribución YGOPRODeck/Konami); usar el set code para fijar edición/rareza al escanear;
centralizar textos en `strings.xml`; backup/restore automático con WorkManager; valor total de la
colección; export/import de mazos `.ydk`.

Ver [[project_rediseno_passcode]] (estado del rediseño) y [[feedback_compose_revision]] (convenciones).
