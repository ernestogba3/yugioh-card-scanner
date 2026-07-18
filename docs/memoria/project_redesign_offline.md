---
name: project_redesign_offline
description: Rediseño offline-first del Yu-Gi-Oh Scanner — decisiones de arquitectura validadas el 2026-06-14
metadata: 
  node_type: memory
  type: project
  originSessionId: 97786f3b-0049-4194-bc80-6beffe1dd548
---

Rediseño aprobado el 2026-06-14 para hacer la app offline-first y publicable (sin PC/servidor en runtime). Documentos en `docs/ARQUITECTURA.md` y `docs/ROADMAP.md`.

Decisiones validadas (§11 de ARQUITECTURA):
- **Dos bases Room separadas:** `catalog.db` (solo lectura, empaquetada en assets) con Cards/CardPrints/Sets; `user.db` (R/W, sincronizable) con Collection/Decks/DeckCards/Settings. Referencias usuario→catálogo son claves lógicas (no FK), porque cruzan BDs.
- **Catálogo:** empezar con JSON importado en 1er arranque (Estrategia B) → migrar a `createFromAsset` antes de publicar (Estrategia A).
- **`Collection.cardPrintId` nullable:** se guarda la carta sin edición exacta al escanear.
- **Firebase solo para usuario** (Auth Google + Firestore backup/restore manual primero). Las cartas NUNCA van a Firebase.
- **Empezar de cero:** no migrar `CartaGuardada`.
- Búsqueda local fuzzy: FTS4 (candidatos) + Levenshtein/Jaro-Winkler en Kotlin (ranking). Reemplaza pg_trgm del backend.

Cambio clave: el [[project_backend]] Node+PostgreSQL deja de ser servidor en runtime y pasa a ser generador del catálogo empaquetado (build-time). Se añade capa Repository entre ViewModel y datos. OCR debe recortar solo la franja superior (nombre).

**Why:** elimina la dependencia de un PC encendido y habilita Play Store.
**How to apply:** seguir el ROADMAP por fases.

**Progreso (2026-06-14):** Fases 0–5 COMPLETADAS. Fase 1 (catálogo offline: `data/catalog/`, `CatalogImporter` con transacción Room, `CardRepository`). Fase 2 (búsqueda fuzzy en memoria: `data/search/` con Levenshtein+Jaro-Winkler+por-palabras; se descartó FTS). Fase 3 (OCR ya recortaba la franja del nombre). Fase 4 pragmática (`AppDatabase`=user.db, separada del catálogo; colección sigue en `CartaGuardada`, migración a `CollectionItem` diferida). Fase 5 (Deck Builder: `Deck`/`DeckCard` en AppDatabase v4, `DeckRepository`, `DeckViewModel`, `MazosScreen`, pestaña Mazos, cartas faltantes). Catálogo generado: 14388 cartas. Compila y empaqueta APK 37MB. Tests `BusquedaFuzzyTest` OK.

**Actualización:** Fases 7 y 8(parcial) hechas. Fase 7: filtro por arquetipo + estadísticas de colección. Fase 8: migración no destructiva `MIGRATION_3_4` en AppDatabase + export de esquema Room a `app/schemas/` (falta createFromAsset, necesita gen en dispositivo).

**Fase 6 (Firebase) CÓDIGO PREPARADO** y a prueba de build: plugin google-services se aplica solo si existe `app/google-services.json` (condicional en app/build.gradle.kts + alias apply false en raíz); deps firebase-auth/firestore/play-services-auth/coroutines-play-services. `AuthRepository`+`SyncRepository`+`AuthViewModel`+`AjustesScreen` (pestaña Ajustes, 4ª). Todo guardado en runtime con `FirebaseApp.getApps().isNotEmpty()`; webClientId leído por getIdentifier (no R.string) para compilar sin el JSON. Compila sin google-services.json (APK 41MB). SHA-1 debug: 4C:60:DF:9E:79:25:D1:BA:67:02:C7:F7:91:6D:43:1F:B7:D0:DA:B0.

**Fase 6 COMPLETADA y VERIFICADA (2026-06-14):** proyecto Firebase `yugiohscanner-1c676` configurado, SHA-1 registrada (resolvió código 10 al añadirla y re-descargar el JSON), Firestore+reglas, google-services.json en app/. Usuario probó login Google + backup + reinstalar + restore con éxito. applicationId sigue siendo `com.example.yugiohscanner` (hay que cambiarlo antes de Play Store, pero eso obliga a re-registrar en Firebase).

Tutorial Fases 6 y 9 en `docs/TUTORIAL_FIREBASE_Y_BILLING.md`. Fase 9 (Play Billing) sigue bloqueada por Play Console.
