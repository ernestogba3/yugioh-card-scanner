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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.viewmodel.CartaAlbum
import com.example.yugiohscanner.ui.viewmodel.EstadoAlbum

/**
 * Álbum de un set: muestra TODAS sus cartas. Las que el usuario posee salen a color; las que
 * le faltan, en gris (desaturadas y atenuadas). Arriba, el porcentaje de completado.
 */
@Composable
fun SetAlbumScreen(estado: EstadoAlbum, onCartaClick: (Int) -> Unit, onCerrar: () -> Unit) {
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
                overflow = TextOverflow.Ellipsis
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
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(estado.cartas, key = { it.cardId }) { carta ->
                        CartaAlbumItem(carta = carta, onClick = { onCartaClick(carta.cardId) })
                    }
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
private fun CartaAlbumItem(carta: CartaAlbum, onClick: () -> Unit) {
    // Las cartas que faltan se ven en gris (desaturadas) y atenuadas.
    val filtroGris = remember0Saturacion()

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (carta.poseida) OroYuGiOh.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
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
        }
    }
}

/** ColorFilter que desatura por completo (gris) para las cartas que faltan. */
@Composable
private fun remember0Saturacion(): ColorFilter =
    androidx.compose.runtime.remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
