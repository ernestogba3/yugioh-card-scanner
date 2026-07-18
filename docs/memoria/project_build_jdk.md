---
name: project-build-jdk
description: Cómo compilar el proyecto desde la terminal (JAVA_HOME no está en el PATH)
metadata: 
  node_type: memory
  type: project
  originSessionId: 698e5bbc-3e72-4bfc-9f42-0b785f8f5c4e
---

En la máquina del usuario no hay `java` en el PATH, así que `./gradlew` falla con "JAVA_HOME is not set". Para compilar desde la terminal hay que apuntar a la JDK que trae Android Studio:

`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` (PowerShell) antes de `.\gradlew compileDebugKotlin`.

Dentro de Android Studio no hace falta: el IDE usa esa misma JDK automáticamente. Ver [[project-yugioh-scanner]].
