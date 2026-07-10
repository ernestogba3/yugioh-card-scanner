package com.example.yugiohscanner.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.data.model.Deck
import com.example.yugiohscanner.data.repository.CartaEnMazo
import com.example.yugiohscanner.data.repository.EstadoBanlist
import com.example.yugiohscanner.data.repository.ReglasMazo
import com.example.yugiohscanner.ui.theme.ColorMagico
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.theme.colorPorTipo
import com.example.yugiohscanner.ui.viewmodel.DeckViewModel

/**
 * Detalle de un mazo: cabecera con estado de tamaño (reglas Yu-Gi-Oh), buscador para añadir
 * cartas y lista de las que ya contiene. Se abre desde [MazosScreen].
 */
@Composable
internal fun DeckDetailScreen(deck: Deck, viewModel: DeckViewModel, onCerrar: () -> Unit) {
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

        // Veredicto de legalidad (tamaño + ban list).
        if (stats.total > 0) {
            val (txtLegal, colLegal) = when {
                stats.ilegales > 0 ->
                    "⚠ ${stats.ilegales} carta(s) superan la ban list" to MaterialTheme.colorScheme.error
                stats.legal -> "✓ Mazo legal" to ColorMagico
                else -> "Ajusta el tamaño para que sea legal (Principal 40–60)" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                txtLegal,
                style = MaterialTheme.typography.labelMedium,
                color = colLegal,
                modifier = Modifier.padding(bottom = 8.dp)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    carta.nombreEs?.takeIf { it.isNotBlank() } ?: carta.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val estado = ReglasMazo.estadoBanlist(carta.banTcg)
                if (estado != EstadoBanlist.LIBRE) {
                    Spacer(modifier = Modifier.height(3.dp))
                    BadgeBanlist(estado)
                }
            }
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
                if (enMazo.estadoBanlist != EstadoBanlist.LIBRE || enMazo.excedeLimite) {
                    Spacer(modifier = Modifier.height(3.dp))
                    BadgeBanlist(enMazo.estadoBanlist, enMazo.excedeLimite)
                }
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

/**
 * Pastilla con el estado de la carta en la Forbidden & Limited List. No dibuja nada si la carta
 * es libre (3 copias) y no está excedida. Si [excede] es true (más copias que el límite legal),
 * lo pinta en rojo aunque sea "libre" (no debería pasar, pero avisa por si acaso).
 */
@Composable
private fun BadgeBanlist(estado: EstadoBanlist, excede: Boolean = false) {
    if (estado == EstadoBanlist.LIBRE && !excede) return
    val error = MaterialTheme.colorScheme.error
    val (color, texto) = when {
        excede && estado == EstadoBanlist.LIBRE -> error to "Ilegal"
        estado == EstadoBanlist.PROHIBIDA -> error to "Prohibida"
        estado == EstadoBanlist.LIMITADA -> error to "Limitada · 1"
        estado == EstadoBanlist.SEMILIMITADA -> OroYuGiOh to "Semi · 2"
        else -> error to "Ilegal"
    }
    Surface(
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = if (excede) "⚠ $texto" else texto,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
