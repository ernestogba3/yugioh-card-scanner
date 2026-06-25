package com.example.yugiohscanner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val MAX_TILT = 16f

// Zonas de la carta (fracción de la altura) sobre la imagen completa de la carta.
private const val NOMBRE_TOP = 0.045f
private const val NOMBRE_BOT = 0.115f
private const val ARTE_TOP = 0.160f
private const val ARTE_BOT = 0.625f

/**
 * Tipo de foil, derivado de la rareza. Cada uno imita el acabado real de esa rareza Yu-Gi-Oh y se
 * dibuja SOLO en la zona donde va de verdad (nombre, arte o toda la carta), con mezcla aditiva
 * para que parezca luz reflejada y no pintura encima.
 */
private enum class Foil {
    NINGUNO, RARE, SUPER, ULTRA, GOLD, SECRET, ULTIMATE, GHOST,
    PRISMATIC, STARLIGHT, COLLECTORS, QUARTER,
    PARALLEL, PLATINUM, MOSAIC, STARFOIL, SHATTERFOIL
}

/** Mapea el nombre de rareza (los de [com.example.yugiohscanner.ui.viewmodel.RAREZAS]) a su foil. */
private fun foilDeRareza(rareza: String?): Foil = when (rareza?.lowercase()?.trim()) {
    null, "", "common" -> Foil.NINGUNO
    "rare" -> Foil.RARE
    "super rare" -> Foil.SUPER
    "ultra rare" -> Foil.ULTRA
    "gold rare" -> Foil.GOLD
    "secret rare" -> Foil.SECRET
    "ultimate rare" -> Foil.ULTIMATE
    "ghost rare" -> Foil.GHOST
    "prismatic secret rare" -> Foil.PRISMATIC
    "starlight rare" -> Foil.STARLIGHT
    "collector's rare" -> Foil.COLLECTORS
    "quarter century secret rare" -> Foil.QUARTER
    "parallel rare" -> Foil.PARALLEL
    "platinum rare" -> Foil.PLATINUM
    "mosaic rare" -> Foil.MOSAIC
    "starfoil rare" -> Foil.STARFOIL
    "shatterfoil rare" -> Foil.SHATTERFOIL
    else -> Foil.SUPER
}

/**
 * Carta del detalle con holográfico interactivo: arrástrala con el dedo y se inclina en 3D
 * (rotationX/Y + cameraDistance). Al soltar vuelve suavemente a su sitio.
 *
 * Sobre la imagen se dibuja el foil de la [rareza] seleccionada, en su zona real y con mezcla
 * aditiva: en reposo apenas se ve y se aviva al inclinar, como un foil de verdad bajo la luz.
 */
@Composable
fun CartaHolografica(
    urlImagen: String?,
    contentDescription: String?,
    acento: Color,
    rareza: String?,
    modifier: Modifier = Modifier,
    anchoCarta: Dp = 210.dp
) {
    val scope = rememberCoroutineScope()

    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }

    val foil = foilDeRareza(rareza)

    Box(
        modifier = modifier
            .width(anchoCarta)
            .aspectRatio(0.686f)
            .graphicsLayer {
                rotationX = rotX.value
                rotationY = rotY.value
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, acento, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch { rotX.animateTo(0f) }
                        scope.launch { rotY.animateTo(0f) }
                    }
                ) { change, drag ->
                    change.consume()
                    // Una sola corrutina por evento (en vez de dos) y snapTo en orden: evita crear
                    // decenas de corrutinas por segundo y posibles carreras sobre el mismo Animatable.
                    scope.launch {
                        rotY.snapTo((rotY.value + drag.x * 0.12f).coerceIn(-MAX_TILT, MAX_TILT))
                        rotX.snapTo((rotX.value - drag.y * 0.12f).coerceIn(-MAX_TILT, MAX_TILT))
                    }
                }
            }
    ) {
        AsyncImage(
            model = urlImagen,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Capa de foil. Common no lleva ninguna. Lee rotX/rotY → se redibuja al inclinar.
        if (foil != Foil.NINGUNO) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                dibujarFoil(foil, rotX.value, rotY.value)
            }
        }
    }
}

// ─── Dibujo del foil ─────────────────────────────────────────────────────────

/** Color del arcoíris a partir de un "ángulo" de tono y una saturación (1=vivo, 0=plata/blanco). */
private fun arco(hue: Float, alpha: Float, sat: Float = 0.85f): Color =
    Color.hsv(((hue % 360f) + 360f) % 360f, sat, 1f).copy(alpha = alpha.coerceIn(0f, 1f))

/** 0 en reposo, 1 con la carta al máximo de inclinación: cuánto "le da la luz" al foil. */
private fun intensidad(rotX: Float, rotY: Float): Float =
    ((abs(rotX) + abs(rotY)) / (2f * MAX_TILT)).coerceIn(0f, 1f)

/** X (en px) del centro del destello, desplazado con la inclinación lateral. `factor` = cuánto barre. */
private fun DrawScope.centroX(rotY: Float, factor: Float): Float =
    size.width * (0.5f + rotY / MAX_TILT * factor)

/** Recorta el dibujo a la zona del arte (para foils que solo van en la ilustración). */
private fun DrawScope.enArte(block: DrawScope.() -> Unit) =
    clipRect(top = size.height * ARTE_TOP, bottom = size.height * ARTE_BOT) { block() }

private fun DrawScope.dibujarFoil(foil: Foil, rotX: Float, rotY: Float) {
    val intens = intensidad(rotX, rotY)
    // El tono del arcoíris se desplaza con la inclinación (el color "viaja" al mover la carta).
    val shift = rotY / MAX_TILT * 150f - rotX / MAX_TILT * 50f
    when (foil) {
        Foil.RARE -> bandaNombre(Color.White, 0.10f + 0.55f * intens, rotY)
        Foil.SUPER -> enArte {
            lineasVerticales(intens, shift, sat = 0.32f)
            glare(Color(0xFFEAF6FF), intens, rotY)
        }
        Foil.ULTRA -> {
            bandaNombre(Color(0xFFFFD86B), 0.18f + 0.55f * intens, rotY)
            enArte { glare(Color(0xFFFFE3A0), intens * 0.9f, rotY) }
        }
        Foil.GOLD -> wash(Color(0xFFFFC83C), 0.12f + 0.38f * intens, rotY)
        Foil.SECRET -> enArte { crossHatch(intens, shift) }
        Foil.ULTIMATE -> relieve(intens, Color(0xFFFFE7B0), Color(0xFF5A3C12), Color(0xFFFFD86B))
        Foil.GHOST -> enArte { fantasma(intens, rotX, rotY) }
        Foil.PRISMATIC -> enArte {
            lineasVerticales(intens, shift, sat = 0.85f)
            lineasHorizontales(intens, shift, sat = 0.85f)
        }
        Foil.STARLIGHT -> {
            lineasVerticales(intens, shift, sat = 0.85f)
            lineasHorizontales(intens, shift, sat = 0.85f)
            destellos(intens)
        }
        Foil.COLLECTORS -> huella(intens, shift)
        Foil.QUARTER -> {
            lineasVerticales(intens, shift, sat = 0.85f)
            lineasHorizontales(intens, shift, sat = 0.85f)
            destellos(intens)
            wash(Color(0xFFFFD86B), 0.06f + 0.16f * intens, rotY)
        }
        Foil.PARALLEL -> lineasVerticales(intens, shift, sat = 0.55f)
        Foil.PLATINUM -> relieve(intens, Color(0xFFF2F4FF), Color(0xFF3A3F52), Color(0xFFDFE4FF))
        Foil.MOSAIC -> mosaico(intens, shift)
        Foil.STARFOIL -> estrellas(intens)
        Foil.SHATTERFOIL -> shatter(intens, shift)
        Foil.NINGUNO -> {}
    }
}

/** Banda brillante horizontal sobre la zona del NOMBRE, que barre al inclinar. Rare/Ultra. */
private fun DrawScope.bandaNombre(color: Color, alpha: Float, rotY: Float) {
    val y0 = size.height * NOMBRE_TOP
    val alto = size.height * (NOMBRE_BOT - NOMBRE_TOP)
    val cx = centroX(rotY, 0.5f)
    val brush = Brush.horizontalGradient(
        colors = listOf(Color.Transparent, color.copy(alpha = alpha.coerceIn(0f, 1f)), Color.Transparent),
        startX = cx - size.width * 0.45f,
        endX = cx + size.width * 0.45f
    )
    drawRect(brush, topLeft = Offset(0f, y0), size = Size(size.width, alto), blendMode = BlendMode.Plus)
}

/** Destello diagonal que recorre la zona actual al inclinar (holo de Super/Ultra). */
private fun DrawScope.glare(color: Color, intens: Float, rotY: Float) {
    val a = (0.10f + 0.52f * intens).coerceIn(0f, 1f)
    val cx = centroX(rotY, 0.6f)
    val brush = Brush.linearGradient(
        colors = listOf(Color.Transparent, color.copy(alpha = a), Color.Transparent),
        start = Offset(cx - size.width * 0.35f, 0f),
        end = Offset(cx + size.width * 0.35f, size.height)
    )
    drawRect(brush, blendMode = BlendMode.Plus)
}

/** Tinte de un color sobre toda la carta (Gold, y tinte dorado del Quarter Century). */
private fun DrawScope.wash(color: Color, alpha: Float, rotY: Float) {
    val a = alpha.coerceIn(0f, 1f)
    val cx = centroX(rotY, 0.5f)
    drawRect(
        Brush.linearGradient(
            colors = listOf(color.copy(alpha = a * 0.5f), color.copy(alpha = a), color.copy(alpha = a * 0.5f)),
            start = Offset(cx - size.width, 0f),
            end = Offset(cx + size.width, size.height)
        ),
        blendMode = BlendMode.Plus
    )
}

private fun DrawScope.lineasVerticales(intens: Float, shift: Float, sat: Float) {
    val a = 0.09f + 0.42f * intens
    val sw = size.width * 0.008f
    val paso = size.width / 26f
    var x = 0f
    while (x <= size.width) {
        drawLine(arco(x / size.width * 240f + shift, a, sat), Offset(x, 0f), Offset(x, size.height), sw, blendMode = BlendMode.Plus)
        x += paso
    }
}

private fun DrawScope.lineasHorizontales(intens: Float, shift: Float, sat: Float) {
    val a = 0.08f + 0.36f * intens
    val sw = size.width * 0.008f
    val paso = size.width / 26f
    var y = 0f
    while (y <= size.height) {
        drawLine(arco(y / size.height * 240f + shift + 90f, a, sat), Offset(0f, y), Offset(size.width, y), sw, blendMode = BlendMode.Plus)
        y += paso
    }
}

/** Cross-hatch diagonal arcoíris (el polarizado característico del Secret Rare). */
private fun DrawScope.crossHatch(intens: Float, shift: Float) {
    val a = 0.10f + 0.46f * intens
    val sw = size.width * 0.010f
    val paso = size.width / 20f
    var off = -size.height
    while (off < size.width) {
        val hue = off / size.width * 240f + shift
        drawLine(arco(hue, a), Offset(off, 0f), Offset(off + size.height, size.height), sw, blendMode = BlendMode.Plus)            // ╲
        drawLine(arco(hue + 130f, a * 0.8f), Offset(off, 0f), Offset(off - size.height, size.height), sw, blendMode = BlendMode.Plus) // ╱
        off += paso
    }
}

/** Relieve grabado en toda la carta (Ultimate dorado / Platinum plateado): líneas claro/oscuro + borde. */
private fun DrawScope.relieve(intens: Float, claro: Color, oscuro: Color, borde: Color) {
    val a = 0.12f + 0.36f * intens
    val sw = size.width * 0.014f
    val paso = size.width / 13f
    var off = -size.height
    while (off < size.width) {
        drawLine(claro.copy(alpha = a), Offset(off, 0f), Offset(off + size.height, size.height), sw, blendMode = BlendMode.Plus)
        val o2 = off + sw * 1.6f
        drawLine(oscuro.copy(alpha = a * 0.8f), Offset(o2, 0f), Offset(o2 + size.height, size.height), sw * 0.6f, blendMode = BlendMode.Overlay)
        off += paso
    }
    drawRect(borde.copy(alpha = (0.10f + 0.25f * intens).coerceIn(0f, 1f)), style = Stroke(size.width * 0.03f), blendMode = BlendMode.Plus)
}

/** Ghost Rare: arte etéreo, casi transparente y 3D. Halo blanco-cian que se mueve con la inclinación. */
private fun DrawScope.fantasma(intens: Float, rotX: Float, rotY: Float) {
    val cx = centroX(rotY, 0.5f)
    val cy = size.height * (0.5f - rotX / MAX_TILT * 0.5f)
    val centro = Offset(cx, cy)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.08f + 0.34f * intens).coerceIn(0f, 1f)),
                Color(0xFF8FE8FF).copy(alpha = 0.14f + 0.18f * intens),
                Color.Transparent
            ),
            center = centro,
            radius = size.width * 0.75f
        ),
        radius = size.width * 0.95f,
        center = centro,
        blendMode = BlendMode.Plus
    )
}

/** Destellos (puntillismo) de Starlight / Quarter Century: motas blancas con halo de color. */
private fun DrawScope.destellos(intens: Float) {
    val a = (0.20f + 0.65f * intens).coerceIn(0f, 1f)
    PUNTOS.forEach { p ->
        val centro = Offset(p.x * size.width, p.y * size.height)
        val r = size.minDimension * (0.007f + 0.009f * ((p.x + p.y) % 1f))
        drawCircle(arco(p.x * 360f, a * 0.4f), r * 2.2f, centro, blendMode = BlendMode.Plus)
        drawCircle(Color.White.copy(alpha = a), r, centro, blendMode = BlendMode.Plus)
    }
}

/** Collector's Rare: textura irregular tipo huella dactilar (anillos descentrados, sin dirección fija). */
private fun DrawScope.huella(intens: Float, shift: Float) {
    val a = 0.10f + 0.38f * intens
    val sw = size.width * 0.009f
    val baseX = size.width * 0.5f
    val baseY = size.height * 0.45f
    var r = size.width * 0.04f
    var i = 0
    while (r < size.maxDimension * 0.95f) {
        val cx = baseX + size.width * 0.04f * sin(i * 0.9f)
        val cy = baseY + size.height * 0.03f * cos(i * 1.3f)
        drawCircle(arco(r / size.width * 260f + shift, a), r, Offset(cx, cy), style = Stroke(sw), blendMode = BlendMode.Plus)
        r += size.width * 0.032f
        i++
    }
}

/** Mosaic Rare: textura fina tipo arena/mosaico brillante por toda la carta. */
private fun DrawScope.mosaico(intens: Float, shift: Float) {
    val a = 0.08f + 0.40f * intens
    val paso = size.width / 28f
    val r = size.width * 0.006f
    var fila = 0
    var y = 0f
    while (y <= size.height) {
        var x = if (fila % 2 == 0) 0f else paso / 2f
        while (x <= size.width) {
            drawCircle(arco((x + y) / size.width * 300f + shift, a, 0.7f), r, Offset(x, y), blendMode = BlendMode.Plus)
            x += paso
        }
        y += paso
        fila++
    }
}

/** Starfoil Rare: pequeñas estrellas/chispas (cruces de 4 puntas) repartidas por la carta. */
private fun DrawScope.estrellas(intens: Float) {
    val a = (0.20f + 0.65f * intens).coerceIn(0f, 1f)
    val sw = size.width * 0.006f
    PUNTOS.forEach { p ->
        val c = Offset(p.x * size.width, p.y * size.height)
        val r = size.minDimension * (0.018f + 0.012f * ((p.x + p.y) % 1f))
        val col = Color.White.copy(alpha = a)
        drawLine(col, Offset(c.x - r, c.y), Offset(c.x + r, c.y), sw, blendMode = BlendMode.Plus)
        drawLine(col, Offset(c.x, c.y - r), Offset(c.x, c.y + r), sw, blendMode = BlendMode.Plus)
        val d = r * 0.6f
        val dim = Color.White.copy(alpha = a * 0.5f)
        drawLine(dim, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), sw * 0.7f, blendMode = BlendMode.Plus)
        drawLine(dim, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), sw * 0.7f, blendMode = BlendMode.Plus)
    }
}

/** Shatterfoil Rare: líneas que irradian como cristal roto desde un punto. */
private fun DrawScope.shatter(intens: Float, shift: Float) {
    val a = 0.10f + 0.44f * intens
    val sw = size.width * 0.008f
    val centro = Offset(size.width * 0.5f, size.height * 0.42f)
    val n = 24
    for (i in 0 until n) {
        val ang = i * (6.2831853f / n) + i * 0.13f
        val largo = size.maxDimension * (0.6f + 0.4f * ((i * 0.37f) % 1f))
        val fin = Offset(centro.x + cos(ang) * largo, centro.y + sin(ang) * largo)
        drawLine(arco(i * 26f + shift, a, 0.6f), centro, fin, sw, blendMode = BlendMode.Plus)
        // Pequeña rama para reforzar el aspecto de cristal astillado.
        val medio = Offset((centro.x + fin.x) / 2f, (centro.y + fin.y) / 2f)
        val rama = Offset(medio.x + cos(ang + 0.5f) * largo * 0.2f, medio.y + sin(ang + 0.5f) * largo * 0.2f)
        drawLine(arco(i * 26f + shift + 40f, a * 0.7f, 0.6f), medio, rama, sw * 0.7f, blendMode = BlendMode.Plus)
    }
}

/** Posiciones fijas (fracción del tamaño) de los destellos/estrellas. */
private val PUNTOS = listOf(
    Offset(0.12f, 0.14f), Offset(0.32f, 0.09f), Offset(0.55f, 0.16f), Offset(0.78f, 0.11f),
    Offset(0.88f, 0.30f), Offset(0.20f, 0.30f), Offset(0.45f, 0.34f), Offset(0.68f, 0.40f),
    Offset(0.10f, 0.48f), Offset(0.34f, 0.52f), Offset(0.58f, 0.55f), Offset(0.82f, 0.50f),
    Offset(0.16f, 0.66f), Offset(0.40f, 0.70f), Offset(0.62f, 0.72f), Offset(0.86f, 0.68f),
    Offset(0.26f, 0.84f), Offset(0.50f, 0.88f), Offset(0.72f, 0.86f), Offset(0.90f, 0.90f)
)
