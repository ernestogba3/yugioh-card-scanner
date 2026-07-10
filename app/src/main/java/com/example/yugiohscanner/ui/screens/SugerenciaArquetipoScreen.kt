package com.example.yugiohscanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yugiohscanner.data.repository.CartaArquetipo
import com.example.yugiohscanner.data.repository.SugerenciaArquetipo
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.viewmodel.DeckViewModel

/**
 * Vista de un arquetipo sugerido: TODAS sus cartas en cuadrícula (las que tienes en color y con
 * el nº de copias; las que te faltan, atenuadas). Abajo, los botones para crear el mazo.
 * Se abre desde [MazosScreen].
 */
@Composable
internal fun SugerenciaArquetipoScreen(
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
