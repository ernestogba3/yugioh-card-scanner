package com.example.yugiohscanner.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.viewmodel.AuthViewModel
import com.example.yugiohscanner.ui.viewmodel.CatalogUpdateViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

@Composable
fun AjustesScreen(viewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    val usuario by viewModel.usuario.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()
    val ocupado by viewModel.ocupado.collectAsState()

    // Muestra el mensaje de feedback (login/backup/restore) como Toast y lo limpia.
    LaunchedEffect(mensaje) {
        mensaje?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.limpiarMensaje()
        }
    }

    // Recibe el resultado del diálogo de Google y obtiene el idToken.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        val tarea = GoogleSignIn.getSignedInAccountFromIntent(resultado.data)
        try {
            val cuenta = tarea.getResult(ApiException::class.java)
            val idToken = cuenta.idToken
            if (idToken != null) viewModel.onIdTokenRecibido(idToken)
            else viewModel.onErrorLogin("Google no devolvió token. Revisa el SHA-1 en Firebase.")
        } catch (e: ApiException) {
            viewModel.onErrorLogin("Inicio cancelado o fallido (código ${e.statusCode})")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        // --- Actualización del catálogo (independiente de Firebase) ---
        TarjetaCatalogo()

        Text(
            "Copia de seguridad en la nube (opcional)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        when {
            !viewModel.firebaseConfigurado -> FirebaseNoConfigurado()
            usuario == null -> SinSesion(
                onIniciar = {
                    val cliente = viewModel.clienteGoogle()
                    if (cliente != null) launcher.launch(cliente.signInIntent)
                    else viewModel.onErrorLogin("No se pudo preparar el inicio de sesión")
                }
            )
            else -> ConSesion(
                nombre = usuario!!.nombre,
                email = usuario!!.email,
                ocupado = ocupado,
                onBackup = { viewModel.hacerBackup() },
                onRestaurar = { viewModel.restaurar() },
                onCerrarSesion = { viewModel.cerrarSesion() }
            )
        }
    }
}

/** Tarjeta de actualización del catálogo: comprobar versión nueva y descargarla. */
@Composable
private fun TarjetaCatalogo(viewModel: CatalogUpdateViewModel = viewModel()) {
    val estado by viewModel.estado.collectAsState()
    val cartas by viewModel.cartasLocales.collectAsState()

    TarjetaInfo {
        Text("Catálogo de cartas", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "$cartas cartas · versión ${viewModel.versionLocal}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (val e = estado) {
            is CatalogUpdateViewModel.Estado.Comprobando,
            is CatalogUpdateViewModel.Estado.Descargando -> {
                val texto = if (e is CatalogUpdateViewModel.Estado.Descargando)
                    "Descargando catálogo…" else "Comprobando…"
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = OroClaro)
                Spacer(modifier = Modifier.height(8.dp))
                Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is CatalogUpdateViewModel.Estado.Disponible -> {
                Text(
                    "Hay una versión nueva (${e.cartas} cartas).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OroClaro
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.descargar(e.version, e.url) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Descargar e instalar") }
            }

            is CatalogUpdateViewModel.Estado.AlDia -> {
                Text("Ya tienes la última versión.", style = MaterialTheme.typography.bodyMedium, color = OroClaro)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.comprobar() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Comprobar de nuevo")
                }
            }

            is CatalogUpdateViewModel.Estado.Aplicada -> {
                Text("✅ Catálogo actualizado: ${e.cartas} cartas.", style = MaterialTheme.typography.bodyMedium, color = OroClaro)
            }

            is CatalogUpdateViewModel.Estado.Error -> {
                Text(e.motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.comprobar() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reintentar")
                }
            }

            CatalogUpdateViewModel.Estado.Inactivo -> {
                Button(onClick = { viewModel.comprobar() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Buscar actualizaciones")
                }
            }
        }
    }
}

@Composable
private fun FirebaseNoConfigurado() {
    TarjetaInfo {
        Text("Firebase no está configurado todavía", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Para activar el login y la copia de seguridad, sigue los pasos de " +
                "docs/TUTORIAL_FIREBASE_Y_BILLING.md: crea el proyecto en Firebase, añade la " +
                "huella SHA-1 y coloca google-services.json en la carpeta app/. " +
                "La app funciona con normalidad sin esto; solo no habrá respaldo en la nube.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SinSesion(onIniciar: () -> Unit) {
    TarjetaInfo {
        Text("Inicia sesión para guardar una copia", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onIniciar, modifier = Modifier.fillMaxWidth()) {
            Text("Iniciar sesión con Google")
        }
    }
}

@Composable
private fun ConSesion(
    nombre: String,
    email: String,
    ocupado: Boolean,
    onBackup: () -> Unit,
    onRestaurar: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    TarjetaInfo {
        Text("Conectado", style = MaterialTheme.typography.labelMedium, color = OroYuGiOh, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(nombre, style = MaterialTheme.typography.titleMedium)
        Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))
        if (ocupado) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = OroClaro)
        } else {
            Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                Text("Hacer copia de seguridad")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRestaurar, modifier = Modifier.fillMaxWidth()) {
                Text("Restaurar copia")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión")
            }
        }
    }
}

@Composable
private fun TarjetaInfo(contenido: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { contenido() }
    }
}
