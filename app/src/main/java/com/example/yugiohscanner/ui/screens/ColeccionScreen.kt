package com.example.yugiohscanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.yugiohscanner.data.model.CartaGuardada
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.theme.colorPorTipo
import com.example.yugiohscanner.ui.viewmodel.ColeccionViewModel
import com.example.yugiohscanner.ui.viewmodel.EstadoAlbum
import com.example.yugiohscanner.ui.viewmodel.EstadoDetalle

/** Sub-vistas dentro de la pestaña Colección. */
private enum class VistaColeccion { Principal, Estadisticas, Sets }

@Composable
fun ColeccionScreen(viewModel: ColeccionViewModel = viewModel()) {
    val cartas by viewModel.cartas.collectAsState()
    val estadisticas by viewModel.estadisticas.collectAsState()
    val detalle by viewModel.detalle.collectAsState()
    val album by viewModel.album.collectAsState()
    val favoritos by viewModel.favoritos.collectAsState()
    val cajas by viewModel.cajas.collectAsState()

    var vista by remember { mutableStateOf(VistaColeccion.Principal) }

    // 1) El detalle de carta tiene prioridad (se abre desde el grid o desde el álbum).
    when (val d = detalle) {
        is EstadoDetalle.Inactivo -> Unit
        is EstadoDetalle.Cargando -> {
            OverlayDetalle(onCerrar = { viewModel.cerrarDetalle() }) { CircularProgressIndicator() }
            return
        }
        is EstadoDetalle.Exito -> {
            DetalleCartaScreen(
                carta = d.carta,
                copias = cartas.count { it.cardId == d.carta.id },
                onGuardar = { cond, rar, artId, urlArte -> viewModel.guardarCarta(d.carta, cond, rar, artId, urlArte) },
                onCerrar = { viewModel.cerrarDetalle() },
                aviso = if (d.parcial) "Datos guardados (sin conexión). Conéctate para ver la ficha completa." else null,
                esFavorito = d.carta.id in favoritos,
                onToggleFavorito = if (cartas.any { it.cardId == d.carta.id }) {
                    { viewModel.toggleFavorito(d.carta.id) }
                } else null
            )
            return
        }
    }

    // 2) El álbum de un set (todas sus cartas, color/gris).
    if (album !is EstadoAlbum.Inactivo) {
        SetAlbumScreen(
            estado = album,
            onCartaClick = { cardId -> viewModel.abrirDetallePorId(cardId) },
            onCerrar = { viewModel.cerrarAlbum() }
        )
        return
    }

    // 3) Sub-pantallas de estadísticas y sets.
    when (vista) {
        VistaColeccion.Estadisticas -> {
            EstadisticasScreen(stats = estadisticas, onCerrar = { vista = VistaColeccion.Principal })
            return
        }
        VistaColeccion.Sets -> {
            SetsScreen(
                cajas = cajas,
                onAbrirSet = { viewModel.abrirAlbumSet(it) },
                onCerrar = { vista = VistaColeccion.Principal }
            )
            return
        }
        VistaColeccion.Principal -> Unit
    }

    // 4) Vista principal: cabecera + accesos + grid de TODAS tus cartas.
    // Agrupamos las copias de la misma carta; las favoritas primero.
    val grupos = remember(cartas) {
        cartas.groupBy { it.cardId }.values.sortedByDescending { it.first().favorito }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        CabeceraAlbum()
        Spacer(modifier = Modifier.height(8.dp))
        ChipContador("${cartas.size} carta(s) · ${cartas.distinctBy { it.cardId }.size} distinta(s)")
        Spacer(modifier = Modifier.height(16.dp))

        if (cartas.isEmpty()) {
            ColeccionVacia()
        } else {
            // Accesos a las pantallas nuevas.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotonAcceso("📊", "Estadísticas", Modifier.weight(1f)) { vista = VistaColeccion.Estadisticas }
                BotonAcceso("📦", "Mis sets", Modifier.weight(1f)) { vista = VistaColeccion.Sets }
            }
            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(grupos, key = { it.first().cardId }) { copias ->
                    CartaGridItem(
                        carta = copias.first(),
                        cantidad = copias.size,
                        condicion = valorRepresentativo(copias.mapNotNull { it.condicion }),
                        rareza = valorRepresentativo(copias.mapNotNull { it.rareza }),
                        favorito = copias.first().favorito,
                        onClick = { viewModel.abrirDetalle(copias.first()) },
                        onEliminar = { viewModel.eliminarCarta(copias.first()) }
                    )
                }
            }
        }
    }
}

/** Valor representativo de una propiedad entre las copias: el único, "Varias" o null. */
private fun valorRepresentativo(valores: List<String>): String? {
    val distintos = valores.distinct()
    return when {
        distintos.isEmpty() -> null
        distintos.size == 1 -> distintos.first()
        else -> "Varias"
    }
}

@Composable
private fun BotonAcceso(emoji: String, texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                texto,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = OroClaro
            )
        }
    }
}

@Composable
private fun CabeceraAlbum() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = OroYuGiOh,
                modifier = Modifier
                    .padding(6.dp)
                    .size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text("Mi Álbum", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun ChipContador(texto: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = OroClaro,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ColeccionVacia() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(20.dp)
                        .size(48.dp),
                    tint = OroYuGiOh
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tu colección está vacía",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Busca cartas en el escáner y guárdalas aquí",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Celda del grid: imagen de la carta a proporción real (0.72), badge de cantidad, condición,
 * corazón de favorito y botón de borrar. Pulsar abre el detalle.
 */
@Composable
private fun CartaGridItem(
    carta: CartaGuardada,
    cantidad: Int,
    condicion: String?,
    rareza: String?,
    favorito: Boolean,
    onClick: () -> Unit,
    onEliminar: () -> Unit
) {
    val acento = colorPorTipo(carta.tipo)
    val nombre = carta.nombreEs?.takeIf { it.isNotBlank() } ?: carta.nombre

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (favorito) OroYuGiOh else acento.copy(alpha = 0.6f)),
        modifier = Modifier.aspectRatio(0.72f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = carta.urlImagen,
                contentDescription = nombre,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )

            // Nombre + condición (si la hay) sobre un degradado oscuro en la parte inferior.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Text(
                    text = nombre,
                    style = MaterialTheme.typography.labelSmall,
                    color = OroClaro,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (rareza != null) {
                    BadgeMini(rareza, destacado = true)
                }
                if (condicion != null) {
                    BadgeMini(condicion)
                }
            }

            if (cantidad > 1) {
                BadgeCantidad(
                    cantidad = cantidad,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }

            // Corazón de favorito (solo si está marcada).
            if (favorito) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorita",
                    tint = OroYuGiOh,
                    modifier = Modifier
                        .align(if (cantidad > 1) Alignment.TopCenter else Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                )
            }

            // Borrar una copia.
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            ) {
                IconButton(onClick = onEliminar, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar una copia",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** Chip pequeño para mostrar condición/rareza sobre la imagen. [destacado] = acento dorado. */
@Composable
private fun BadgeMini(texto: String, destacado: Boolean = false) {
    Surface(
        color = if (destacado) OroYuGiOh.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f),
        contentColor = if (destacado) Color.Black else OroClaro,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(top = 3.dp)
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun BadgeCantidad(cantidad: Int, modifier: Modifier = Modifier) {
    Surface(
        color = OroYuGiOh,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = "×$cantidad",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Contenedor a pantalla completa para los estados Cargando/Error del detalle. */
@Composable
private fun OverlayDetalle(onCerrar: () -> Unit, contenido: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCerrar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("Detalle de la carta", style = MaterialTheme.typography.titleMedium)
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            contenido()
        }
    }
}
