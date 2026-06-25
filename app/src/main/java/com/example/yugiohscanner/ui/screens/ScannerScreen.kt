package com.example.yugiohscanner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.yugiohscanner.data.catalog.CardArt
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.ui.viewmodel.ColeccionViewModel
import com.example.yugiohscanner.ui.viewmodel.EstadoBusqueda
import com.example.yugiohscanner.ui.viewmodel.Filtros
import com.example.yugiohscanner.ui.viewmodel.OpcionesFiltro
import com.example.yugiohscanner.ui.theme.IconoCamara
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.theme.colorPorTipo
import com.example.yugiohscanner.ui.viewmodel.RANGOS_ATK_DEF
import com.example.yugiohscanner.ui.viewmodel.ScannerViewModel

@Composable
fun ScannerScreen(
    scannerViewModel: ScannerViewModel = viewModel(),
    coleccionViewModel: ColeccionViewModel = viewModel()
) {
    val context = LocalContext.current
    val estado by scannerViewModel.estado.collectAsState()
    val filtros by scannerViewModel.filtros.collectAsState()
    val opciones by scannerViewModel.opciones.collectAsState()
    val coleccion by coleccionViewModel.cartas.collectAsState()
    // Cuántas copias tienes de cada carta (cardId -> nº de copias).
    val copiasPorCarta = remember(coleccion) {
        coleccion.groupingBy { it.cardId }.eachCount()
    }
    var textoBusqueda by remember { mutableStateOf("") }
    var mostrarCamara by remember { mutableStateOf(false) }
    var mostrarFiltros by remember { mutableStateOf(false) }
    // Si está activo, solo se muestran los resultados que ya tienes en tu colección.
    var soloColeccion by remember { mutableStateOf(false) }
    // Carta que se muestra en la pantalla de detalle (null = no hay detalle abierto).
    var cartaDetalle by remember { mutableStateOf<CartaYuGiOh?>(null) }
    // Artes de la carta del detalle (para el selector) y el arte sugerido por el escáner (pHash).
    var artesDetalle by remember { mutableStateOf<List<CardArt>>(emptyList()) }
    var artIdSugerido by remember { mutableStateOf<Long?>(null) }

    // Cuando se abre un detalle, carga sus artes para el selector (se reinician al cerrarse).
    LaunchedEffect(cartaDetalle?.id) {
        val carta = cartaDetalle
        if (carta == null) {
            artesDetalle = emptyList()
        } else {
            scannerViewModel.cargarArtes(carta.id.toLong()) { artesDetalle = it }
        }
    }

    val permisoCamara = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            mostrarCamara = true
        } else {
            Toast.makeText(
                context,
                "Se necesita el permiso de cámara para escanear cartas",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (mostrarCamara) {
        CameraScreen(
            onIdentificar = { frame, callback -> scannerViewModel.identificarFrame(frame, callback) },
            onCartaIdentificada = { carta, artId, _ ->
                // Identificada por passcode/pHash: abrir su detalle con el arte sugerido.
                artIdSugerido = artId
                cartaDetalle = carta
                mostrarCamara = false
            },
            onTextReconocido = { candidatos ->
                // El primer candidato (la lectura más probable) se muestra en la caja de búsqueda.
                textoBusqueda = candidatos.firstOrNull() ?: ""
                scannerViewModel.buscarDesdeOcr(candidatos)
                mostrarCamara = false
            },
            onCerrar = { mostrarCamara = false }
        )
        return
    }

    cartaDetalle?.let { carta ->
        DetalleCartaScreen(
            carta = carta,
            copias = copiasPorCarta[carta.id] ?: 0,
            onGuardar = { cond, rar, artId, urlArte ->
                coleccionViewModel.guardarCarta(carta, cond, rar, artId, urlArte)
            },
            onCerrar = { cartaDetalle = null },
            artes = artesDetalle,
            artIdInicial = artIdSugerido
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Buscar Carta", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            OutlinedTextField(
                value = textoBusqueda,
                onValueChange = { textoBusqueda = it },
                label = { Text("Nombre de la carta") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                leadingIcon = {
                    IconButton(onClick = {
                        val consulta = textoBusqueda.trim()
                        if (consulta.isNotEmpty()) scannerViewModel.buscarCarta(consulta)
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                },
                trailingIcon = {
                    IconButton(onClick = {
                        val permiso = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (permiso == PackageManager.PERMISSION_GRANTED) {
                            mostrarCamara = true
                        } else {
                            permisoCamara.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Icon(IconoCamara, contentDescription = "Escanear con cámara", tint = OroYuGiOh)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val consulta = textoBusqueda.trim()
                    if (consulta.isNotEmpty()) scannerViewModel.buscarCarta(consulta)
                }),
                supportingText = {
                    Text("Toca la 📷 para escanear el nombre. La búsqueda tolera erratas del OCR. También puedes escribir el nombre o usar los filtros.")
                }
            )
        }

        item {
            TextButton(
                onClick = { mostrarFiltros = !mostrarFiltros },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (mostrarFiltros) "▼ Ocultar filtros" else "▶ Mostrar filtros")
            }
        }

        if (mostrarFiltros) {
            item {
                PanelFiltros(
                    filtros = filtros,
                    opciones = opciones,
                    onFiltrosActualizados = { nuevosFiltros ->
                        scannerViewModel.actualizarFiltro(nuevosFiltros)
                    },
                    onBuscar = { scannerViewModel.buscarPorFiltros(textoBusqueda) },
                    onLimpiar = { scannerViewModel.limpiarFiltros() }
                )
            }
        }

        when (val e = estado) {
            is EstadoBusqueda.Inactivo -> item {
                MensajeCentrado(
                    texto = "Escribe el nombre de una carta o usa la cámara",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is EstadoBusqueda.Cargando -> item {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is EstadoBusqueda.Error -> item {
                MensajeCentrado(
                    texto = e.mensaje,
                    color = MaterialTheme.colorScheme.error
                )
            }
            is EstadoBusqueda.Exito -> {
                val visibles = if (soloColeccion) {
                    e.cartas.filter { (copiasPorCarta[it.id] ?: 0) > 0 }
                } else {
                    e.cartas
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${visibles.size} resultado(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilterChip(
                            selected = soloColeccion,
                            onClick = { soloColeccion = !soloColeccion },
                            label = { Text("Solo en mi colección") }
                        )
                    }
                }
                if (visibles.isEmpty()) {
                    item {
                        MensajeCentrado(
                            texto = "No tienes ninguna de estas cartas en tu colección",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(visibles, key = { it.id }) { carta ->
                    CartaItem(
                        carta = carta,
                        copias = copiasPorCarta[carta.id] ?: 0,
                        onGuardar = { coleccionViewModel.guardarCarta(carta) },
                        onClick = { artIdSugerido = null; cartaDetalle = carta }
                    )
                }
            }
        }
    }
}

@Composable
private fun MensajeCentrado(texto: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
private fun PanelFiltros(
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
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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

@Composable
private fun CartaItem(carta: CartaYuGiOh, copias: Int, onGuardar: () -> Unit, onClick: () -> Unit) {
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
