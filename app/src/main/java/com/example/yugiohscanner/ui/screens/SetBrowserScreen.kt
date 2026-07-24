package com.example.yugiohscanner.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yugiohscanner.data.search.TextoUtil
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.viewmodel.EstadoAlbum
import com.example.yugiohscanner.ui.viewmodel.SetBrowserViewModel
import com.example.yugiohscanner.ui.viewmodel.SetCatalogo

/**
 * Navegador "Por set": busca cualquier set del catálogo y, al abrirlo, muestra su álbum en modo
 * AÑADIR (añadir todo el set o elegir cartas). Es el flujo estrella para Structure Decks.
 */
@Composable
fun SetBrowserScreen(
    viewModel: SetBrowserViewModel = viewModel(),
    onCerrar: () -> Unit
) {
    val context = LocalContext.current
    val album by viewModel.album.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()

    // Confirmación tras añadir (o error): un aviso breve, y se descarta.
    LaunchedEffect(mensaje) {
        mensaje?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.descartarMensaje()
        }
    }

    // Si hay un set abierto, mostramos su álbum en modo añadir.
    if (album is EstadoAlbum.Exito || album is EstadoAlbum.Cargando) {
        val setName = (album as? EstadoAlbum.Exito)?.setName ?: ""
        SetAlbumScreen(
            estado = album,
            onCartaClick = { /* en el navegador las cartas son para añadir, no para ver detalle */ },
            onCerrar = { viewModel.cerrarAlbum() },
            onAnadirCartas = { ids -> viewModel.anadirCartas(setName, ids) }
        )
        return
    }

    val sets by viewModel.setsFiltrados.collectAsState()
    val busqueda by viewModel.busqueda.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCerrar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("Añadir por set", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = busqueda,
            onValueChange = { viewModel.buscar(it) },
            label = { Text("Set o carta (también en español)") },
            placeholder = { Text("Ej: Burst of Destiny, SDBE, Ojos Azules…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            supportingText = { Text("Busca por nombre/código del set, o por una carta que contenga (ES o EN).") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (sets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (busqueda.isBlank()) "📦 Cargando sets del catálogo…"
                    else "No hay sets que coincidan con \"$busqueda\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sets, key = { it.setName }) { set ->
                    SetItem(set = set, onClick = { viewModel.abrirAlbum(set.setName) })
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SetItem(set: SetCatalogo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    TextoUtil.decodificarHtml(set.setName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        set.setCode?.takeIf { it.isNotBlank() }?.let { append("$it · ") }
                        append("${set.total} carta(s)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Si el set apareció por contener una carta buscada, se muestra esa carta:
                // en español arriba y, si existe distinta, en inglés justo debajo.
                if (set.coincidenciaEn != null) {
                    val es = set.coincidenciaEs?.takeIf { it.isNotBlank() }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Contiene: ${es ?: set.coincidenciaEn}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = OroClaro,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (es != null && !es.equals(set.coincidenciaEn, ignoreCase = true)) {
                        Text(
                            text = set.coincidenciaEn,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Abrir set",
                    tint = OroClaro,
                    modifier = Modifier
                        .padding(6.dp)
                        .width(20.dp)
                        .height(20.dp)
                )
            }
        }
    }
}
