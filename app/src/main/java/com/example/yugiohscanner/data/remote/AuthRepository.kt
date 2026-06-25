package com.example.yugiohscanner.data.remote

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Login con Google a través de Firebase Auth. Está escrito para ser TOLERANTE a que Firebase
 * todavía no esté configurado: si falta `google-services.json`, `configurado()` devuelve false
 * y la app no intenta usar Firebase (la pantalla de Ajustes muestra un aviso).
 */
class AuthRepository(context: Context) {

    private val appContext = context.applicationContext

    /** true solo si `google-services.json` está presente y Firebase se ha inicializado. */
    fun configurado(): Boolean = FirebaseApp.getApps(appContext).isNotEmpty()

    private val auth: FirebaseAuth?
        get() = if (configurado()) FirebaseAuth.getInstance() else null

    val usuarioActual: FirebaseUser?
        get() = auth?.currentUser

    /** Cliente de Google Sign-In configurado con el Web Client ID del proyecto Firebase. */
    fun clienteGoogle(): GoogleSignInClient? {
        val webClientId = webClientId() ?: return null
        val opciones = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(appContext, opciones)
    }

    /** Canjea el idToken de Google por una sesión de Firebase. */
    suspend fun loginConGoogle(idToken: String): Result<FirebaseUser> {
        val auth = auth ?: return Result.failure(IllegalStateException("Firebase no configurado"))
        return try {
            val credencial = GoogleAuthProvider.getCredential(idToken, null)
            val usuario = auth.signInWithCredential(credencial).await().user
                ?: return Result.failure(IllegalStateException("No se obtuvo el usuario"))
            Result.success(usuario)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cerrarSesion() {
        auth?.signOut()
        clienteGoogle()?.signOut()
    }

    /**
     * `default_web_client_id` lo genera el plugin google-services a partir del JSON. Lo leemos
     * por nombre (no por `R.string.`) para que el código COMPILE aunque el recurso aún no exista.
     */
    private fun webClientId(): String? {
        val resId = appContext.resources.getIdentifier(
            "default_web_client_id", "string", appContext.packageName
        )
        return if (resId != 0) appContext.getString(resId) else null
    }
}
