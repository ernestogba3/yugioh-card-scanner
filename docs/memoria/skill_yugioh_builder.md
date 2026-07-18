---
name: skill-yugioh-builder
description: "Skill yugioh-android-builder instalado y alineado al stack real (AGP 9.x, offline-first)"
metadata: 
  node_type: memory
  type: project
  originSessionId: a4442428-a080-442c-b829-016d089a44fc
---

Existe el skill **`yugioh-android-builder`** en `C:\Users\err-r\.claude\skills\yugioh-android-builder\`
(SKILL.md + references/gradle-config.md, api-models.md, ui-patterns.md + scripts/setup_project.py).

El 2026-06-17 se adaptó del stack genérico que traía (AGP 8.2, Retrofit en runtime, una sola
BD, paquete `com.tusnombre`) al stack REAL del proyecto: AGP 9.2.1, Compose BOM 2026.02.01,
Room 2.7.0 con **dos BDs** (catalog.db empaquetada + user.db), Firebase solo login/backup,
**sin Retrofit en runtime**, paquete `com.example.yugiohscanner`. Coherente con
[[project_redesign_offline]] y [[feedback_agp9_kotlin]] (sin kotlin-android explícito, sin kotlinOptions).

**Actualizado 2026-06-25** con todo el rediseño "passcode + binder": SKILL.md reescrito (estructura
REAL `data/catalog`+`data/db`+`data/scan`+`data/remote`+`ui/components`; tabla de fases con estado;
stack con tooling Node/sharp y actualización por HttpURLConnection; disparadores nuevos: passcode,
phash, precios, mazos por arquetipo, tema binder, carta holografica, actualizar catalogo) + NUEVA
referencia `references/subsistemas-2026.md` que documenta los 8 subsistemas (escáner passcode/pHash,
precios, mazos por arquetipo+ReglasMazo, tema cálido, CartaHolografica foil-por-rareza, auto-update
del catálogo, versiones Room catalog.db v4/user.db v8, tooling backend). Ver [[project_rediseno_passcode]].

**No correr `setup_project.py` sobre el proyecto actual**: ya tiene Gradle bien configurado;
el script solo sirve para empezar de cero. El skill se recarga al reiniciar Claude Code.
