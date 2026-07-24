package com.example.yugiohscanner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yugiohscanner.data.catalog.CardArt
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.ui.components.CartaItem
import com.example.yugiohscanner.ui.components.PanelFiltros
import com.example.yugiohscanner.ui.theme.IconoCamara
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.viewmodel.ColeccionViewModel
import com.example.yugiohscanner.ui.viewmodel.EstadoBusqueda
import com.example.yugiohscanner.ui.viewmodel.ScannerViewModel

@Composable
fun ScannerScreen(
    scannerViewModel: ScannerViewModel = viewModel(),
    coleccionViewModel: ColeccionViewModel = viewModel(),
    // Si true, abre la cámara nada más entrar (método "Escanear" de la pantalla de entrada).
    abrirCamaraAlEntrar: Boolean = false,
    // Si se pasa, muestra una fila para volver a la pantalla de entrada de "Añadir".
    onVolver: (() -> Unit)? = null
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

    // Método "Escanear": abrir la cámara al entrar (pidiendo permiso si hace falta). Solo una vez.
    LaunchedEffect(Unit) {
        if (abrirCamaraAlEntrar) {
            val permiso = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            if (permiso == PackageManager.PERMISSION_GRANTED) {
                mostrarCamara = true
            } else {
                permisoCamara.launch(Manifest.permission.CAMERA)
            }
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
        if (onVolver != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadir cartas", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

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
private fun MensajeCentrado(texto: String, color: Color) {
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
