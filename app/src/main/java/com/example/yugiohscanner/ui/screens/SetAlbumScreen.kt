package com.example.yugiohscanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yugiohscanner.ui.theme.Granate
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.viewmodel.CartaAlbum
import com.example.yugiohscanner.ui.viewmodel.EstadoAlbum

/**
 * Álbum de un set: muestra TODAS sus cartas. Las que el usuario posee salen a color; las que
 * le faltan, en gris (desaturadas y atenuadas). Arriba, el porcentaje de completado.
 *
 * Si se pasa [onAnadirCartas] (modo AÑADIR, usado por "Por set"), aparece una barra inferior para
 * añadir todo el set de golpe o elegir cartas concretas. Si es null (modo SOLO LECTURA, usado por
 * "Mis sets"), el álbum solo se consulta y al tocar una carta se abre su detalle.
 */
@Composable
fun SetAlbumScreen(
    estado: EstadoAlbum,
    onCartaClick: (Int) -> Unit,
    onCerrar: () -> Unit,
    onAnadirCartas: ((Set<Int>) -> Unit)? = null
) {
    // Modo selección y cartas elegidas (solo relevantes en modo añadir).
    var modoSeleccion by remember { mutableStateOf(false) }
    var seleccion by remember { mutableStateOf(setOf<Int>()) }

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
            Text(
                text = (estado as? EstadoAlbum.Exito)?.setName ?: "Álbum del set",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        when (estado) {
            is EstadoAlbum.Cargando, EstadoAlbum.Inactivo -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OroYuGiOh)
                }
            }
            is EstadoAlbum.Exito -> {
                CabeceraProgreso(poseidas = estado.poseidas, total = estado.total)
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(estado.cartas, key = { it.cardId }) { carta ->
                        CartaAlbumItem(
                            carta = carta,
                            seleccionada = carta.cardId in seleccion,
                            modoSeleccion = modoSeleccion,
                            onClick = {
                                if (modoSeleccion) {
                                    seleccion = if (carta.cardId in seleccion) {
                                        seleccion - carta.cardId
                                    } else {
                                        seleccion + carta.cardId
                                    }
                                } else {
                                    onCartaClick(carta.cardId)
                                }
                            }
                        )
                    }
                }

                // Barra de añadir (solo en modo AÑADIR, es decir, si hay callback).
                if (onAnadirCartas != null) {
                    BarraAnadir(
                        total = estado.total,
                        modoSeleccion = modoSeleccion,
                        nSeleccionadas = seleccion.size,
                        onAnadirTodo = { onAnadirCartas(estado.cartas.map { it.cardId }.toSet()) },
                        onToggleSeleccion = {
                            modoSeleccion = !modoSeleccion
                            if (!modoSeleccion) seleccion = emptySet()
                        },
                        onAnadirSeleccionadas = {
                            onAnadirCartas(seleccion)
                            seleccion = emptySet()
                            modoSeleccion = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Barra inferior de "Añadir": botón grande para añadir todo el set y, debajo, un modo para elegir
 * cartas concretas. Se apoya solo en el callback [onAnadirTodo]/[onAnadirSeleccionadas] del VM.
 */
@Composable
private fun BarraAnadir(
    total: Int,
    modoSeleccion: Boolean,
    nSeleccionadas: Int,
    onAnadirTodo: () -> Unit,
    onToggleSeleccion: () -> Unit,
    onAnadirSeleccionadas: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Botón estrella: añade el set completo (una copia de cada carta).
        Button(
            onClick = onAnadirTodo,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Granate,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Añadir todo el set ($total)", fontWeight = FontWeight.SemiBold)
        }

        // Modo "elegir cartas": alterna la selección o, si ya hay elegidas, las añade.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onToggleSeleccion,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (modoSeleccion) "Cancelar" else "Elegir cartas")
            }
            if (modoSeleccion) {
                Button(
                    onClick = onAnadirSeleccionadas,
                    enabled = nSeleccionadas > 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OroYuGiOh,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        if (nSeleccionadas > 0) "Añadir elegidas ($nSeleccionadas)" else "Elige cartas",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CabeceraProgreso(poseidas: Int, total: Int) {
    val porcentaje = if (total > 0) (poseidas * 100 / total).coerceIn(0, 100) else 0
    val fraccion = if (total > 0) (poseidas.toFloat() / total).coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "$porcentaje%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = OroClaro,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tienes $poseidas de $total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { fraccion },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = OroYuGiOh,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CartaAlbumItem(
    carta: CartaAlbum,
    seleccionada: Boolean,
    modoSeleccion: Boolean,
    onClick: () -> Unit
) {
    // Las cartas que faltan se ven en gris (desaturadas) y atenuadas.
    val filtroGris = remember0Saturacion()

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            if (seleccionada) 2.dp else 1.dp,
            when {
                seleccionada -> Granate
                carta.poseida -> OroYuGiOh.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outline
            }
        ),
        modifier = Modifier.aspectRatio(0.72f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = carta.urlImagen,
                contentDescription = carta.nombre,
                contentScale = ContentScale.Crop,
                colorFilter = if (carta.poseida) null else filtroGris,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .alpha(if (carta.poseida) 1f else 0.45f)
            )
            // Marca de "la tienes".
            if (carta.poseida) {
                Surface(
                    color = OroYuGiOh,
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        "✓",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            // En modo selección, marca la carta elegida con un check granate en la esquina.
            if (modoSeleccion && seleccionada) {
                Surface(
                    color = Granate,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Elegida",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(3.dp)
                            .size(16.dp)
                    )
                }
            }
        }
    }
}

/** ColorFilter que desatura por completo (gris) para las cartas que faltan. */
@Composable
private fun remember0Saturacion(): ColorFilter =
    androidx.compose.runtime.remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
