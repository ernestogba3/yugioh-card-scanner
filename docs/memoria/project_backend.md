---
name: project-backend
description: El proyecto añade un backend propio Node.js + PostgreSQL (carpeta backend/)
metadata: 
  node_type: memory
  type: project
  originSessionId: 698e5bbc-3e72-4bfc-9f42-0b785f8f5c4e
---

A partir del 2026-06-13 el proyecto deja de pegar directamente contra YGOPRODeck desde Android y pasa a tener **backend propio** en `backend/` (Node.js + Express + PostgreSQL).

- **Datos**: se importan masivamente desde YGOPRODeck (`cardinfo.php` sin parámetros = ~13.000 cartas; `cardsets.php` = sets) con `npm run import`.
- **Búsqueda fuzzy** con extensión `pg_trgm` (índice GIN `gin_trgm_ops`), tolerante a erratas del OCR. Endpoint clave: `GET /cards/search`. Busca contra nombre EN **y ES** (las cartas físicas del usuario están en español).
- **Nombres en español (`cards.name_es`)**: YGOPRODeck NO tiene español (solo en/fr/de/it/pt; `language=es` devuelve 400). Se obtienen de **YGOResources/YGOrganization** (`https://db.ygoresources.com/data/idx/card/name/{en,es}`, formato `{nombre: [konamiId]}`), cruzando EN↔ES por konamiId y emparejando con nuestras cartas por el nombre inglés. ~13.634/14.388 cartas con name_es. Lo hace `importSpanishNames()` en el importer.
- **Formato JSON idéntico a YGOPRODeck** a propósito, para que el modelo Android `CartaYuGiOh` no cambie. Requiere `usesCleartextTraffic=true` (backend es http).

**Por qué**: control total, sin límites de rate, búsqueda a medida.

**Estado a 2026-06-13 (sesión completada)**: Android ya apunta al backend propio. `BASE_URL = http://localhost:3000/`. Flujo final: Cámara → recorte zona nombre + OCR (`CameraScreen.recortarZonaNombre`) → `GET /cards/search?q=` (fuzzy) → carta. Se eliminó la traducción (TraduccionInstance/ApiService/RespuestaTraduccion borrados); pg_trgm corrige las erratas del OCR directamente.

**Setup local que funciona (PostgreSQL 17 ya instalado y corriendo)**:
- Contraseña de postgres por defecto: `postgres`. `.env` = `postgres://postgres:postgres@localhost:5432/yugioh`.
- Datos ya importados (14.388 cartas, 1.020 sets). NO repetir import salvo que se quiera refrescar.
- psql en `C:\Program Files\PostgreSQL\17\bin\psql.exe`.

**Para trabajar (móvil físico por USB, no emulador)** hay que repetir cada sesión (no son permanentes):
1. Arrancar backend: en `backend/` → `npm start`.
2. Puente USB: `adb reverse tcp:3000 tcp:3000` (adb en `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`). Se borra al desconectar el cable o reiniciar.
- `10.0.2.2` solo serviría para emulador SIN adb reverse.

Ver [[project-yugioh-scanner]] y [[project-build-jdk]].
