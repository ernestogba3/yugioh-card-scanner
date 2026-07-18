package com.example.yugiohscanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yugiohscanner.ui.theme.BordeCuero
import com.example.yugiohscanner.ui.theme.ColorMagico
import com.example.yugiohscanner.ui.theme.CueroMedio
import com.example.yugiohscanner.ui.theme.OroEnvejecido
import com.example.yugiohscanner.ui.theme.Pergamino
import com.example.yugiohscanner.ui.theme.PergaminoTenue
import com.example.yugiohscanner.ui.viewmodel.EventoToast
import kotlinx.coroutines.delay

// Tiempo que el aviso permanece visible antes de cerrarse solo.
private const val MILIS_VISIBLE = 3500L

/**
 * Aviso flotante que aparece tras guardar una carta (éxito o duplicada).
 *
 * Se monta una sola vez en [MainScreen] y observa el [EventoToast] del ColeccionViewModel.
 * Entra deslizando desde abajo, se cierra solo a los pocos segundos y ofrece "Deshacer"
 * mientras está visible (borra la copia recién guardada).
 */
@Composable
fun ToastEscaneo(
    evento: EventoToast?,
    onDeshacer: (Int) -> Unit,
    onDescartar: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Recuerda el último evento no nulo para poder animar la SALIDA con su contenido.
    var ultimo by remember { mutableStateOf<EventoToast?>(null) }
    if (evento != null) ultimo = evento

    // Autocierre: se reinicia cada vez que llega un evento nuevo.
    LaunchedEffect(evento) {
        if (evento != null) {
            delay(MILIS_VISIBLE)
            onDescartar()
        }
    }

    AnimatedVisibility(
        visible = evento != null,
        enter = slideInVertically(animationSpec = tween(300)) { it } + fadeIn(tween(300)),
        exit = slideOutVertically(animationSpec = tween(300)) { it } + fadeOut(tween(300)),
        modifier = modifier
    ) {
        ultimo?.let { ContenidoToast(it, onDeshacer) }
    }
}

@Composable
private fun ContenidoToast(evento: EventoToast, onDeshacer: (Int) -> Unit) {
    // Verde para una carta nueva, oro para una que ya tenías (duplicada).
    val acento = if (evento.esDuplicada) OroEnvejecido else ColorMagico
    val titulo = if (evento.esDuplicada) "Ya tenías esta carta" else "Añadida a tu colección"
    val subtitulo = if (evento.esDuplicada) {
        "${evento.nombre} · ahora ×${evento.copias}"
    } else {
        "${evento.nombre} · ${evento.subtitulo}"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CueroMedio,
        border = BorderStroke(1.5.dp, acento),
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = evento.urlImagen,
                contentDescription = evento.nombre,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(44.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.background)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Punto de color como indicador de la variante (éxito/duplicada).
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(acento)
                    )
                    Text(
                        text = titulo,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Pergamino,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = PergaminoTenue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = { onDeshacer(evento.idLocal) }) {
                Text(
                    text = "Deshacer",
                    color = acento,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
