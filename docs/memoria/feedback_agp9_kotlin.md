---
name: feedback-agp9-kotlin
description: "AGP 9.x auto-applies kotlin-android — never declare it explicitly, and drop kotlinOptions"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 60244fb1-e963-44c5-8c16-655323d48e2a
---

Con AGP 9.x, NO se debe declarar el plugin `kotlin-android` (`org.jetbrains.kotlin.android`) explícitamente en ningún módulo ni en el root build file. AGP ya lo aplica internamente y declararlo de nuevo genera: `Cannot add extension with name 'kotlin', as there is an extension already registered with that name.`

Tampoco se debe usar el bloque `kotlinOptions { jvmTarget = "..." }` porque sin el plugin declarado explícitamente, el DSL accessor no existe en tiempo de compilación del script. AGP 9.x propaga el JVM target desde `compileOptions { targetCompatibility = JavaVersion.VERSION_11 }` automáticamente.

**Why:** Descubierto corrigiendo el proyecto YuGiOh Scanner (Gradle sync fallaba con dos errores encadenados).

**How to apply:** Para proyectos Android con AGP 9.x:
- `app/build.gradle.kts` plugins: solo `android.application`, `kotlin.compose`, `ksp`
- Root `build.gradle.kts` plugins: solo esos mismos con `apply false`
- Eliminar `kotlinOptions {}` del bloque `android {}`, dejar solo `compileOptions`
