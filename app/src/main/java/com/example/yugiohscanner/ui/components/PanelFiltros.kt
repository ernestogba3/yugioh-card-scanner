package com.example.yugiohscanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.viewmodel.Filtros
import com.example.yugiohscanner.ui.viewmodel.OpcionesFiltro
import com.example.yugiohscanner.ui.viewmodel.RANGOS_ATK_DEF

/** Panel de filtros de búsqueda (tipo, nivel, atributo, raza, arquetipo, rareza, ATK/DEF). */
@Composable
internal fun PanelFiltros(
    filtros: Filtros,
    opciones: OpcionesFiltro,
    onFiltrosActualizados: (Filtros) -> Unit,
    onBuscar: () -> Unit,
    onLimpiar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "FILTROS",
                style = MaterialTheme.typography.labelLarge,
                color = OroYuGiOh,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Tipo + Nivel en una fila (dos columnas) para que el panel no quede tan largo.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownFiltro(
                    label = "Tipo",
                    opciones = opciones.tipos,
                    seleccion = filtros.tipo,
                    onSeleccion = { onFiltrosActualizados(filtros.copy(tipo = it)) },
                    modifier = Modifier.weight(1f)
                )
                DropdownFiltro(
                    label = "Nivel",
                    opciones = opciones.niveles,
                    seleccion = filtros.nivel,
                    onSeleccion = { onFiltrosActualizados(filtros.copy(nivel = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Atributo + Raza.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownFiltro(
                    label = "Atributo",
                    opciones = opciones.elementos,
                    seleccion = filtros.elemento,
                    onSeleccion = { onFiltrosActualizados(filtros.copy(elemento = it)) },
                    modifier = Modifier.weight(1f)
                )
                DropdownFiltro(
                    label = "Raza",
                    opciones = opciones.razas,
                    seleccion = filtros.raza,
                    onSeleccion = { onFiltrosActualizados(filtros.copy(raza = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = filtros.arquetipo,
                onValueChange = { onFiltrosActualizados(filtros.copy(arquetipo = it)) },
                label = { Text("Arquetipo (p. ej. Blue-Eyes)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Rareza (cruza con las impresiones de la carta).
            DropdownFiltro(
                label = "Rareza",
                opciones = opciones.rarezas,
                seleccion = filtros.rareza,
                onSeleccion = { onFiltrosActualizados(filtros.copy(rareza = it)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ATK + DEF.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownFiltro(
                    label = "ATK mín.",
                    opciones = RANGOS_ATK_DEF,
                    seleccion = filtros.rangoAtk,
                    onSeleccion = { onFiltrosActualizados(filtros.copy(rangoAtk = it)) },
                    modifier = Modifier.weight(1f)
                )
                DropdownFiltro(
                    label = "DEF mín.",
                    opciones = RANGOS_ATK_DEF,
                    seleccion = filtros.rangoDef,
                    onSeleccion = { onFiltrosActualizados(filtros.copy(rangoDef = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onBuscar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buscar con estos filtros")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onLimpiar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Limpiar filtros")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownFiltro(
    label: String,
    opciones: List<String>,
    seleccion: String,
    onSeleccion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandido by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { expandido = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = if (seleccion.isBlank()) "Todos" else seleccion,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false }
        ) {
            DropdownMenuItem(
                text = { Text("Todos") },
                onClick = {
                    onSeleccion("")
                    expandido = false
                }
            )
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion) },
                    onClick = {
                        onSeleccion(opcion)
                        expandido = false
                    }
                )
            }
        }
    }
}
