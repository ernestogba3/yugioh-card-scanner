# Roadmap técnico — Yu-Gi-Oh Card Scanner

> Acompaña a [`ARQUITECTURA.md`](./ARQUITECTURA.md). Las fases están ordenadas para que la
> app **funcione end-to-end cuanto antes** y cada paso deje algo verificable en el emulador.
>
> ⛔ **Fase 0 (validación) debe aprobarse antes de escribir código.**

---

## Fase 0 — Validar el diseño (sin código) ✅ COMPLETADA (2026-06-14)

- [x] Aprobar el esquema de Room: entidades `Card`, `CardPrint`, `CardSet`,
      `CollectionItem`, `Deck`, `DeckCard`, `Setting`.
- [x] Aprobar la separación en **dos bases** (`catalog.db` + `user.db`).
- [x] Resolver las 6 decisiones abiertas (§11 de ARQUITECTURA — todas validadas).
- [x] Aprobar el rol de Firebase (solo usuario) y la estructura de Firestore.

**Salida:** diseño aprobado. **Vía libre para empezar la Fase 1.**

---

## Fase 1 — Catálogo offline en Room (corazón del rediseño) 🚧 código listo

**Meta:** la app busca cartas en local, sin backend encendido.

- [x] Crear el módulo `data/catalog/` con `CatalogDatabase` y entidades de catálogo
      (`Card`, `CardPrint`, `CardSet`).
- [x] Script para generar el catálogo (Estrategia B): `backend/src/export-catalog.js`
      (`npm run export-catalog`) escribe `app/src/main/assets/database/catalog.json`.
- [x] Importar el JSON a Room en el primer arranque, en streaming y por lotes
      (`CatalogImporter`, disparado desde `MainActivity`).
- [x] `CatalogDao` con consultas (`buscarPorNombre`, `buscarPorFiltros`, `obtenerCartaPorId`,
      `obtenerPrints`, `obtenerSets`).
- [x] `CardRepository`: mapea catálogo → `CartaYuGiOh`; `ScannerViewModel` y los lookups de
      `ColeccionViewModel` ahora leen de Room en vez de `RetrofitInstance`.
- [x] Compila (`compileDebugKotlin` BUILD SUCCESSFUL).
- [ ] **PENDIENTE (paso del usuario):** ejecutar `npm run export-catalog` para generar el
      JSON y reinstalar la app. Hasta entonces la búsqueda no devuelve resultados.

**Verificable en emulador:** generar el catálogo, instalar, activar modo avión →
buscar "blue eyes" manualmente devuelve la carta sin red.

---

## Fase 2 — Búsqueda tolerante a errores (OCR-friendly) ✅ COMPLETADA

**Meta:** un nombre mal escrito encuentra la carta correcta.

> **Cambio de enfoque vs. plan original:** en vez de FTS4 se usa **ranking fuzzy en memoria
> sobre el índice completo de nombres** (14k es barato). FTS funciona por tokens y falla con
> erratas fuertes del OCR ("Azulez" no haría match limpio con "Azules"); el fuzzy directo
> tolera mucho mejor esos casos. FTS queda como optimización futura si hiciera falta.

- [x] Módulo `data/search/`: `TextoUtil` (normaliza acentos/ñ/signos/espacios) +
      `Similitud` (Levenshtein + Jaro-Winkler + comparación por palabras).
- [x] Índice ligero de nombres (`CatalogDao.obtenerIndiceNombres`) cacheado en memoria y
      compartido (el catálogo es inmutable tras importarse).
- [x] `CardRepository.buscarPorNombre`: puntúa contra ES y EN, filtra por umbral (0.62),
      ordena por parecido y devuelve hasta 30 resultados (el 1º = mejor coincidencia).
- [x] Test unitario `BusquedaFuzzyTest` (caso OCR, nombre distinto, exacto, palabra parcial)
      → **BUILD SUCCESSFUL**.

**Verificado:** "Dragon Blanco Ojos Azulez" puntúa ≥0.85 contra "Dragón Blanco de Ojos
Azules" y <0.62 contra cartas no relacionadas.

---

## Fase 3 — OCR enfocado al nombre ✅ COMPLETADA

**Meta:** la cámara lee solo el nombre, no la carta entera.

- [x] Marco guía dorado en `CameraScreen` para alinear el nombre (Canvas con scrim).
- [x] Recorta el frame a la franja superior (`recortarZonaNombre`, ~15%–40% de alto) antes
      de pasarlo a ML Kit; toma la línea con más letras como nombre.
- [x] OCR → `buscarDesdeOcr` → `CardRepository.buscarPorNombre` (fuzzy, local).

**Nota:** ya estaba implementado en el código base; verificado y conectado a la búsqueda
local. Mejoras futuras: detección del rectángulo de la carta para recortar con precisión.

---

## Fase 4 — Colección en `user.db` ✅ COMPLETADA (enfoque pragmático)

**Meta:** datos de usuario en una BD separada del catálogo.

> **Decisión pragmática:** `AppDatabase` (yugioh_db) ES la "user.db" del diseño (ya guarda
> los datos de usuario). Se mantiene la colección sobre `CartaGuardada` (funciona) y la BD
> queda **separada del catálogo** (`CatalogDatabase`). La migración fina a `CollectionItem`
> (con `cardPrintId` nullable, rareza, estado físico) se difiere para no romper lo que ya va;
> se hará junto con la gestión avanzada de colección.

- [x] Dos bases separadas: `CatalogDatabase` (solo lectura) + `AppDatabase` (usuario).
- [x] La colección y el detalle ya no usan red (leen del catálogo local, Fase 1).
- [ ] (Diferido) `CollectionItem` con impresión/rareza/condición + sync.

**Verificado:** escanear/buscar → "Guardar en colección" → aparece en Colección y persiste.

---

## Fase 5 — Deck Builder ✅ COMPLETADA

**Meta:** crear mazos y ver qué cartas faltan.

- [x] `Deck` + `DeckCard` en `AppDatabase` (FK real deck→cards con CASCADE), `DeckDao`.
- [x] `DeckRepository`: crear/borrar mazo, añadir/quitar cartas (1–3), cruzar con catálogo
      y colección.
- [x] `DeckViewModel` + `MazosScreen`/`DeckDetailScreen` + pestaña "Mazos" en la navegación.
- [x] Buscador dentro del mazo para añadir cartas (poseídas o no, usa la búsqueda fuzzy).
- [x] **Cartas faltantes**: `DeckCard.quantity − copias en colección` (por `cardId`), con
      badge "Te faltan N" por carta y total en la cabecera.

**Verificado:** compila y empaqueta (APK 37 MB). Pendiente: probarlo tú en el móvil.

---

## Fase 6 — Firebase (Auth + backup/restore) ✅ COMPLETADA

**Meta:** login y copia de seguridad opcional en la nube.

- [x] Gradle cableado: plugin google-services aplicado **solo si existe** `google-services.json`
      (el proyecto compila sin él); deps firebase-auth/firestore/play-services-auth.
- [x] `AuthRepository` (login Google) + `SyncRepository` (backup/restore manual) +
      `AuthViewModel` + `AjustesScreen` (pestaña Ajustes) + estructura `users/{uid}/...`.
- [x] Proyecto Firebase `yugiohscanner-1c676` configurado (SHA-1 registrada, Firestore + reglas).
- [x] **VERIFICADO por el usuario:** login con Google, "Hacer copia", desinstalar/reinstalar,
      login, "Restaurar copia" → vuelve todo. 🎉
- [ ] (Futuro) sync automático por fila con WorkManager + `updatedAt`.

---

## Fase 7 — Búsqueda avanzada y estadísticas ✅ COMPLETADA

- [x] Búsqueda por tipo, nivel, atributo, **arquetipo**, ATK, DEF (todo en Room, local).
- [x] Estadísticas de colección: nº de cartas y distintas, por tipo, arquetipos principales
      (tarjeta desplegable en Colección). El % por set ya existía (Fase anterior).
- [ ] (Diferido) Filtro por **rareza**: requiere cruzar con `card_prints`; la búsqueda hoy
      devuelve cartas, no impresiones.

---

## Fase 8 — Pulido para Play Store 🚧 parcial

- [x] **Migración no destructiva** en `AppDatabase` (user.db): `MIGRATION_3_4` conserva
      colección y mazos al actualizar. Esquema exportado a `app/schemas/` (Room schemaLocation).
- [ ] **Estrategia A del catálogo (`createFromAsset`)** → primer arranque instantáneo.
      *Requiere generar una `.db` con el identityHash de Room; lo más fiable es producirla con
      un test instrumentado en un dispositivo (necesita tu móvil). Pendiente.*
- [ ] Iconos, splash, permisos mínimos, política de privacidad (Firebase/cámara).
- [ ] `bundleRelease` firmado + ficha de Play Store.

---

## Fase 9 — Monetización (premium, pago único)

- [ ] Google Play Billing (pago único).
- [ ] Gating de funciones premium: backup avanzado, estadísticas avanzadas, exportación,
      herramientas de torneo. **Sin anuncios intrusivos.**

---

## Fase 10 — Actualización del catálogo (cartas y sets nuevos) 🔴 CRÍTICA — planificada

**Problema:** el catálogo va EMPAQUETADO (offline-first). Konami publica sets nuevos cada pocas
semanas, así que el catálogo se queda obsoleto: las cartas recién salidas no se encuentran al
escanear ni aparecen en los álbumes/sets. Hay que poder **actualizar el catálogo local sin
republicar la app** (o, como mínimo, de forma incremental y barata).

**Meta:** mantener el catálogo al día (cartas, impresiones, sets) con mínimo consumo de datos y
degradando con elegancia sin conexión (sin red, la app sigue con el catálogo que tenga).

### Enfoque recomendado (híbrido)
1. **Catálogo versionado.** Añadir versión/fecha al catálogo (tabla `catalog_meta` o `Setting`
   `catalogVersion`); el backend la fija al exportar.
2. **Manifiesto remoto.** Publicar un `manifest.json` pequeño (versión más reciente + URLs de
   descarga). Hosting candidato: **Firebase Storage** (ya tenemos Firebase), GitHub Releases o CDN.
3. **Comprobar + descargar.** En arranque (limitado: 1×/día, opcional solo WiFi) o con botón
   manual en Ajustes: comparar versión local vs remota; si hay nueva, descargar el delta (o el
   catálogo completo) y aplicarlo.
4. **Aplicar con upsert.** `CatalogImporter` debe soportar INSERT-or-REPLACE para cartas/sets
   cambiados y altas de prints nuevos (hoy usa IGNORE). Transaccional, en streaming y por lotes.
5. **UI en Ajustes.** "Buscar actualizaciones del catálogo": progreso, fecha de última
   actualización y nº de cartas.
6. **Respaldo on-demand (complementario).** Si al escanear/buscar una carta NO está en el
   catálogo local y hay conexión, consultar YGOPRODeck para ESA carta y cachearla. Cubre el hueco
   entre actualizaciones completas.

### Sub-tareas
- [ ] Backend: añadir `catalogVersion`/fecha al export; generar **deltas por fecha** y un
      `manifest.json`; subirlos al hosting elegido.
- [ ] App: versión local (tabla/Setting) + `CatalogUpdateRepository` (comprobar manifiesto,
      descargar, aplicar upsert).
- [ ] `CatalogImporter`: modo upsert (REPLACE) + alta incremental de prints/sets.
- [ ] `AjustesScreen`: botón "Actualizar catálogo" con progreso, fecha y total.
- [ ] (Opcional) **WorkManager**: chequeo periódico en segundo plano con restricción de red.
- [ ] Respaldo on-demand desde YGOPRODeck para cartas sueltas no encontradas.

### Decisiones a confirmar al llegar
- **Hosting**: Firebase Storage (integrado) vs GitHub Releases (gratis/simple) vs CDN.
- **Full vs delta**: empezar con descarga **completa** (simple/robusto) y migrar a **delta por
  fecha** cuando el tamaño moleste.
- **Cuándo**: solo manual al principio; auto-check (WorkManager, WiFi) después.

> No rompe el offline-first: la actualización es oportunista y opcional; sin conexión la app
> funciona con el catálogo empaquetado/última versión descargada.

---

## Dependencias nuevas previstas (a confirmar al llegar a cada fase)

| Fase | Dependencia | Para qué |
|------|-------------|----------|
| 1 | `androidx.room:room-runtime/ktx/compiler` (ya está) | Catálogo y usuario |
| 2 | Levenshtein/Jaro-Winkler (librería o propio) | Ranking fuzzy local |
| 3 | CameraX + ML Kit (ya están) | OCR del nombre |
| 6 | `firebase-bom`, `firebase-auth`, `firebase-firestore`, `play-services-auth` | Login/sync |
| 6 | `androidx.work:work-runtime-ktx` | Sync en segundo plano |
| 9 | `com.android.billingclient:billing` | Pago único premium |
| 10 | `androidx.work:work-runtime-ktx` | Chequeo periódico de actualización del catálogo |
| 10 | Firebase Storage / GitHub Releases / CDN | Servir `manifest.json` + catálogo/deltas |

> Recordatorio del proyecto: las versiones van en `gradle/libs.versions.toml` y se referencian
> con `libs.`; nunca hardcodear versiones en `build.gradle.kts`.

---

## Estado actual

- **Hecho:** app base (Compose, navegación, búsqueda por backend, colección con
  `CartaGuardada`, escaneo OCR básico), backend Node+PostgreSQL con búsqueda fuzzy.
- **Siguiente:** aprobar Fase 0 → empezar Fase 1 (catálogo offline en Room).
```
