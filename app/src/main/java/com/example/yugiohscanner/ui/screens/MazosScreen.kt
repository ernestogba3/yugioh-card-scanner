package com.example.yugiohscanner.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yugiohscanner.data.model.Deck
import com.example.yugiohscanner.data.repository.SugerenciaArquetipo
import com.example.yugiohscanner.ui.theme.ColorMagico
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.theme.RojoCalido
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
                val abrir: (SugerenciaArquetipo) -> Unit = { sug ->
                    arquetipoSel = sug
                    viewModel.abrirArquetipo(sug.arquetipo)
                }
                item { SeccionTitulo("Sugerencias para ti") }
                // Mejor opción: la primera (ya viene ordenada por puntuación meta).
                item { HeroSugerencia(sugerencia = sugerencias.first(), onAbrir = abrir) }
                // Resto: carrusel horizontal para comparar de un vistazo.
                if (sugerencias.size > 1) {
                    item {
                        Text(
                            "Comparar otros arquetipos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(sugerencias.drop(1), key = { "s-${it.arquetipo}" }) { sug ->
                                AltSugerencia(sugerencia = sug, onAbrir = abrir)
                            }
                        }
                    }
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

/** Color de la barra de cobertura según el % (rojo bajo → oro medio → verde alto). */
private fun colorCobertura(porcentaje: Int): Color = when {
    porcentaje >= 66 -> ColorMagico
    porcentaje >= 33 -> OroYuGiOh
    else -> RojoCalido
}

/**
 * Tarjeta HÉROE de la mejor sugerencia: fondo dorado, barra de cobertura que se llena con
 * animación, cuántas cartas te faltan y acceso para crear/ver el mazo.
 */
@Composable
private fun HeroSugerencia(sugerencia: SugerenciaArquetipo, onAbrir: (SugerenciaArquetipo) -> Unit) {
    val faltan = (sugerencia.totalCatalogo - sugerencia.poseidas).coerceAtLeast(0)
    // La barra se anima de 0 al % real al aparecer (o al cambiar de arquetipo).
    val progreso = remember(sugerencia.arquetipo) { Animatable(0f) }
    LaunchedEffect(sugerencia.arquetipo) {
        progreso.animateTo(sugerencia.porcentaje / 100f, animationSpec = tween(1000))
    }

    Card(
        onClick = { onAbrir(sugerencia) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(2.dp, OroYuGiOh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(OroYuGiOh.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
                .padding(18.dp)
        ) {
            Text(
                "🏆 MEJOR OPCIÓN",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = OroClaro
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                sugerencia.arquetipo,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cobertura",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${sugerencia.porcentaje}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorCobertura(sugerencia.porcentaje)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progreso.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = colorCobertura(sugerencia.porcentaje),
                trackColor = MaterialTheme.colorScheme.surface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                if (faltan > 0) "Tienes ${sugerencia.poseidas} de ${sugerencia.totalCatalogo} · te faltan $faltan"
                else "¡Tienes las ${sugerencia.totalCatalogo} cartas del arquetipo!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (sugerencia.potencia > 0) {
                Text(
                    "⭐ ${sugerencia.potencia} carta(s) clave del meta (Limitadas/Semi)",
                    style = MaterialTheme.typography.labelSmall,
                    color = OroClaro
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OroYuGiOh)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "📋 Ver cartas y crear mazo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

/** Tarjeta compacta del carrusel: arquetipo, % grande y cuántas tienes. */
@Composable
private fun AltSugerencia(sugerencia: SugerenciaArquetipo, onAbrir: (SugerenciaArquetipo) -> Unit) {
    Card(
        onClick = { onAbrir(sugerencia) },
        modifier = Modifier.width(132.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, OroYuGiOh.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                sugerencia.arquetipo,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${sugerencia.porcentaje}%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colorCobertura(sugerencia.porcentaje)
            )
            Text(
                "${sugerencia.poseidas}/${sugerencia.totalCatalogo}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
