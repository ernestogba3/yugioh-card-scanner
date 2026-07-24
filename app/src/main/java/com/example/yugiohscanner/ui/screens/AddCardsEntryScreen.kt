package com.example.yugiohscanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yugiohscanner.ui.theme.BordeCuero
import com.example.yugiohscanner.ui.theme.CueroClaro
import com.example.yugiohscanner.ui.theme.CueroMedio
import com.example.yugiohscanner.ui.theme.Granate
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.theme.Pergamino
import com.example.yugiohscanner.ui.theme.PergaminoTenue

/**
 * Pantalla de entrada del flujo "Añadir cartas". Layout: una rejilla de dos botones grandes
 * (Escanear y Por set) y, debajo, ocupando todo el ancho, el botón de búsqueda manual.
 *
 * No tiene ViewModel: es pura navegación (ver [MainScreen]).
 */
@Composable
fun AddCardsEntryScreen(
    onEscanear: () -> Unit,
    onPorSet: () -> Unit,
    onManual: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Añadir cartas", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "¿Qué acabas de conseguir?",
            style = MaterialTheme.typography.bodyMedium,
            color = PergaminoTenue
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Rejilla superior: dos botones grandes (escanear = granate, por set = cuero/oro con sello).
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MetodoCard(
                emoji = "📷",
                titulo = "Escanear",
                comoLabel = "una a una",
                descripcion = "Cartas sueltas o un sobre recién abierto",
                fondo = Granate,
                contentColor = Color.White,
                onClick = onEscanear,
                modifier = Modifier.weight(1f)
            )
            MetodoCard(
                emoji = "📦",
                titulo = "Por set",
                comoLabel = "el set entero",
                descripcion = "Structure decks y productos cerrados",
                fondo = CueroClaro,
                contentColor = Pergamino,
                acento = OroYuGiOh,
                badge = "DE GOLPE",
                onClick = onPorSet,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Botón manual a TODO el ancho, debajo de la rejilla.
        Surface(
            color = CueroMedio,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BordeCuero),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onManual)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔍", fontSize = 26.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Búsqueda manual",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = OroClaro
                    )
                    Text(
                        "¿Sin código o el escáner falla? Búscala por nombre",
                        style = MaterialTheme.typography.bodySmall,
                        color = PergaminoTenue
                    )
                }
                Text("›", fontSize = 22.sp, color = PergaminoTenue)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tira de contexto: educa sobre el flujo Structure Deck.
        Surface(
            color = CueroMedio,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, BordeCuero.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("💡", fontSize = 16.sp)
                Text(
                    text = "¿Compraste un Structure Deck? Entra en \"Por set\", búscalo y pulsa " +
                        "\"Añadir todo el set\" — sus cartas entran de una vez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PergaminoTenue
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Botón grande de método (escanear / por set). [acento] y [badge] son opcionales (los usa
 * "Por set" para el sello dorado "DE GOLPE"). Reserva una franja superior para el sello, así el
 * emoji nunca se solapa y ambas tarjetas quedan alineadas.
 */
@Composable
private fun MetodoCard(
    emoji: String,
    titulo: String,
    comoLabel: String,
    descripcion: String,
    fondo: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    acento: Color? = null,
    badge: String? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(fondo)
            .clickable(onClick = onClick)
            .heightIn(min = 190.dp)
            .padding(vertical = 16.dp, horizontal = 12.dp)
    ) {
        // Franja superior reservada para el sello (misma altura haya o no badge).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            if (badge != null && acento != null) {
                Surface(color = acento, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            Text(text = emoji, fontSize = 40.sp)
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Surface(
                color = (acento ?: contentColor).copy(alpha = 0.18f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = comoLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Text(
                text = descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}
