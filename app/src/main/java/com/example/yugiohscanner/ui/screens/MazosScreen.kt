package com.example.yugiohscanner.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.data.model.Deck
import com.example.yugiohscanner.data.repository.CartaArquetipo
import com.example.yugiohscanner.data.repository.CartaEnMazo
import com.example.yugiohscanner.data.repository.SugerenciaArquetipo
import com.example.yugiohscanner.ui.theme.ColorMagico
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.theme.colorPorTipo
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

@Composable
private fun DeckDetailScreen(deck: Deck, viewModel: DeckViewModel, onCerrar: () -> Unit) {
    val detalle by viewModel.detalle.collectAsState()
    val resultados by viewModel.resultados.collectAsState()
    val stats by viewModel.estadisticas.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(deck.id) { viewModel.abrirMazo(deck.id) }

    // Avisos de límite (Deck Principal/Extra lleno, máx. copias): se muestran y se limpian.
    LaunchedEffect(mensaje) {
        mensaje?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.limpiarMensaje()
        }
    }

    var textoBusqueda by remember { mutableStateOf("") }
    var mostrarRenombrar by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = onCerrar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(deck.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${stats.total} carta(s)" + if (stats.faltan > 0) " · te faltan ${stats.faltan}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (stats.faltan > 0) MaterialTheme.colorScheme.error else OroClaro
                )
            }
            IconButton(onClick = { mostrarRenombrar = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Renombrar mazo", tint = OroYuGiOh)
            }
        }

        // Estado de tamaño del mazo (reglas Yu-Gi-Oh: Principal 40–60, Extra ≤ 15).
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChipEstado(
                etiqueta = "Principal",
                valor = "${stats.principal}/40–60",
                valido = stats.principalValido,
                modifier = Modifier.weight(1f)
            )
            ChipEstado(
                etiqueta = "Extra",
                valor = "${stats.extra}/15",
                valido = stats.extraValido,
                modifier = Modifier.weight(1f)
            )
        }

        // Reparto por categoría.
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChipResumen("Monstruos", stats.monstruos, Modifier.weight(1f))
            ChipResumen("Mágicas", stats.magicas, Modifier.weight(1f))
            ChipResumen("Trampas", stats.trampas, Modifier.weight(1f))
        }

        // Acciones del mazo.
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { viewModel.duplicarMazo(deck.id) }) { Text("Duplicar") }
            TextButton(
                onClick = {
                    val envio = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, deck.name)
                        putExtra(Intent.EXTRA_TEXT, viewModel.textoExportable(deck.name))
                    }
                    context.startActivity(Intent.createChooser(envio, "Compartir mazo"))
                },
                enabled = detalle.isNotEmpty()
            ) { Text("Compartir") }
        }

        OutlinedTextField(
            value = textoBusqueda,
            onValueChange = {
                textoBusqueda = it
                viewModel.buscarParaAnadir(it)
            },
            label = { Text("Añadir carta (busca por nombre)") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Resultados de búsqueda para añadir (solo cuando se está buscando).
            if (resultados.isNotEmpty()) {
                item { SeccionTitulo("Resultados — toca ＋ para añadir") }
                items(resultados, key = { "r-${it.id}" }) { carta ->
                    ResultadoBusquedaItem(carta = carta, onAnadir = { viewModel.anadirCarta(carta) })
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // Cartas que ya están en el mazo.
            if (detalle.isEmpty()) {
                item {
                    Text(
                        "Este mazo está vacío. Busca cartas arriba y añádelas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                item { SeccionTitulo("En el mazo (${detalle.size} distintas)") }
                items(detalle, key = { "d-${it.carta.id}" }) { enMazo ->
                    CartaEnMazoItem(
                        enMazo = enMazo,
                        onMas = { viewModel.cambiarCantidad(enMazo.carta, +1) },
                        onMenos = { viewModel.cambiarCantidad(enMazo.carta, -1) },
                        onQuitar = { viewModel.quitarCarta(enMazo.carta) }
                    )
                }
            }
        }
    }

    if (mostrarRenombrar) {
        DialogoEditarMazo(
            nombreInicial = deck.name,
            descripcionInicial = deck.description ?: "",
            onConfirmar = { nombre, desc ->
                viewModel.renombrarMazo(deck.id, nombre, desc)
                mostrarRenombrar = false
            },
            onCancelar = { mostrarRenombrar = false }
        )
    }
}

/** Chip pequeño con un total por categoría (monstruos/mágicas/trampas). */
@Composable
private fun ChipResumen(etiqueta: String, valor: Int, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$valor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OroClaro)
            Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Chip de estado de una zona del mazo: verde si cumple la regla, rojo si no. */
@Composable
private fun ChipEstado(etiqueta: String, valor: String, valido: Boolean, modifier: Modifier = Modifier) {
    val color = if (valido) ColorMagico else MaterialTheme.colorScheme.error
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Diálogo para renombrar/editar la descripción de un mazo (campos prerrellenados). */
@Composable
private fun DialogoEditarMazo(
    nombreInicial: String,
    descripcionInicial: String,
    onConfirmar: (String, String?) -> Unit,
    onCancelar: () -> Unit
) {
    var nombre by remember { mutableStateOf(nombreInicial) }
    var descripcion by remember { mutableStateOf(descripcionInicial) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Editar mazo") },
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
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun SeccionTitulo(texto: String) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = OroYuGiOh,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ResultadoBusquedaItem(carta: CartaYuGiOh, onAnadir: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ImagenCarta(carta)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                carta.nombreEs?.takeIf { it.isNotBlank() } ?: carta.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAnadir) {
                Icon(Icons.Default.Add, contentDescription = "Añadir al mazo", tint = OroYuGiOh)
            }
        }
    }
}

@Composable
private fun CartaEnMazoItem(
    enMazo: CartaEnMazo,
    onMas: () -> Unit,
    onMenos: () -> Unit,
    onQuitar: () -> Unit
) {
    val carta = enMazo.carta
    val acento = colorPorTipo(carta.type)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ImagenCarta(carta)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    carta.nombreEs?.takeIf { it.isNotBlank() } ?: carta.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(carta.type, style = MaterialTheme.typography.labelSmall, color = acento)
                if (enMazo.faltan > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            "Te faltan ${enMazo.faltan}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            // Stepper de cantidad.
            TextButton(onClick = onMenos) { Text("−", style = MaterialTheme.typography.titleMedium) }
            Text("${enMazo.cantidad}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onMas, enabled = enMazo.cantidad < 3) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onQuitar) {
                Icon(Icons.Default.Delete, contentDescription = "Quitar del mazo", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
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
        }
    }
}

/**
 * Vista de un arquetipo sugerido: TODAS sus cartas en cuadrícula (las que tienes en color y con
 * el nº de copias; las que te faltan, atenuadas). Abajo, los botones para crear el mazo.
 */
@Composable
private fun SugerenciaArquetipoScreen(
    sugerencia: SugerenciaArquetipo,
    viewModel: DeckViewModel,
    onCerrar: () -> Unit
) {
    val cartas by viewModel.arquetipoCartas.collectAsState()

    LaunchedEffect(sugerencia.arquetipo) { viewModel.abrirArquetipo(sugerencia.arquetipo) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = onCerrar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(sugerencia.arquetipo, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Tienes ${sugerencia.poseidas} de ${sugerencia.totalCatalogo} cartas del arquetipo",
                    style = MaterialTheme.typography.labelMedium,
                    color = OroClaro
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (val lista = cartas) {
                null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OroYuGiOh)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lista, key = { "a-${it.carta.id}" }) { ca ->
                        CartaArquetipoCelda(ca)
                    }
                }
            }
        }

        // Botones para crear el mazo (al crearlo se abre automáticamente).
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.crearMazoDesdeArquetipo(sugerencia.arquetipo, soloPoseidas = true) },
                enabled = sugerencia.poseidas > 0,
                modifier = Modifier.weight(1f)
            ) { Text("Con las que tengo") }
            OutlinedButton(
                onClick = { viewModel.crearMazoDesdeArquetipo(sugerencia.arquetipo, soloPoseidas = false) },
                modifier = Modifier.weight(1f)
            ) { Text("Con todas") }
        }
    }
}

/** Celda de la cuadrícula de arquetipo: la carta, atenuada si no la tienes, con badge de copias. */
@Composable
private fun CartaArquetipoCelda(item: CartaArquetipo) {
    val carta = item.carta
    val poseida = item.enColeccion > 0

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            AsyncImage(
                model = carta.imagenes.firstOrNull()?.urlImagenPequena,
                contentDescription = carta.name,
                contentScale = ContentScale.Crop,
                alpha = if (poseida) 1f else 0.35f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
            )
            if (poseida) {
                Surface(
                    color = OroYuGiOh,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        "x${item.enColeccion}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Text(
            carta.nombreEs?.takeIf { it.isNotBlank() } ?: carta.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (poseida) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ImagenCarta(carta: CartaYuGiOh) {
    AsyncImage(
        model = carta.imagenes.firstOrNull()?.urlImagenPequena,
        contentDescription = carta.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .width(46.dp)
            .height(67.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.background)
    )
}
