---
name: project_uiux_mockups
description: "Plan UI/UX de 6 mockups (2026-07-18): Toast post-escaneo HECHO y compilando; pendientes chips, valor total, y ya-existentes (mazos/arte)"
metadata: 
  node_type: memory
  type: project
  originSessionId: 724a42d8-7da1-4f70-8a8e-eb97563b1bff
---

Plan "IMPLEMENTACIÓN DE MEJORAS UI/UX" traído por el usuario el **2026-07-18**. El plan original
venía escrito para OTRA arquitectura (Hilt, Retrofit/YGOPRODeck en runtime, una sola BD Room,
`CardEntity`/`HomeViewModel`) — NADA de eso aplica a esta app (offline-first, sin Hilt, dos BDs,
`CartaGuardada`/`Card`, `ScannerViewModel`/`ColeccionViewModel`, navegación por sealed class en
`MainScreen`). Se reencuadró: mantener las IDEAS, adaptar el código.

**Mapa de las 6 features:**
- 1.1 Chips de estado en colección → NUEVO, offline. (wishlist "NECESARIA" no existe → usar `favorito` que sí)
- 1.2 Toast post-escaneo → NUEVO, offline. **✅ HECHO 2026-07-18** (ver abajo)
- 1.3 Deck suggestions (hero/coverage) → YA EXISTE: `SugerenciaArquetipoScreen`+`DeckViewModel`+`MazosScreen`. Solo sería rediseño
- 1.4 Modal de arte → YA EXISTE: `CartaGuardada.chosenArtId` + `CardArt.kt`. Solo pulir
- 1.5 Widget valor: TOTAL factible (`Card.priceCm` × copias); HISTÓRICO 4 semanas NO factible sin construir tabla de snapshots (precios estáticos)
- 1.6 Onboarding/Welcome → NUEVO pero debe convivir con login Firebase (`AuthViewModel`)

Mockups HTML publicados como Artifact (paleta cuero/oro real de `Color.kt`) y aprobados para empezar.
Orden recomendado y acordado: **1.2 Toast → 1.1 Chips → 1.5 valor total**.

**✅ 1.2 TOAST — HECHO y compilando (BUILD SUCCESSFUL).** Disparado desde el único punto de guardado
`ColeccionViewModel.guardarCarta()`. Cambios:
- `CartaDao`: `contarPorCardId(cardId)` y `eliminarPorIdLocal(idLocal)`.
- `ColeccionViewModel`: data class `EventoToast` (idLocal/nombre/subtitulo/urlImagen/copias/esDuplicada),
  flujo `eventoToast: StateFlow<EventoToast?>`, `guardarCarta` emite el evento (duplicada si copiasPrevias>0),
  `descartarToast()`, `deshacerGuardado(idLocal)`.
- `ui/components/ToastEscaneo.kt` NUEVO: AnimatedVisibility slide+fade, autocierre `MILIS_VISIBLE=3500`,
  botón Deshacer; verde (ColorMagico)=nueva, oro (OroEnvejecido)=duplicada.
- `MainScreen`: recibe `ColeccionViewModel = viewModel()` (misma instancia que las pantallas, mismo
  Activity owner) y monta `ToastEscaneo` en el Box raíz, `align(BottomCenter)` + `padding(bottom=96.dp)`.
- NO se implementó variante de error (en el flujo real, escaneo sin match cae al OCR; no hay fallo de guardado).
- Falta que el usuario lo pruebe en dispositivo/emulador.

**✅ 1.1 CHIPS — HECHO y compilando.** Descubrimiento: `CartaGridItem` (ColeccionScreen) YA pintaba
×N (BadgeCantidad), favorito (corazón) y rareza (BadgeMini) — 3 de los 4 chips del mockup ya existían.
Solo se añadió el único nuevo: chip verde **«EN MAZO»** = carta de la colección que está en algún mazo.
- `DeckDao.cardIdsEnMazos(): Flow<List<Long>>` (SELECT DISTINCT cardId FROM deck_cards).
- `ColeccionViewModel.cardIdsEnMazos: StateFlow<Set<Int>>`.
- `ColeccionScreen`: param `enMazo` en CartaGridItem + composable `BadgeEnMazo` (ColorMagico). Puesto
  abajo junto a rareza/condición (arriba ya hay cantidad+corazón; celdas pequeñas 3 col).

**✅ 1.5 VALOR TOTAL — HECHO y compilando.** Solo el total (histórico NO: precios estáticos, requiere
snapshots). Suma `Card.priceCm` (CardMarket EUR) × copias; como la colección tiene 1 fila/copia, se
suma por fila. Cartas sin priceCm cuentan 0 (avisado en el subtítulo del widget).
- `CardRepository.preciosPorId(ids): Map<Int,Double>` (lotes de 900, parse `priceCm?.toDoubleOrNull()`).
- `ColeccionViewModel.valorColeccion: StateFlow<Double>` derivado de `cartas` (se recalcula solo).
- `ColeccionScreen.ValorColeccionWidget` (borde Granate, nº en OroClaro) + `formatoEuros` es-ES (€2.847,50),
  arriba del álbum cuando hay cartas.

**✅ 1.6 ONBOARDING — HECHO y compilando.** Hallazgo clave: NO hay login como puerta — `MainActivity`
abre directo a `MainScreen`; el login Google es opcional y vive en Ajustes (solo backup/restore). Así que
no había conflicto bienvenida-vs-login. Adaptaciones: saludo genérico "¡Bienvenido, duelista!" (en primer
arranque no hay nombre), y una sola CTA real (escanear/manual caen en la misma pestaña Escáner).
- `data/PreferenciasApp.kt` NUEVO: SharedPreferences "app_prefs", `esPrimerArranque()`/`marcarBienvenidaVista()`.
- `ui/screens/BienvenidaScreen.kt` NUEVO: paleta cálida, emblema 🃏, CTA oro, tarjeta consejo passcode,
  nota "no necesitas cuenta". Sin fondo animado (a propósito). `onEmpezar` marca la bandera y entra.
- `MainScreen`: gate al inicio — si `esPrimerArranque`, muestra BienvenidaScreen y return; si no, contenido normal.
- Para re-probar hay que borrar datos de la app (la bandera persiste). Ofrecí añadir botón "ver bienvenida
  otra vez" en Ajustes si lo quiere (aún no hecho).

**✅ 1.3 REDISEÑO SUGERENCIAS — HECHO y compilando.** En `MazosScreen`: se sustituyó la lista plana
`SugerenciaItem` por tarjeta HÉROE (`HeroSugerencia`, la mejor = sugerencias.first(), barra de cobertura
animada con Animatable+tween, color por % rojo/oro/verde `colorCobertura`, "te faltan N", botón crear) +
carrusel `LazyRow` de `AltSugerencia` (resto). Sin imágenes (SugerenciaArquetipo no las trae; evita consulta
extra). Al tocar sigue abriendo SugerenciaArquetipoScreen existente.

**✅ HISTÓRICO DE VALOR — HECHO y compilando (con migración Room v8→9).** Un snapshot por día:
- `data/model/ValorSnapshot.kt` (@PrimaryKey fecha "yyyy-MM-dd", valorEur, timestamp) + `data/db/ValorHistoricoDao.kt`
  (guardar REPLACE, historico() Flow ASC, eliminarTodo).
- `AppDatabase` v9 + `MIGRATION_8_9` (CREATE TABLE verificado contra schemas/9.json, identityHash OK) + dao registrado.
- `ColeccionViewModel`: `valorColeccion` ahora hace `.onEach { registrarSnapshotHoy }`; `historicoValor: StateFlow<List>`;
  `data class ResumenValor(total, cambioSemana?, porcentaje?)`; `resumenValor = combine(valor, historico)` con
  `calcularResumen` (referencia = foto más reciente ≤ hace 7 días, o la más antigua de otro día; null si solo hoy).
- `ui/screens/HistoricoValorScreen.kt` NUEVO: gráfica Canvas (línea oro + área verde + puntos, último resaltado),
  rango de fechas y stats min/prom/máx; si <2 fotos muestra mensaje "se irá construyendo".
- `ColeccionScreen`: widget usa `ResumenValor` (tendencia ↑verde/↓rojo o "se calculará con los próximos días"),
  Surface `onClick`→ abre histórico; enum `VistaColeccion` +Historico; `formatoEuros` ahora `internal` (reutilizado).
- Para probar el histórico: cambiar la fecha del emulador +1 día y reabrir Colección crea una 2ª foto → aparece
  gráfica y tendencia.

TODOS los del plan hechos salvo lo que ya existía (1.4 arte). Falta que el usuario pruebe en dispositivo.

Ver [[project_mejoras_plan]] (otro plan, P0 escáner sigue pendiente) y [[project_rediseno_passcode]].
