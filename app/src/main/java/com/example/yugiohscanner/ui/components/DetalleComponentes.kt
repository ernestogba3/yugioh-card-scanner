package com.example.yugiohscanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh

/** Desplegable opcional (condición/rareza) con opción "Sin especificar". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectorOpcional(
    label: String,
    opciones: List<String>,
    seleccion: String?,
    onSeleccion: (String?) -> Unit
) {
    var expandido by remember { mutableStateOf(false) }
    val sin = "Sin especificar"

    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { expandido = it }
    ) {
        OutlinedTextField(
            value = seleccion ?: sin,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            DropdownMenuItem(
                text = { Text(sin) },
                onClick = { onSeleccion(null); expandido = false }
            )
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion) },
                    onClick = { onSeleccion(opcion); expandido = false }
                )
            }
        }
    }
}

/** Caja de una estadística (ATK/DEF/Nivel) con etiqueta arriba y valor grande dorado. */
@Composable
internal fun CajaStat(label: String, valor: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                valor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OroClaro
            )
        }
    }
}

/** Selector de cantidad (− N +) para añadir varias copias de una vez. */
@Composable
internal fun SelectorCantidad(cantidad: Int, onMenos: () -> Unit, onMas: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonPaso(simbolo = "−", onClick = onMenos)
            Text(
                "$cantidad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(28.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            BotonPaso(simbolo = "+", onClick = onMas)
        }
    }
}

@Composable
private fun BotonPaso(simbolo: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(simbolo, style = MaterialTheme.typography.titleLarge, color = OroYuGiOh)
    }
}

/** Pastilla con el tipo de carta, teñida con su color de acento. */
@Composable
internal fun PillTipo(texto: String, color: Color) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = color,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = texto.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/** Pastilla con la edición/set (dorado translúcido). */
@Composable
internal fun PillEdicion(texto: String) {
    Surface(
        color = OroYuGiOh.copy(alpha = 0.16f),
        contentColor = OroClaro,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OroYuGiOh.copy(alpha = 0.5f))
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/** Tarjeta de sección con título en versalitas doradas y contenido arbitrario. */
@Composable
internal fun TarjetaSeccion(titulo: String, contenido: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                titulo,
                style = MaterialTheme.typography.labelLarge,
                color = OroYuGiOh,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            contenido()
        }
    }
}

/** Fila etiqueta ↔ valor dentro de una [TarjetaSeccion]. */
@Composable
internal fun FilaDato(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            valor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
