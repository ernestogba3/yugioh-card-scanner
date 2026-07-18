---
name: project_docs_index
description: Toda la documentación vive en docs/ con INDICE.md como puerta única; docs/memoria/ es copia de las memorias (re-sincronizar al cambiarlas)
metadata: 
  node_type: memory
  type: project
  originSessionId: 724a42d8-7da1-4f70-8a8e-eb97563b1bff
---

El usuario pidió (2026-07-18) tener TODA la documentación junta en una sola carpeta de acceso rápido.
Solución aplicada (las memorias NO se pueden mover de `.claude/.../memory/` o el sistema de memoria deja
de cargarlas):

- **`docs/INDICE.md`** = puerta de entrada única; enlaza arquitectura, roadmap, guías, memorias y CLAUDE.md.
- **`docs/memoria/`** = COPIA de solo lectura de todas las memorias (incluye MEMORY.md) + su propio README
  que explica que las originales viven en `.claude`.

**IMPORTANTE para futuras sesiones:** al crear/editar una memoria, la copia en `docs/memoria/` queda
desactualizada. Hay que **re-sincronizarla** (`cp` de `memory/*.md` a `docs/memoria/`) cuando el usuario
lo pida ("actualiza la copia de memorias") o tras cambios de memoria relevantes. No es automático.
