package com.example.yugiohscanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yugiohscanner.ui.components.CartaInvocable
import com.example.yugiohscanner.data.catalog.CardArt
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.theme.colorPorTipo
import com.example.yugiohscanner.ui.viewmodel.RAREZAS

/**
 * Pantalla de detalle de una carta: imagen grande con borde dorado, nombre, pills de tipo y
 * edición, cajas de stats (ATK/DEF/Nivel), efecto completo, sets y un selector de cantidad
 * para añadir N copias a la colección de una vez.
 */
/** Opciones de estado físico de una carta (de mejor a peor). */
val CONDICIONES = listOf("Nueva", "Casi nueva", "Buen estado", "Jugada", "Dañada")

@Composable
fun DetalleCartaScreen(
    carta: CartaYuGiOh,
    copias: Int,
    onGuardar: (condicion: String?, rareza: String?, chosenArtId: Long?, urlArte: String?) -> Unit,
    onCerrar: () -> Unit,
    aviso: String? = null,
    // Favorito: solo se muestra el corazón si se pasa el callback (p.ej. desde la colección).
    esFavorito: Boolean = false,
    onToggleFavorito: (() -> Unit)? = null,
    // Selector de arte: lista de artes de la carta (vacía = sin selector). El arte sugerido se
    // preselecciona (p.ej. el que reconoció el escáner por pHash).
    artes: List<CardArt> = emptyList(),
    artIdInicial: Long? = null
) {
    val acento = colorPorTipo(carta.type)
    val nombrePrincipal = carta.nombreEs?.takeIf { it.isNotBlank() } ?: carta.name
    val edicion = carta.sets?.firstOrNull()?.nombre

    // Arte elegido por el usuario. Por defecto el sugerido (o ninguno = arte principal).
    var artSeleccionado by remember(carta.id, artIdInicial) { mutableStateOf(artIdInicial) }
    val arteActual = artes.firstOrNull { it.artId == artSeleccionado }
    val urlImagenPrincipal = arteActual?.url ?: carta.imagenes.firstOrNull()?.urlImagen

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Barra superior con botón de volver y, si procede, el corazón de favorito.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCerrar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Detalle de carta",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (onToggleFavorito != null) {
                IconButton(onClick = onToggleFavorito) {
                    Icon(
                        imageVector = if (esFavorito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (esFavorito) "Quitar de favoritos" else "Marcar como favorita",
                        tint = if (esFavorito) OroYuGiOh else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Aviso (p.ej. datos locales sin conexión).
        if (aviso != null) {
            Surface(
                color = OroYuGiOh.copy(alpha = 0.16f),
                contentColor = OroClaro,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    aviso,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // "Arena de invocación": carta con holográfico interactivo + botón Invocar (Fase 6).
        CartaInvocable(
            urlImagen = urlImagenPrincipal,
            contentDescription = nombrePrincipal,
            acento = acento,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Selector de arte: solo si la carta tiene varias ilustraciones. El usuario elige cuál
        // posee; se guarda como chosenArtId y permite copias con artes distintos.
        if (artes.size > 1) {
            Text(
                "ARTES (${artes.size})",
                style = MaterialTheme.typography.labelLarge,
                color = OroYuGiOh,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                artes.forEach { art ->
                    val elegido = art.artId == artSeleccionado
                    AsyncImage(
                        model = art.urlSmall ?: art.url,
                        contentDescription = "Arte ${art.artId}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(64.dp)
                            .aspectRatio(0.686f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (elegido) 3.dp else 1.dp,
                                color = if (elegido) OroYuGiOh else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { artSeleccionado = art.artId }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Nombre en español (principal) e inglés (debajo, atenuado).
        Text(
            nombrePrincipal,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        if (nombrePrincipal != carta.name) {
            Text(
                carta.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Fila de pills: tipo (fondo oscuro) + edición (dorado translúcido), si la hay.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillTipo(texto = carta.type, color = acento)
            if (!edicion.isNullOrBlank() && edicion != "Sin set") {
                PillEdicion(texto = edicion)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Tres cajas de stats: ATK, DEF, Nivel.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CajaStat(label = "ATK", valor = carta.atk?.toString() ?: "—", modifier = Modifier.weight(1f))
            CajaStat(label = "DEF", valor = carta.def?.toString() ?: "—", modifier = Modifier.weight(1f))
            CajaStat(label = "NIVEL", valor = carta.level?.toString() ?: "—", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Precio (offline, del catálogo): CardMarket EUR como principal, TCGPlayer USD secundario.
        if (carta.precioCmEur != null || carta.precioTcgUsd != null) {
            TarjetaSeccion(titulo = "PRECIO") {
                carta.precioCmEur?.let { FilaDato("CardMarket (med.)", "€$it") }
                carta.precioTcgUsd?.let { FilaDato("TCGPlayer (med.)", "$$it") }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Datos secundarios (raza/atributo).
        if (carta.race.isNotBlank() || carta.attribute != null) {
            TarjetaSeccion(titulo = "DATOS") {
                if (carta.race.isNotBlank()) FilaDato("Raza / Tipo", carta.race)
                carta.attribute?.let { FilaDato("Atributo", it) }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Efecto / descripción completa.
        TarjetaSeccion(titulo = "EFECTO") {
            Text(
                text = carta.desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Sets en los que aparece la carta.
        val sets = carta.sets.orEmpty()
        if (sets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            TarjetaSeccion(titulo = "SETS (${sets.size})") {
                sets.forEach { set ->
                    val valor = if (set.precio != null) "${set.codigo} · $${set.precio}" else set.codigo
                    FilaDato(set.nombre, valor)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        if (copias > 0) {
            Text(
                text = "Ya tienes $copias en tu colección",
                style = MaterialTheme.typography.labelMedium,
                color = OroClaro
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Selectores de la copia que posees: condición física y rareza de la impresión.
        var condicion by remember { mutableStateOf<String?>(null) }
        var rareza by remember { mutableStateOf<String?>(null) }
        SelectorOpcional(
            label = "Condición",
            opciones = CONDICIONES,
            seleccion = condicion,
            onSeleccion = { condicion = it }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SelectorOpcional(
            label = "Rareza",
            opciones = RAREZAS,
            seleccion = rareza,
            onSeleccion = { rareza = it }
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Selector de cantidad + botón de añadir.
        var cantidad by remember { mutableIntStateOf(1) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectorCantidad(
                cantidad = cantidad,
                onMenos = { if (cantidad > 1) cantidad-- },
                onMas = { if (cantidad < 9) cantidad++ }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    val urlArte = arteActual?.urlSmall ?: arteActual?.url
                    repeat(cantidad) { onGuardar(condicion, rareza, artSeleccionado, urlArte) }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OroYuGiOh,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (cantidad > 1) "Añadir $cantidad al álbum" else "Añadir al álbum",
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorOpcional(
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
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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

@Composable
private fun CajaStat(label: String, valor: String, modifier: Modifier = Modifier) {
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

@Composable
private fun SelectorCantidad(cantidad: Int, onMenos: () -> Unit, onMas: () -> Unit) {
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

@Composable
private fun PillTipo(texto: String, color: Color) {
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

@Composable
private fun PillEdicion(texto: String) {
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

@Composable
private fun TarjetaSeccion(titulo: String, contenido: @Composable () -> Unit) {
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

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
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
