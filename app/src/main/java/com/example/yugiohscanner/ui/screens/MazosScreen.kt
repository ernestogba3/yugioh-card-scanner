package com.example.yugiohscanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yugiohscanner.data.model.Deck
import com.example.yugiohscanner.data.repository.SugerenciaArquetipo
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.viewmodel.DeckViewModel

@Composable
fun MazosScreen(viewModel: DeckViewModel = viewModel()) {
    val mazos by viewModel.mazos.collectAsState()
    val sugerencias by viewModel.sugerencias.collectAsState()
    var mazoAbierto by remember { mutableStateOf<Deck?>(null) }
    var arquetipoSel by remember { mutableStateOf<SugerenciaArquetipo?>(null) }

    // Recalcula las sugerencias al entrar (dependen de la colección, que cambia fuera de aquí).
    LaunchedEffect(Unit) { viewModel.cargarSugerencias() }

    // Al crear un mazo desde una sugerencia, ábrelo directamente (en vez de volver al menú).
    val mazoCreadoId by viewModel.mazoCreadoId.collectAsState()
    LaunchedEffect(mazoCreadoId, mazos) {
        val id = mazoCreadoId ?: return@LaunchedEffect
        val deck = mazos.find { it.id == id } ?: return@LaunchedEffect
        arquetipoSel = null
        viewModel.cerrarArquetipo()
        mazoAbierto = deck
        viewModel.abrirMazo(deck.id)
        viewModel.cargarSugerencias()
        viewModel.consumirMazoCreado()
    }

    // Si la lista cambia, mantenemos sincronizado el mazo abierto (p. ej. su nombre).
    val abierto = mazoAbierto?.let { sel -> mazos.find { it.id == sel.id } }
    if (abierto != null) {
        DeckDetailScreen(
            deck = abierto,
            viewModel = viewModel,
            onCerrar = {
                mazoAbierto = null
                viewModel.limpiarBusqueda()
            }
        )
        return
    }

    // Previsualización de un arquetipo sugerido (grid con todas sus cartas).
    arquetipoSel?.let { sel ->
        SugerenciaArquetipoScreen(
            sugerencia = sel,
            viewModel = viewModel,
            onCerrar = { arquetipoSel = null; viewModel.cerrarArquetipo() }
        )
        return
    }

    var mostrarDialogo by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { mostrarDialogo = true },
                containerColor = OroYuGiOh,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo mazo") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sugerencias.isNotEmpty()) {
                item { SeccionTitulo("Sugerencias para ti") }
                item {
                    Text(
                        "Arquetipos que ya coleccionas. Toca uno para crear su mazo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(sugerencias, key = { "s-${it.arquetipo}" }) { sug ->
                    SugerenciaItem(sugerencia = sug, onAbrir = {
                        arquetipoSel = sug
                        viewModel.abrirArquetipo(sug.arquetipo)
                    })
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            item { Text("Mis Mazos", style = MaterialTheme.typography.headlineMedium) }

            if (mazos.isEmpty()) {
                item {
                    Text(
                        "Aún no tienes mazos.\nPulsa «Nuevo mazo» para crear uno.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(mazos, key = { it.id }) { mazo ->
                    MazoItem(
                        mazo = mazo,
                        onAbrir = { mazoAbierto = mazo; viewModel.abrirMazo(mazo.id) },
                        onEliminar = { viewModel.eliminarMazo(mazo) }
                    )
                }
            }
        }
    }

    if (mostrarDialogo) {
        DialogoNuevoMazo(
            onConfirmar = { nombre, desc ->
                viewModel.crearMazo(nombre, desc)
                mostrarDialogo = false
            },
            onCancelar = { mostrarDialogo = false }
        )
    }
}

@Composable
private fun MazoItem(mazo: Deck, onAbrir: () -> Unit, onEliminar: () -> Unit) {
    Card(
        onClick = onAbrir,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(mazo.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!mazo.description.isNullOrBlank()) {
                    Text(
                        mazo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onEliminar) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar mazo", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DialogoNuevoMazo(onConfirmar: (String, String?) -> Unit, onCancelar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Nuevo mazo") },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(nombre, descripcion) },
                enabled = nombre.isNotBlank()
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

/** Tarjeta de una sugerencia: arquetipo, cuántas tienes de cuántas y barra de progreso. */
@Composable
private fun SugerenciaItem(sugerencia: SugerenciaArquetipo, onAbrir: () -> Unit) {
    Card(
        onClick = onAbrir,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, OroYuGiOh.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sugerencia.arquetipo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${sugerencia.poseidas}/${sugerencia.totalCatalogo}",
                    style = MaterialTheme.typography.labelLarge,
                    color = OroClaro
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { sugerencia.porcentaje / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = OroYuGiOh,
                trackColor = MaterialTheme.colorScheme.surface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Tienes el ${sugerencia.porcentaje}% del arquetipo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (sugerencia.potencia > 0) {
                Text(
                    "⭐ ${sugerencia.potencia} carta(s) clave del meta (Limitadas/Semi)",
                    style = MaterialTheme.typography.labelSmall,
                    color = OroClaro
                )
            }
        }
    }
}

/** Título de sección en versalitas doradas; lo comparten esta pantalla y el detalle de mazo. */
@Composable
internal fun SeccionTitulo(texto: String) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = OroYuGiOh,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}
