package com.example.yugiohscanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.colorPorTipo

/** Tarjeta de resultado de búsqueda: imagen, nombres, tipo, ATK/DEF, descripción y botón guardar. */
@Composable
internal fun CartaItem(carta: CartaYuGiOh, copias: Int, onGuardar: () -> Unit, onClick: () -> Unit) {
    val acento = colorPorTipo(carta.type)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Franja lateral con el color del tipo de carta.
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(acento)
            )
            Column(modifier = Modifier.padding(14.dp)) {
                Row {
                    AsyncImage(
                        model = carta.imagenes.firstOrNull()?.urlImagenPequena,
                        contentDescription = carta.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(82.dp)
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // Nombre en español como principal; el inglés debajo, más pequeño.
                        val nombrePrincipal = carta.nombreEs?.takeIf { it.isNotBlank() } ?: carta.name
                        Text(
                            nombrePrincipal,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (nombrePrincipal != carta.name) {
                            Text(
                                carta.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        EtiquetaTipo(texto = carta.type, color = acento)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = carta.race,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (carta.atk != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ChipDato("ATK ${carta.atk}")
                                ChipDato("DEF ${carta.def ?: "—"}")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = carta.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (copias > 0) {
                    Text(
                        text = "Ya tienes $copias en tu colección",
                        style = MaterialTheme.typography.labelSmall,
                        color = OroClaro
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Button(
                    onClick = onGuardar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (copias > 0) "Guardar otra copia" else "Guardar en colección")
                }
            }
        }
    }
}

/** Pastilla con el tipo de carta, teñida con su color de acento. */
@Composable
private fun EtiquetaTipo(texto: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = texto.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/** Chip pequeño para mostrar un dato (ATK/DEF). */
@Composable
private fun ChipDato(texto: String) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
