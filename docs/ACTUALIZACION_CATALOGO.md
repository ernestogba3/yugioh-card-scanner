# Actualización del catálogo (Fase 10)

La app es **offline-first**: el catálogo de cartas viaja dentro del APK. Como Konami saca cartas
nuevas cada pocas semanas, este mecanismo permite **refrescar el catálogo sin republicar la app**.

## Cómo funciona

```
GitHub Action (semanal)  ──tira de──►  API pública de YGOPRODeck
        │
        ├─ genera  app/src/main/assets/database/catalog.json   (catálogo completo)
        └─ genera  app/src/main/assets/database/manifest.json  (versión + nº cartas + URL)
                                  │  commit + push al repo
                                  ▼
App (Ajustes → "Buscar actualizaciones")
        1. descarga manifest.json (pequeño) del "raw" URL de GitHub
        2. compara la versión remota con la local (guardada en SharedPreferences)
        3. si hay una nueva, descarga catalog.json entero y reconstruye Room
           (CatalogImporter.reemplazarCatalogo → borra cards/prints/sets/arts y reimporta;
            los pHash NO se tocan). Sin internet, la app sigue con el catálogo que tenga.
```

## Qué tienes que hacer tú (una sola vez)

1. **Subir el proyecto a GitHub** (si no lo está):
   ```bash
   git init
   git add .
   git commit -m "Proyecto YuGiOh Scanner"
   git branch -M main
   git remote add origin https://github.com/TU_USUARIO/TU_REPO.git
   git push -u origin main
   ```

2. **Poner tu repo en la URL del manifest.** Abre
   `app/src/main/java/com/example/yugiohscanner/data/repository/CatalogUpdateRepository.kt`
   y cambia `OWNER/REPO` por tu `usuario/repositorio` en `MANIFEST_URL` (y en
   `CATALOG_URL_FALLBACK`). Ejemplo:
   ```
   "https://raw.githubusercontent.com/ernesto/yugioh-scanner/main/app/src/main/assets/database/manifest.json"
   ```
   > El Action ya construye la URL de descarga sola (con `GITHUB_REPOSITORY`), pero la app
   > necesita saber de dónde leer el manifest, y eso es esta constante.

3. **Lanzar el Action la primera vez** para generar `catalog.json` + `manifest.json`:
   - En GitHub → pestaña **Actions** → **Actualizar catálogo** → **Run workflow**.
   - A partir de ahí se ejecuta solo cada lunes (puedes cambiar el cron en
     `.github/workflows/update-catalog.yml`).

4. **Probar en la app**: Ajustes → **Buscar actualizaciones** → **Descargar e instalar**.

## Notas

- El catálogo (~13 MB) se commitea al repo; para GitHub es perfectamente asumible.
- Los **pHash** (`phashes.json`, escáner visual) NO los regenera el Action (descargan ~20k
  imágenes, es caro). Se mantienen los que ya tengas; el escaneo por passcode/nombre no depende
  de ellos.
- Generar el catálogo en local sigue funcionando igual: `cd backend && npm run export-catalog`
  (ahora también escribe `manifest.json`). Puedes fijar la URL con la variable `CATALOG_URL`.
- Versionado: la "versión" es la fecha ISO de generación. La app guarda la última aplicada en
  `SharedPreferences("catalogo")`; mientras sea distinta de la remota, ofrece actualizar.
