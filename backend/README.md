# Backend Yu-Gi-Oh (Node.js + PostgreSQL)

API propia con la base de cartas de Yu-Gi-Oh importada desde YGOPRODeck y búsqueda
fuzzy con `pg_trgm` (tolerante a erratas del OCR).

## Requisitos
- Node.js 18 o superior
- PostgreSQL 14 o superior

## 1. Crear la base de datos
```bash
# Desde psql o pgAdmin:
CREATE DATABASE yugioh;
```

## 2. Configurar variables
```bash
cd backend
cp .env.example .env      # En Windows PowerShell: copy .env.example .env
# Edita .env con tu usuario/contraseña de PostgreSQL
```

## 3. Instalar dependencias
```bash
npm install
```

## 4. Importar las cartas (una sola vez, ~13.000 cartas)
```bash
npm run import
```
Esto crea las tablas, descarga todo el catálogo de YGOPRODeck y lo vuelca a PostgreSQL.
Para actualizar el catálogo en el futuro, vuelve a ejecutarlo.

## 5. Arrancar el servidor
```bash
npm start          # o "npm run dev" para recarga automática
# 🚀 Backend escuchando en http://localhost:3000
```

## Endpoints
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/health` | Comprobación rápida |
| GET | `/cards/search?q=blue+eyes` | Búsqueda fuzzy por nombre |
| GET | `/cards/search?type=Effect+Monster&attribute=DARK&level=4` | Búsqueda por filtros |
| GET | `/cards/search?q=dark&atk=2000` | Combina nombre + filtros (atk/def = mínimo) |
| GET | `/cards/:id` | Ficha de una carta |
| GET | `/sets` | Todos los sets con su nº total de cartas |

Prueba rápida:
```bash
curl "http://localhost:3000/cards/search?q=blue+eyes+white+dragon"
```

## Búsqueda fuzzy
- El índice GIN de trigramas (`idx_cards_name_trgm`) acelera `name % 'texto'`.
- Se ordena por `similarity`/`word_similarity`, ideal para nombres mal leídos por el OCR.
- Umbral de similitud por defecto de pg_trgm: 0.3. Para hacerlo más/menos estricto:
  `SELECT set_limit(0.2);` (por sesión) o ajusta `pg_trgm.similarity_threshold`.

## Conectar la app Android
El JSON de `/cards/search` tiene el **mismo formato** que YGOPRODeck (`{ "data": [...] }`
con `card_images` y `card_sets`), así que el modelo `CartaYuGiOh` no cambia.

En `RetrofitInstance.kt` cambia la URL base:
```kotlin
// Emulador Android -> "localhost" del PC se accede como 10.0.2.2
private const val BASE_URL = "http://10.0.2.2:3000/"
```
Y en `YuGiOhApiService.kt` apunta al nuevo endpoint:
```kotlin
@GET("cards/search")
suspend fun buscarCartas(
    @Query("q") nombre: String? = null,
    @Query("type") tipo: String? = null,
    @Query("level") nivel: Int? = null,
    @Query("attribute") atributo: String? = null,
    @Query("atk") atk: Int? = null,   // ahora número (mínimo), no "gte2000"
    @Query("def") def: Int? = null
): RespuestaApi

@GET("sets")
suspend fun obtenerSets(): List<SetInfo>
```
Como es HTTP (no HTTPS), Android necesita permitir tráfico claro en desarrollo:
en `AndroidManifest.xml`, dentro de `<application ...>` añade
`android:usesCleartextTraffic="true"`.
