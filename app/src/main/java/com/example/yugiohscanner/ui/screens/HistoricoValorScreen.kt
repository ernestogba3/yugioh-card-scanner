package com.example.yugiohscanner.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yugiohscanner.data.model.ValorSnapshot
import com.example.yugiohscanner.ui.theme.ColorMagico
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh

/**
 * Gráfica de evolución del valor de la colección. Dibuja una foto por día con Canvas (línea +
 * relleno + puntos, el último resaltado). Con menos de 2 fotos aún no hay evolución que mostrar.
 */
@Composable
internal fun HistoricoValorScreen(snapshots: List<ValorSnapshot>, onCerrar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCerrar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("📊 Valor de la colección", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (snapshots.size < 2) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "El histórico se irá construyendo a medida que uses la app y cambie tu colección.\n\nVuelve dentro de unos días para ver la evolución.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
            return
        }

        GraficaValor(
            snapshots = snapshots,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Rango de fechas.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(snapshots.first().fecha, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(snapshots.last().fecha, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(24.dp))

        val valores = snapshots.map { it.valorEur }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EstadoValor("Mínimo", valores.min())
            EstadoValor("Promedio", valores.average())
            EstadoValor("Máximo", valores.max())
        }
    }
}

@Composable
private fun EstadoValor(etiqueta: String, valor: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            formatoEuros(valor),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OroClaro
        )
    }
}

@Composable
private fun GraficaValor(snapshots: List<ValorSnapshot>, modifier: Modifier = Modifier) {
    val valores = snapshots.map { it.valorEur }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Canvas(modifier = modifier) {
        val pad = 12.dp.toPx()
        val gw = size.width - 2 * pad
        val gh = size.height - 2 * pad

        val mn = valores.min()
        val mx = valores.max()
        // Margen del 5% arriba/abajo para que la línea no toque los bordes; si son iguales, centra.
        val rango = (mx - mn).takeIf { it > 0.0 } ?: 1.0
        val margen = rango * 0.05
        val lo = mn - margen
        val alto = (mx + margen) - lo

        val n = valores.size
        val puntos = valores.mapIndexed { i, v ->
            val x = pad + gw * i / (n - 1)
            val y = pad + gh * (1f - ((v - lo) / alto).toFloat())
            Offset(x, y)
        }

        // Líneas de referencia horizontales.
        for (i in 0..3) {
            val y = pad + gh * i / 3
            drawLine(gridColor, Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 1f)
        }

        // Relleno bajo la curva.
        val area = Path().apply {
            moveTo(puntos.first().x, size.height - pad)
            puntos.forEach { lineTo(it.x, it.y) }
            lineTo(puntos.last().x, size.height - pad)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                listOf(ColorMagico.copy(alpha = 0.35f), ColorMagico.copy(alpha = 0f)),
                startY = pad,
                endY = size.height - pad
            )
        )

        // Línea.
        val linea = Path().apply {
            moveTo(puntos.first().x, puntos.first().y)
            puntos.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(linea, color = OroYuGiOh, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))

        // Puntos (el último resaltado).
        puntos.forEachIndexed { i, p ->
            val ultimo = i == puntos.lastIndex
            drawCircle(if (ultimo) OroClaro else ColorMagico, radius = (if (ultimo) 6 else 4).dp.toPx(), center = p)
        }
    }
}
