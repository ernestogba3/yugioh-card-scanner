# Arquitectura — Yu-Gi-Oh Card Scanner (rediseño Offline-First)

> Estado: **PROPUESTA PARA VALIDAR**. No se modifica código de la app hasta aprobar
> el diseño de Room, Firebase y las entidades.
> Fecha: 2026-06-14 · Autor: rediseño de arquitectura

---

## 1. Objetivo y principios

Convertir la app en un producto Android **autónomo, offline-first y publicable en Play Store**:

1. **Funciona sin Internet.** Toda búsqueda de cartas se resuelve en local (Room).
2. **No depende de un PC encendido** ni de un servidor propio en producción.
3. **Firebase solo para usuario:** login, sincronización y backup de *colección / mazos / ajustes*.
   Las cartas **nunca** se descargan de Firebase.
4. **Escalable:** preparada para colección, deck builder, búsqueda avanzada y premium.

### Regla de oro de los datos
| Dato | Dónde vive | Quién lo escribe |
|------|------------|------------------|
| Catálogo de cartas (Cards, CardPrints, Sets) | Room local, **solo lectura** | Se genera offline y viaja dentro del APK |
| Datos del usuario (Collection, Decks, DeckCards, Settings) | Room local, lectura/escritura | El usuario; se sincronizan a Firebase |

---

## 2. El cambio más importante: de "backend en runtime" a "catálogo empaquetado"

Hoy la app llama por Retrofit a un backend Node+PostgreSQL (`10.0.2.2:3000`) que tiene que
estar encendido. Eso **rompe el objetivo offline**. El rediseño:

- El backend `backend/` **deja de ser un servicio en producción** y pasa a ser una
  **herramienta de build**: genera el catálogo de cartas una vez y lo empaqueta en la app.
- En runtime, la app **no hace red para buscar cartas**. Lee de Room.
- Retrofit/YGOPRODeck se mantienen **solo** para tareas opcionales: actualizar el catálogo
  empaquetado en futuras versiones (proceso de desarrollo, no del usuario) y descargar
  imágenes con Coil bajo demanda.

> Ventaja clave: `importer.js` ya descarga y normaliza todos los campos que necesitamos
> (`id, name, name_es, type, atk, def, level, race, attribute, archetype, imágenes`,
> y `card_sets` con `set_code` y `set_rarity`). Solo cambia el destino: en vez de
> PostgreSQL, escribe el catálogo en formato consumible por Room.

---

## 3. Arquitectura por capas (MVVM + Clean, igual que ahora pero ampliada)

```
┌─────────────────────────────────────────────────────────┐
│ UI (Jetpack Compose)                                      │
│  ScannerScreen · ColeccionScreen · DeckBuilderScreen ...  │
└───────────────▲───────────────────────────┬──────────────┘
                │ StateFlow                  │ eventos
┌───────────────┴───────────────────────────▼──────────────┐
│ ViewModel (estado UI, viewModelScope)                     │
│  ScannerVM · ColeccionVM · DeckVM · AuthVM · SyncVM        │
└───────────────▲───────────────────────────┬──────────────┘
                │                            │
┌───────────────┴───────────────────────────▼──────────────┐
│ Repository (la app habla SOLO con repositorios)           │
│  CardRepository · CollectionRepository · DeckRepository    │
│  AuthRepository · SyncRepository                           │
└──────▲────────────▲────────────▲───────────▲──────────────┘
       │            │            │           │
 ┌─────┴────┐ ┌─────┴─────┐ ┌────┴─────┐ ┌───┴────────┐
 │ Room      │ │ ML Kit    │ │ Firebase │ │ Coil       │
 │ (catálogo │ │ OCR +     │ │ (Auth,   │ │ (imágenes  │
 │  + user)  │ │ CameraX   │ │ Firestore)│ │  remotas)  │
 └───────────┘ └───────────┘ └──────────┘ └────────────┘
```

**Novedad respecto a hoy:** se añade una capa **Repository** entre ViewModel y datos.
Hoy `ScannerViewModel` llama directo a `RetrofitInstance`. Con repositorios, el ViewModel
no sabe si los datos vienen de Room, de OCR o de Firebase: eso hace el código testeable y
escalable. Es el cambio estructural principal del lado app.

---

## 4. Flujo de escaneo (objetivo)

```
CameraX (preview + análisis de frames)
   ↓  recorte de la franja superior (solo el nombre)
ML Kit OCR  → texto crudo del nombre
   ↓
CardRepository.buscarPorNombre(texto)
   ↓  búsqueda local en Room (FTS + similitud)
Mejor coincidencia (Levenshtein / Jaro-Winkler)
   ↓
Mostrar carta  →  (opción) Guardar en colección
```

Sin red en ningún punto del camino crítico.

---

## 5. Diseño de datos en Room — **PUNTO A VALIDAR**

### 5.1 Decisión: **dos bases de datos Room separadas**

| BD | Archivo | Contenido | Migración | Sync |
|----|---------|-----------|-----------|------|
| **CatalogDatabase** | `catalog.db` (empaquetada en `assets/`) | Cards, CardPrints, Sets | Se **reemplaza** entera al actualizar catálogo | No |
| **UserDatabase** | `user.db` (creada en runtime) | Collection, Decks, DeckCards, Settings | Migraciones Room normales, **nunca destructiva** | Sí (Firebase) |

**Por qué separadas:**
- Permite **actualizar el catálogo** (sacar versión nueva con cartas nuevas) **sin tocar
  ni borrar la colección del usuario**. Si estuvieran juntas, reemplazar el catálogo
  arriesgaría los datos del usuario.
- El catálogo es enorme y de solo lectura; la BD de usuario es pequeña y cambia mucho.
- Hoy `AppDatabase` usa `fallbackToDestructiveMigration(true)` — aceptable para datos de
  prueba, **inaceptable** para la colección de un usuario real. La separación resuelve esto.

**Coste:** Room no permite *foreign keys* entre dos bases distintas. Las referencias del
usuario al catálogo (p. ej. `Collection.cardPrintId → CardPrints.id`) serán **claves
lógicas** (un `Long` que guardamos pero que Room no valida automáticamente). Es un
compromiso estándar y aceptable; lo gestionamos en el Repository.

### 5.2 Entidades del **catálogo** (`catalog.db`, solo lectura)

#### `Cards` — una fila por carta única
```kotlin
@Entity(
    tableName = "cards",
    indices = [Index("nameEn"), Index("nameEs"), Index("archetype")]
)
data class Card(
    @PrimaryKey val id: Long,        // id de YGOPRODeck (estable y único)
    val nameEs: String?,             // puede faltar traducción
    val nameEn: String,
    val description: String,
    val type: String,                // "Effect Monster", "Spell Card"...
    val frameType: String?,          // normal/effect/spell/trap... (útil para color de marco)
    val attribute: String?,          // DARK, LIGHT... (null en mágicas/trampas)
    val race: String?,               // "Dragon", "Spellcaster" / o tipo de mágica/trampa
    val level: Int?,                 // nivel/rango/link
    val atk: Int?,
    val def: Int?,
    val archetype: String?,
    val imageUrl: String,            // imagen grande (Coil la descarga si hay red)
    val imageUrlSmall: String?
)
```
> Añadidos respecto a la propuesta original: `frameType, race, atk, def, imageUrlSmall`.
> Razón: la búsqueda avanzada futura (tipo/atributo/nivel/ATK/DEF) y el render de la carta
> los necesitan, y `importer.js` ya los trae. No cuesta nada incluirlos ahora.

#### `CardPrints` — una fila por impresión (set + rareza)
```kotlin
@Entity(
    tableName = "card_prints",
    indices = [Index("cardId"), Index("setCode")]
)
data class CardPrint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,         // → Card.id (clave lógica)
    val setCode: String,      // "LOB-001", "SDK-E001"
    val setName: String,      // "Legend of Blue Eyes White Dragon"
    val rarity: String?,      // "Ultra Rare", "Secret Rare"
    val edition: String?,     // "1st Edition" / "Unlimited" (puede no venir de la API)
    val imageUrl: String?     // normalmente la misma que la carta
)
```
> `importer.js` ya extrae `card_sets[].set_code` y `set_rarity`. `edition` no siempre viene
> en la API → será `null` cuando falte (se rellenará manualmente o en updates futuros).

#### `Sets` — catálogo de ediciones (para % de colección)
```kotlin
@Entity(tableName = "sets")
data class CardSet(
    @PrimaryKey val setName: String,
    val setCode: String?,
    val numOfCards: Int,
    val tcgDate: String?
)
```

#### FTS para búsqueda rápida por nombre (recomendado)
```kotlin
@Fts4(contentEntity = Card::class)
@Entity(tableName = "cards_fts")
data class CardFts(val nameEs: String?, val nameEn: String)
```
> FTS4 da búsqueda por prefijo/tokens muy rápida sobre 13k cartas. El ranking fuzzy fino
> (Levenshtein/Jaro-Winkler) se aplica **en Kotlin** sobre el conjunto reducido que devuelve
> FTS, no sobre las 13k filas. Ver §7.

### 5.3 Entidades de **usuario** (`user.db`, lectura/escritura, sincronizable)

#### `Collection` — cartas que posee el usuario
```kotlin
@Entity(tableName = "collection", indices = [Index("cardPrintId")])
data class CollectionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardPrintId: Long? = null,   // → CardPrint.id (clave lógica). NULL = sin edición exacta
    val cardId: Long,        // desnormalizado: permite consultas sin cruzar BDs
    val quantity: Int = 1,
    val foil: Boolean = false,
    val condition: String? = null,   // "Mint", "Played"...
    val updatedAt: Long = System.currentTimeMillis() // para sync (last-write-wins)
)
```
> **`cardId` duplicado a propósito:** como las dos BDs no comparten FK, guardar también
> `cardId` evita tener que abrir el catálogo para saber qué carta es. Recomendado.
>
> **DECIDIDO:** el escaneo rápido **puede guardar la carta sin conocer la impresión exacta**
> → `cardPrintId` es **nullable**. La rareza/edición se afina después editando el ítem.

#### `Decks`
```kotlin
@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
```

#### `DeckCards` — relación mazo ↔ carta
```kotlin
@Entity(
    tableName = "deck_cards",
    primaryKeys = ["deckId", "cardId"],
    foreignKeys = [ForeignKey(
        entity = Deck::class, parentColumns = ["id"], childColumns = ["deckId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("cardId")]
)
data class DeckCard(
    val deckId: Long,
    val cardId: Long,        // → Card.id (clave lógica al catálogo)
    val quantity: Int = 1    // 1..3 según reglas del juego
)
```
> Aquí **sí** hay FK real `deckId → Decks.id` (misma BD): borrar un mazo borra sus cartas.
> `cardId` es lógica (catálogo en otra BD).
>
> **Decisión a validar:** un mazo referencia cartas por `cardId` (la carta), no por
> impresión. En Yu-Gi-Oh un mazo se compone de cartas, no de ediciones concretas → correcto.
> Pero "cartas faltantes" cruza DeckCards con Collection por `cardId`. Confirmar en §11.

#### `Settings`
```kotlin
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
```

### 5.4 Diagrama de relaciones

```
 catalog.db (solo lectura, empaquetada)      user.db (R/W, sincronizada)
 ┌──────────┐      ┌──────────────┐          ┌──────────────┐
 │  Cards   │1───* │  CardPrints  │ ·······> │  Collection  │
 │ (id PK)  │      │ (cardId)     │  lógica  │ (cardPrintId,│
 │          │      └──────────────┘          │  cardId)     │
 │          │··············································^   │
 │          │  lógica (cardId)                          │   │
 │          │                          ┌──────────┐  *  │   │
 │          │<·········lógica·········*│DeckCards │     └───┘
 └──────────┘                          │(cardId)  │
 ┌──────────┐                          └────┬─────┘
 │  Sets    │                            *  │ FK real
 └──────────┘                          ┌────┴─────┐
                                       │  Decks   │
                                       └──────────┘
   ───  FK real (Room valida)
   ···  clave lógica (cruza BDs, valida el Repository)
```

### 5.5 Migración desde lo que existe hoy (`CartaGuardada`)

Hoy la "colección" es la tabla `cartas_guardadas` (entidad `CartaGuardada`). En el nuevo
modelo eso se divide en catálogo (Cards/CardPrints) + propiedad (Collection). Plan:

- La app nueva instala `catalog.db` empaquetada y crea `user.db` vacía.
- Si hay datos antiguos en `cartas_guardadas`, una migración única los convierte a
  `CollectionItem` (cruzando `cardId` contra el catálogo). Como hoy es una app de curso con
  datos de prueba, **es aceptable empezar de cero** y no migrar — a validar.

---

## 6. Generación del catálogo empaquetado (`catalog.db`)

Dos estrategias; recomiendo la A.

### Estrategia A (recomendada): BD SQLite prempaquetada + `createFromAsset`
1. Adaptar `backend/importer.js` para que, además de (o en vez de) PostgreSQL, escriba un
   archivo **SQLite** con el esquema EXACTO de `CatalogDatabase` (incluida la tabla interna
   `room_master_table` con el `identity_hash` que Room espera).
2. Colocar ese archivo en `app/src/main/assets/database/catalog.db`.
3. En runtime: `Room.databaseBuilder(...).createFromAsset("database/catalog.db").build()`.
4. Resultado: primer arranque instantáneo, sin red, catálogo completo.

> Cuidado técnico: el `identity_hash` de Room es estricto. Forma fiable de generar el `.db`
> correcto: crear un pequeño generador (test instrumentado o build de debug) que construya
> la `CatalogDatabase` vacía con Room, inserte las filas leyendo el volcado de
> `importer.js`, y luego extraer ese archivo del dispositivo. Así el hash siempre cuadra.

### Estrategia B (más simple de arrancar): JSON empaquetado + importación en 1er arranque
1. `importer.js` exporta el catálogo a `assets/catalog.json(.gz)`.
2. En el primer arranque, la app lee el JSON e inserta en Room (Cards/CardPrints/Sets).
3. Más lento la primera vez (unos segundos para ~13k cartas) pero sin el problema del
   `identity_hash`.

**Recomendación:** empezar con **B** para validar el modelo rápido, migrar a **A** antes de
publicar para que el primer arranque sea instantáneo.

---

## 7. Búsqueda local tolerante a errores (OCR → carta)

Objetivo: `"Dragon Blanco Ojos Azulez"` → `"Dragón Blanco de Ojos Azules"`.

Pipeline en `CardRepository.buscarPorNombre(texto)` (IMPLEMENTADO en Fase 2):
1. **Normalizar** (`TextoUtil`): minúsculas, quitar acentos/ñ y signos, colapsar espacios.
2. **Índice en memoria:** se carga una vez el índice ligero de nombres ES/EN normalizados
   (`CatalogDao.obtenerIndiceNombres`) y se cachea (el catálogo es inmutable).
3. **Ranking fuzzy en Kotlin** (`Similitud`) sobre TODO el índice:
   - **Jaro-Winkler** (prefijos correctos y erratas cortas) + **Levenshtein** normalizado.
   - **Comparación por palabras**: tolera palabras de más/menos o reordenadas (típico OCR).
   - Se compara contra `nameEs` y `nameEn`; gana la mayor similitud.
4. Filtrar por umbral (0.62), ordenar por parecido y devolver hasta 30 (el 1º = mejor).

> **Decisión (Fase 2): fuzzy en memoria en lugar de FTS4.** Con ~14k nombres, puntuar todos
> en cada búsqueda es barato (decenas de ms en `Dispatchers.Default`) y tolera mucho mejor
> las erratas del OCR que FTS, que matchea por tokens y fallaría con "Azulez" vs "Azules".
> FTS queda como optimización futura si el catálogo creciera mucho. No se necesita `pg_trgm`
> ni el backend: esto replica en local lo que hacía PostgreSQL. Implementación propia
> (Levenshtein/Jaro-Winkler), sin dependencias nuevas.

---

## 8. OCR: leer solo el nombre

Problema actual: el OCR lee toda la carta. Solución:
- En `CameraScreen`, recortar el frame a la **franja superior** donde está el nombre antes de
  pasarlo a ML Kit (un `Rect` proporcional, p. ej. el ~15% superior y márgenes laterales).
- Dibujar en pantalla un **marco guía** para que el usuario alinee la carta.
- Pasar **solo ese recorte** a `TextRecognition` → menos ruido, más velocidad, no se procesa
  descripción ni estadísticas.
- Tomar la línea más larga / de mayor confianza del recorte como nombre candidato.

> Mejora futura: detección de la carta (bordes/rectángulo) para recortar con precisión
> aunque la carta esté ligeramente girada. No es MVP.

---

## 9. Firebase — solo usuario (login, sync, backup)

### Qué NO hace Firebase
- No almacena el catálogo de cartas. No se consulta para buscar cartas. Nunca.

### Qué SÍ hace
- **Firebase Auth:** login con Google (y anónimo opcional para probar sin cuenta).
- **Firestore:** copia en la nube de los datos de usuario para backup/sync entre dispositivos.
- **Storage:** solo si en el futuro el usuario sube fotos propias de sus cartas (no MVP).

### Estructura Firestore
```
users/{uid}
  ├─ profile            { displayName, email, premium: bool, updatedAt }
  ├─ collection/{itemId}  { cardPrintId, cardId, quantity, foil, condition, updatedAt }
  ├─ decks/{deckId}       { name, description, updatedAt }
  │    └─ cards/{cardId}    { cardId, quantity }      // subcolección del mazo
  └─ settings/{key}       { value, updatedAt }
```

### Estrategia de sincronización (offline-first)
- Room es **siempre** la fuente de verdad en el dispositivo. La UI nunca espera a Firebase.
- Sincronización en segundo plano con **WorkManager** (cuando hay red):
  - **Subida:** filas de `user.db` con `updatedAt` mayor que el último sync → Firestore.
  - **Bajada:** documentos de Firestore con `updatedAt` mayor → Room.
  - **Conflictos:** *last-write-wins* por `updatedAt` para MVP (sencillo y suficiente).
- Sin red: todo sigue funcionando; la cola de cambios se sube al recuperar conexión.

> Decisión a validar (§11): granularidad del backup. ¿Sync continuo por fila (más complejo,
> multi-dispositivo real) o backup/restore manual "subir todo / bajar todo" (mucho más
> simple para MVP)? Recomiendo **backup/restore manual** primero.

---

## 10. Estructura de carpetas propuesta (app)

```
com.example.yugiohscanner/
├── data/
│   ├── catalog/            # CatalogDatabase, Card/CardPrint/CardSet, CatalogDao, FTS
│   ├── user/               # UserDatabase, Collection/Deck/DeckCard/Setting, DAOs
│   ├── remote/             # Firebase (Auth, Firestore), Retrofit (solo updates/imágenes)
│   ├── repository/         # CardRepository, CollectionRepository, DeckRepository, ...
│   └── search/             # normalización + Levenshtein/Jaro-Winkler
├── domain/                 # (opcional) modelos de dominio si divergen de las entidades
├── ui/
│   ├── screens/            # Scanner, Coleccion, DeckBuilder, BusquedaAvanzada, Ajustes...
│   ├── theme/
│   └── viewmodel/
├── ocr/                    # recorte de frame + wrapper ML Kit
├── sync/                   # WorkManager workers + SyncRepository
└── MainActivity.kt
```

---

## 11. Decisiones validadas (2026-06-14) ✅

Todas confirmadas. Quedan congeladas para la implementación:

1. **Dos BDs Room** (`catalog.db` solo lectura + `user.db` sincronizable). ✅
2. **Catálogo: Estrategia B → A.** JSON importado en 1er arranque ahora; migrar a
   `createFromAsset` antes de publicar (Fase 8). ✅
3. **`Collection.cardPrintId` nullable:** se puede guardar la carta sin edición exacta; la
   rareza/edición se afina después. ✅
4. **Firebase: backup/restore manual primero.** Sync automático por fila queda para más
   adelante (Fase 6, segunda parte). ✅
5. **Empezar de cero:** no se migran los datos de prueba de `CartaGuardada`. ✅
6. **Campos extra en `Cards`** (`frameType, race, atk, def, imageUrlSmall`) aprobados. ✅

---

## 12. Qué se conserva y qué cambia (resumen)

| Hoy | Rediseño |
|-----|----------|
| `AppDatabase` (1 BD, `fallbackToDestructiveMigration`) | `CatalogDatabase` + `UserDatabase` |
| `CartaGuardada` = catálogo + propiedad mezclados | `Card`/`CardPrint` (catálogo) + `CollectionItem` (propiedad) |
| ViewModel llama a Retrofit directo | ViewModel → **Repository** → Room/OCR/Firebase |
| Búsqueda en backend (pg_trgm, requiere PC) | Búsqueda local (FTS + Levenshtein/Jaro-Winkler) |
| Backend = servidor en runtime | Backend = generador de `catalog.db` (build-time) |
| OCR lee toda la carta | OCR solo la franja del nombre |
| Sin login ni backup | Firebase Auth + Firestore (solo datos de usuario) |
```
