---
name: project-yugioh-scanner
description: "Yu-Gi-Oh card scanner Android app — Kotlin 2.1.0, AGP 9.2.1, KSP, Room, CameraX, ML Kit, Gson, Coil, Firebase (offline-first; Retrofit solo build-time)"
metadata: 
  node_type: memory
  type: project
  originSessionId: 60244fb1-e963-44c5-8c16-655323d48e2a
---

Android app to scan Yu-Gi-Oh cards using the camera.
Path: C:\Users\err-r\Desktop\Curso de programacion 2026\ProyectoVideoJuego\EscanerCartasYuGiOh

Stack (verificado en libs.versions.toml 2026-06-19): Kotlin 2.1.0, AGP 9.2.1, KSP 2.1.0-1.0.29, Jetpack Compose (BOM 2026.02.01), Room 2.7.0, CameraX 1.3.4, ML Kit text-recognition 16.0.1, Gson 2.11.0, Coil 2.7.0, Firebase (BoM 33.7.0: Auth + Firestore), **OpenCV 4.13.0** (`org.opencv:opencv` de Maven Central; detección del rectángulo de la carta en el escáner, ver `data/scan/DetectorCarta.kt`).

Arquitectura **offline-first** (ver [[project_redesign_offline]]): la app busca cartas en un catálogo Room local empaquetado; NO usa Retrofit en runtime. Retrofit/YGOPRODeck quedan **solo para build-time** (el backend genera el catálogo) y, opcionalmente, descarga de imágenes con Coil.

**Why:** Student project for a 2026 programming course.
**How to apply:** When suggesting changes, always reference the version catalog (libs.versions.toml) and use catalog aliases — never hardcode dependency versions in build files.
