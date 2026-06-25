package com.example.yugiohscanner.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yugiohscanner.data.remote.AuthRepository
import com.example.yugiohscanner.data.remote.SyncRepository
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Datos del usuario que muestra la UI (sin exponer el tipo de Firebase). */
data class UsuarioUi(val uid: String, val nombre: String, val email: String)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = AuthRepository(application)
    private val syncRepo = SyncRepository(application)

    /** Si false, la pantalla de Ajustes invita a configurar Firebase (no usa la nube). */
    val firebaseConfigurado: Boolean = authRepo.configurado()

    private val _usuario = MutableStateFlow(authRepo.usuarioActual?.toUi())
    val usuario: StateFlow<UsuarioUi?> = _usuario

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje

    private val _ocupado = MutableStateFlow(false)
    val ocupado: StateFlow<Boolean> = _ocupado

    fun clienteGoogle(): GoogleSignInClient? = authRepo.clienteGoogle()

    fun onIdTokenRecibido(idToken: String) {
        viewModelScope.launch {
            _ocupado.value = true
            authRepo.loginConGoogle(idToken)
                .onSuccess {
                    _usuario.value = it.toUi()
                    _mensaje.value = "Sesión iniciada como ${it.email ?: "usuario"}"
                }
                .onFailure { _mensaje.value = "Error al iniciar sesión: ${it.message}" }
            _ocupado.value = false
        }
    }

    fun onErrorLogin(texto: String) {
        _mensaje.value = texto
    }

    fun cerrarSesion() {
        authRepo.cerrarSesion()
        _usuario.value = null
        _mensaje.value = "Sesión cerrada"
    }

    fun hacerBackup() {
        val uid = authRepo.usuarioActual?.uid ?: return
        viewModelScope.launch {
            _ocupado.value = true
            syncRepo.subir(uid)
                .onSuccess { _mensaje.value = "Copia subida: $it carta(s)" }
                .onFailure { _mensaje.value = "Error en la copia: ${it.message}" }
            _ocupado.value = false
        }
    }

    fun restaurar() {
        val uid = authRepo.usuarioActual?.uid ?: return
        viewModelScope.launch {
            _ocupado.value = true
            syncRepo.bajar(uid)
                .onSuccess { _mensaje.value = "Restaurado: ${it.cartas} carta(s), ${it.mazos} mazo(s)" }
                .onFailure { _mensaje.value = "Error al restaurar: ${it.message}" }
            _ocupado.value = false
        }
    }

    fun limpiarMensaje() {
        _mensaje.value = null
    }

    private fun FirebaseUser.toUi() = UsuarioUi(uid, displayName ?: "Usuario", email ?: "")
}
