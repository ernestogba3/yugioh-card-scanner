# Ideas futuras — Yu-Gi-Oh Card Scanner

> Recogidas el 2026-06-17. Complementa a `ROADMAP.md`. Cada idea lleva una valoración de
> viabilidad y el enfoque técnico previsto, para retomarla sin volver a analizarla.

---

## 1. Valor de la carta en EUR y USD  *(futuro, pedido explícito)*
**Qué:** mostrar el precio de mercado de cada carta (CardMarket €, TCGPlayer $) en el detalle
y, opcionalmente, el valor total de la colección.

**Enfoque:**
- El catálogo offline (`Card`) hoy NO tiene precios. Hay que añadirlos en **build-time**:
  `backend/importer.js` → leer `card_prices` de YGOPRODeck (`cardmarket_price`,
  `tcgplayer_price`) y volcarlos al catálogo (campos `priceEur`, `priceUsd` en `Card`, o una
  tabla `card_prices` aparte). Regenerar `catalog.json`/`catalog.db`.
- Los precios son volátiles → guardar también un **snapshot** del precio en `CartaGuardada`
  al añadir la carta (como `priceEur`/`priceUsd`) para histórico de valor.
- UI: el spec de `ui-patterns.md` ya describe `PriceDisplay` (CardMarket primario, TCGPlayer
  secundario) y el "valor total €" en Colección. Reactivar cuando existan los datos.
**Coste:** medio-alto (importer + esquema catálogo + UI). **Bloqueante:** regenerar catálogo.

## 2. Filtros lo más completos posible  *(parcialmente hecho; ampliar)*
**Ya hay:** nombre, tipo, nivel, atributo, raza, arquetipo, ATK mín., DEF mín., "solo en mi
colección".
**Añadir:**
- **Rareza** (requiere cruzar con `card_prints`; hoy la búsqueda devuelve cartas, no impresiones).
- **frameType** (normal/effect/fusion/synchro/xyz/link/pendulum) — ya está en `Card`.
- **ATK/DEF máximos** (además de mínimos) y **rango de nivel** (p. ej. 4–8).
- **Ordenar resultados** (nombre, ATK, DEF, nivel) — local, sin tocar la BD.
- **Solo me faltan / solo tengo** cruzando con colección.
**Coste:** bajo-medio. La mayoría son cambios en `CatalogDao.buscarPorFiltros` + UI; rareza es
el único que necesita el modelo de impresiones.

## 3. Descripciones de carta en español  *(futuro)*
**Qué:** igual que el nombre (`nameEs`), tener `descriptionEs`.
**Enfoque:** el catálogo solo trae `description` (inglés). Opciones:
- **(recomendada)** build-time: `importer.js` pide a YGOPRODeck las descripciones en español
  (`language=es` donde exista) y las guarda en un campo `descriptionEs` de `Card`. Regenerar
  catálogo. Si no hay traducción oficial, queda `null` y se muestra el inglés.
- on-demand con API de traducción (se descartó en el rediseño offline-first; rompería "sin red").
**Coste:** medio (importer + esquema catálogo + fallback en `DetalleCartaScreen`).

## 4. Animación de entrada (splash)  *(futuro)*
**Qué:** pantalla/animación de bienvenida al abrir la app.
**Enfoque:** API `SplashScreen` de Android 12+ (icono animado) o una pantalla Compose intro
con animación (logo dorado + fade) antes de `MainScreen`. Autocontenido, sin datos.
**Coste:** bajo.

## 5. Login de inicio de sesión  *(futuro, condicionado a publicar en Play)*
**Qué:** que cada usuario tenga su colección/mazos personalizados.
**Enfoque:** Firebase Auth (Google) ya está cableado (`AuthRepository`, `AuthViewModel`,
`AjustesScreen`, sync backup/restore en Firestore). Falta:
- **Puerta de login** opcional al inicio (o seguir como invitado).
- Asociar `user.db` ↔ `uid` y sync automático por fila (WorkManager) — hoy es backup/restore
  manual. Ver `ARQUITECTURA.md` §9.
**Coste:** medio-alto. **Depende de:** configuración Firebase (ya existe el proyecto).

## 6. Deck creator: colección + cartas que faltan en gris  *(pedido concreto, hacer pronto)*
**Qué:**
- Al crear/editar un mazo, **mostrar primero las cartas de tu colección** para añadirlas con un toque.
- Si no tienes la carta, usar el **buscador** (ya existe) para añadirla igualmente.
- Las cartas del mazo que **no posees** se muestran **en gris**, para ver de un vistazo que el
  mazo no está completo (ya se calcula `faltan`/`enColeccion` en `CartaEnMazo`).
**Enfoque:** añadir en `DeckViewModel`/`DeckRepository` una lista de cartas de la colección
(distintas) como sugerencias; en `CartaEnMazoItem`, atenuar (alpha/escala de grises) la imagen
y el texto cuando `enColeccion < cantidad`.
**Coste:** medio. Totalmente viable con la arquitectura actual.

## 7. Escáner con tracking en vivo  *(baja prioridad — el buscador ya cubre el caso)*
**Qué:** seguir la carta en tiempo real y buscar por nombre **o id** automáticamente.
**Enfoque:** pasar de captura puntual a `ImageAnalysis` continuo (analizar frames con ML Kit),
detectar el rectángulo de la carta y lanzar la búsqueda fuzzy al vuelo. El id de carta no es
legible a simple vista en la carta física, así que seguiría siendo por nombre (OCR).
**Coste:** alto. **Nota del usuario:** no es relevante ahora porque el buscador funciona.

## 8. Foto de perfil = personaje de Yu-Gi-Oh  *(futuro, tras login)*
**Qué:** en Ajustes, tras iniciar sesión, usar un avatar de un personaje de Yu-Gi-Oh como
imagen de perfil.
**Enfoque:** asset(s) local(es) en `res/drawable`; mostrarlo en `AjustesScreen` junto al
nombre/email del usuario. Opcional: elegir entre varios avatares.
**Coste:** bajo. **Depende de:** que el login muestre datos de usuario.
> Cuidado con copyright si se publica: usar arte propio/estilizado, no imágenes oficiales.
