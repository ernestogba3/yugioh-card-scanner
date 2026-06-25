# Tutorial — Fase 6 (Firebase) y Fase 9 (Pago premium)

Estas dos fases necesitan que **tú** crees cuentas y descargues archivos de configuración que
yo no puedo generar. Aquí tienes el paso a paso. Cuando termines la parte de "lo que haces
tú", avísame y yo escribo el código de la app.

> Regla que NO cambia: **las cartas nunca van a Firebase**. Firebase es solo para login y para
> respaldar/sincronizar los datos del usuario (colección, mazos, ajustes). El catálogo sigue
> 100% local en Room.

---

# FASE 6 — Firebase (login con Google + backup/restore)

## Parte A — Lo que haces TÚ (en la web, ~20 min)

### 1. Crear el proyecto Firebase
1. Entra en https://console.firebase.google.com con tu cuenta Google (ernestogba2@gmail.com).
2. **Agregar proyecto** → nombre p. ej. `YuGiOhScanner` → continúa (puedes desactivar Google
   Analytics, no hace falta para esto).

### 2. Registrar la app Android
1. En el proyecto, pulsa el icono de **Android** ("Agrega Firebase a tu app de Android").
2. **Nombre del paquete**: debe ser EXACTAMENTE `com.example.yugiohscanner`
   (es tu `applicationId` en `app/build.gradle.kts`).
3. Apodo: el que quieras. **SHA-1**: necesario para el login con Google. Sácalo así:
   - En la terminal del proyecto (PowerShell):
     ```powershell
     $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
     ./gradlew signingReport
     ```
   - Copia el **SHA1** de la variante `debug` (línea `SHA1: ...`) y pégalo en la consola.
   - (Para publicar en Play Store harás esto otra vez con la firma de release.)
4. **Descarga `google-services.json`** y déjalo en la carpeta **`app/`** del proyecto
   (al lado de `app/build.gradle.kts`). ⚠️ Este archivo es el que me falta para programar.

### 3. Activar los servicios en la consola
1. **Authentication** → Comenzar → pestaña **Sign-in method** → habilita **Google** →
   Guardar. (Opcional: habilita también **Anónimo** para probar sin cuenta.)
2. **Firestore Database** → Crear base de datos → modo **producción** → elige región
   (p. ej. `eur3`). Luego pega estas reglas (pestaña **Reglas**) para que cada usuario solo
   acceda a SUS datos:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{uid}/{document=**} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```

### 4. Avísame
Cuando `app/google-services.json` esté en su sitio y los servicios activados, dímelo.

## Parte B — Lo que hago YO (el código)

> No lo ejecutes tú; es la referencia de lo que añadiré cuando tengas la Parte A lista.

1. **Plugin y dependencias** (en `gradle/libs.versions.toml` y `build.gradle.kts`):
   - Plugin `com.google.gms.google-services` (a nivel raíz + aplicado en `app`).
   - `firebase-bom`, `firebase-auth`, `firebase-firestore`, `play-services-auth`,
     `androidx.work:work-runtime-ktx` (para sync en segundo plano).
2. **`AuthRepository` + `AuthViewModel`**: login con Google (One Tap / Credential Manager),
   estado `Sesion(uid, nombre, email)` o `SinSesion`. Pantalla/diálogo de login.
3. **`SyncRepository`** (backup/restore manual primero, como decidimos):
   - **Subir**: lee colección (`CartaGuardada`) y mazos (`Deck`/`DeckCard`) de Room y escribe
     en `users/{uid}/collection`, `users/{uid}/decks`, `users/{uid}/settings`.
   - **Bajar**: lee Firestore y reemplaza/combina en Room.
   - Estructura Firestore (ver `ARQUITECTURA.md` §9).
4. **UI**: en una pantalla de **Ajustes** (nueva pestaña o menú): "Iniciar sesión",
   "Hacer copia de seguridad", "Restaurar copia", y mostrar quién está conectado.
5. Más adelante: sync automático con WorkManager + `updatedAt` (last-write-wins).

## Notas importantes
- El `applicationId` real es `com.example.yugiohscanner`. Para publicar en Play Store
  conviene cambiarlo por algo propio (p. ej. `com.ernesto.yugiohscanner`); si lo cambias,
  hazlo ANTES de generar `google-services.json` (debe coincidir).
- `google-services.json` **no debe subirse a un repositorio público** (añádelo a `.gitignore`).
  No contiene secretos críticos, pero es buena práctica.

---

# FASE 9 — Pago único Premium (Google Play Billing)

> Esto solo funciona de verdad cuando la app está subida a Play Console (en pista interna o
> superior) y se prueba con una cuenta de tester. En local solo se puede dejar el código
> preparado y simular.

## Parte A — Lo que haces TÚ (en Play Console)

### 1. Cuenta de desarrollador
1. Necesitas una **cuenta de Google Play Console** (pago único de ~25 USD de alta).
   https://play.google.com/console
2. Crea la app (nombre, idioma, gratis con compras dentro de la app).

### 2. Subir un primer build
1. Genera un **App Bundle** firmado (te ayudo con la firma):
   ```powershell
   ./gradlew bundleRelease
   ```
2. Súbelo a una **pista de pruebas internas**. Billing necesita que la app exista en Play.

### 3. Crear el producto Premium
1. En Play Console → **Monetización → Productos → Productos integrados** (in-app, pago único).
2. Crea uno con ID p. ej. `premium_unlock`, ponle precio y actívalo.
3. Añade tu correo como **tester de licencias** (Configuración → Pruebas de licencia) para
   poder comprar sin que te cobren de verdad.

### 4. Avísame
Cuando el producto `premium_unlock` exista y tengas la pista interna, dímelo.

## Parte B — Lo que hago YO (el código)

1. **Dependencia** `com.android.billingclient:billing-ktx` (en el catálogo de versiones).
2. **`BillingRepository`**:
   - Conecta con Google Play, consulta el producto `premium_unlock`.
   - Lanza el flujo de compra y procesa el resultado.
   - **Reconoce (acknowledge)** la compra (obligatorio) y guarda el estado premium en Room
     (`Setting` `premium=true`) para que funcione offline.
   - Restaura compras al iniciar sesión (consulta compras previas).
3. **Gating (bloqueo) de funciones premium** — según el plan, gratis vs. premium:
   - Gratis: escáner, colección, deck builder, búsquedas.
   - Premium: backup avanzado, estadísticas avanzadas, exportación de colección,
     herramientas de torneo. **Sin anuncios intrusivos.**
   - En la UI: si `premium=false`, esas opciones muestran un candado y un botón "Hazte
     Premium" que lanza la compra.
4. Pantalla simple de "Premium" explicando qué incluye y el botón de compra.

## Orden recomendado
Haz **primero la Fase 6 (Firebase)**: es gratis, no requiere Play Console y te da login +
backup, que es lo más útil. La Fase 9 (pago) déjala para cuando vayas a publicar de verdad.
