---
name: feedback_compose_revision
description: "Convenciones de revisión de código del proyecto (Compose drag-coroutines, LF) salidas del /code-review 2026-06-25"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 7197471d-8ed5-4aac-8503-37e3ad20ebef
---

Del `/code-review` del 2026-06-25 sobre la app YuGiOh Scanner salieron dos convenciones a respetar:

1. **En manejadores de gestos de Compose (`detectDragGestures`/`pointerInput`), NO lanzar una
   corrutina por cada eje y por cada evento de drag.** El `onDrag` se dispara decenas de veces por
   segundo; hacer `scope.launch { rotY.snapTo(...) }` y otro `scope.launch { rotX.snapTo(...) }`
   crea muchísimas corrutinas efímeras y, al ser launches separados sobre el mismo `Animatable`, su
   orden no está garantizado (micro-jitter). **Patrón correcto:** UNA corrutina por evento con los
   `snapTo` en orden:
   ```kotlin
   scope.launch {
       rotY.snapTo((rotY.value + drag.x * f).coerceIn(-MAX, MAX))
       rotX.snapTo((rotX.value - drag.y * f).coerceIn(-MAX, MAX))
   }
   ```
   Aplicado en `ui/components/CartaHolografica.kt`.

2. **Fin de línea: el repo usa LF.** Hay un `.gitattributes` en la raíz (`* text=auto eol=lf`,
   `.bat`/`.cmd` en CRLF, binarios marcados `binary`) para silenciar los avisos
   `LF will be replaced by CRLF` en Windows. Si se renormalizan los archivos existentes:
   `git add --renormalize .` (genera un commit de solo-fin-de-línea, opcional).

**Why:** rendimiento/estabilidad de la animación holográfica y evitar ruido de diffs por CRLF.
**How to apply:** seguir ambos patrones en futuros gestos/Canvas y al añadir archivos de texto.
Ver [[project_rediseno_passcode]] (detalle "carta viva") y [[skill_yugioh_builder]].
