# 📚 Índice de documentación — Yu-Gi-Oh Card Scanner

Punto de entrada único a **toda** la documentación del proyecto. Todo está bajo esta carpeta
`docs/`, así que basta con abrir aquí y navegar desde los enlaces.

_Última actualización: 2026-07-18._

---

## 🏛️ Arquitectura y diseño

| Documento | De qué trata |
|---|---|
| [ARQUITECTURA.md](ARQUITECTURA.md) | Arquitectura **offline-first**: dos bases de datos Room (catálogo de solo lectura + datos de usuario), Firebase solo para login/backup, sin backend en runtime. |
| [ROADMAP.md](ROADMAP.md) | Estado del proyecto **por fases**: qué está hecho, en curso y pendiente. |
| [IDEAS_FUTURAS.md](IDEAS_FUTURAS.md) | Ideas y mejoras candidatas para el futuro. |

## 🛠️ Guías y procesos

| Documento | De qué trata |
|---|---|
| [ACTUALIZACION_CATALOGO.md](ACTUALIZACION_CATALOGO.md) | Cómo se regenera/actualiza el catálogo de cartas empaquetado en la app. |
| [TUTORIAL_FIREBASE_Y_BILLING.md](TUTORIAL_FIREBASE_Y_BILLING.md) | Configuración de Firebase (Auth + Firestore) y facturación. |

## 🧠 Memoria del proyecto (notas de Claude)

Copia legible de las memorias que Claude usa entre sesiones. Ver el aviso sobre dónde viven las
"originales" en el README de esa carpeta.

| Documento | De qué trata |
|---|---|
| [memoria/README.md](memoria/README.md) | Qué es la copia de memorias y cómo mantenerla. |
| [memoria/MEMORY.md](memoria/MEMORY.md) | Índice de todas las notas de memoria. |
| [memoria/project_uiux_mockups.md](memoria/project_uiux_mockups.md) | Plan UI/UX (Toast, chips, valor total + histórico, onboarding, rediseño de mazos). |
| [memoria/project_mejoras_plan.md](memoria/project_mejoras_plan.md) | Plan de mejoras (tests, refactor, icono). |
| [memoria/project_rediseno_passcode.md](memoria/project_rediseno_passcode.md) | Rediseño passcode + pHash + tema "binder". |

_(La carpeta `memoria/` contiene también el resto de notas: perfil de usuario, backend, convenciones, etc.)_

## 📄 Otros documentos del repositorio

| Documento | Dónde | De qué trata |
|---|---|---|
| [CLAUDE.md](../CLAUDE.md) | raíz del repo | Guía para Claude Code: stack, comandos, patrones y contexto del proyecto. |

---

## 🗂️ ¿Dónde está cada cosa?

```
EscanerCartasYuGiOh/
├── CLAUDE.md              ← guía para Claude (raíz)
└── docs/                  ← 📚 TODA la documentación
    ├── INDICE.md          ← este archivo (empieza por aquí)
    ├── ARQUITECTURA.md
    ├── ROADMAP.md
    ├── IDEAS_FUTURAS.md
    ├── ACTUALIZACION_CATALOGO.md
    ├── TUTORIAL_FIREBASE_Y_BILLING.md
    └── memoria/           ← copia legible de las memorias de Claude
        ├── README.md
        ├── MEMORY.md
        └── (una nota .md por memoria)
```

> **Nota:** las memorias que Claude carga en cada sesión viven fuera del repo, en
> `C:\Users\err-r\.claude\...\memory\`. La carpeta `docs/memoria/` es una **copia de lectura**;
> para actualizarla, pídeme *"actualiza la copia de memorias"*.
