package com.example.yugiohscanner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Una partícula del destello de invocación (dirección y tamaño aleatorios). */
private data class Particula(val angulo: Float, val distancia: Float, val radio: Float)

private const val MAX_TILT = 16f

/**
 * "Arena de invocación" (Fase 6): la carta del detalle, pero viva.
 *
 *  - **Holográfico interactivo**: arrastra el dedo sobre la carta y se inclina en 3D
 *    (rotationX/Y + cameraDistance) mientras un brillo iridiscente se desplaza con el gesto.
 *    Al soltar, vuelve suavemente a su sitio.
 *  - **Botón Invocar**: la carta se eleva con un rebote (spring), aparece un círculo mágico
 *    girando, un destello, una onda de choque y partículas que salen disparadas.
 *
 * Autocontenido: se le pasa la imagen (que puede ser el arte elegido) y un color de acento
 * (normalmente el color por tipo de la carta) para teñir los efectos.
 */
@Composable
fun CartaInvocable(
    urlImagen: String?,
    contentDescription: String?,
    acento: Color,
    modifier: Modifier = Modifier,
    anchoCarta: Dp = 170.dp
) {
    val alto = anchoCarta / 0.686f
    val scope = rememberCoroutineScope()

    // Inclinación holográfica (grados) controlada por el dedo.
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }

    // Animación de invocación: rebote del levantamiento (0..1) y "estallido" de efectos (0..1).
    val lift = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    var particulas by remember { mutableStateOf<List<Particula>>(emptyList()) }

    // Giro continuo del círculo mágico.
    val giro by rememberInfiniteTransition(label = "giro").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "anguloGiro"
    )

    fun invocar() {
        scope.launch {
            particulas = List(26) {
                Particula(
                    angulo = Random.nextFloat() * 360f,
                    distancia = 0.6f + Random.nextFloat() * 0.9f,
                    radio = 2f + Random.nextFloat() * 4f
                )
            }
            lift.snapTo(0f)
            burst.snapTo(0f)
            // El levantamiento rebota; el estallido es un barrido lineal de ~0.9s.
            launch { lift.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow)) }
            burst.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .width(anchoCarta * 1.7f)
                .height(alto + 56.dp),
            contentAlignment = Alignment.Center
        ) {
            val b = burst.value

            // Efectos detrás/alrededor de la carta: círculo mágico, onda, partículas, destello.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centro = Offset(size.width / 2f, size.height / 2f)
                val radioBase = size.minDimension * 0.46f

                val visCirculo = sin(b.coerceIn(0f, 1f) * Math.PI).toFloat()
                if (visCirculo > 0.01f) {
                    rotate(degrees = giro, pivot = centro) {
                        dibujarCirculoMagico(centro, radioBase, acento.copy(alpha = 0.85f * visCirculo))
                    }
                }

                // Onda de choque: anillo que se expande y se desvanece.
                if (b in 0.001f..0.999f) {
                    drawCircle(
                        color = acento.copy(alpha = (1f - b) * 0.6f),
                        radius = radioBase * (0.3f + b * 1.3f),
                        center = centro,
                        style = Stroke(width = (1f - b) * 10f + 1f)
                    )
                    // Partículas saliendo del centro.
                    particulas.forEach { p ->
                        val rad = Math.toRadians(p.angulo.toDouble())
                        val dist = radioBase * p.distancia * b
                        val pos = Offset(
                            centro.x + (cos(rad) * dist).toFloat(),
                            centro.y + (sin(rad) * dist).toFloat()
                        )
                        drawCircle(
                            color = acento.copy(alpha = 1f - b),
                            radius = p.radio * (1f - b * 0.6f),
                            center = pos
                        )
                    }
                }

                // Destello blanco en el primer tercio del estallido.
                val flash = (1f - b * 3f).coerceIn(0f, 1f)
                if (flash > 0.01f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f * flash),
                        radius = radioBase * 1.2f,
                        center = centro
                    )
                }
            }

            // La carta: se eleva con el rebote y se inclina con el dedo (holográfico).
            val escala = lerp(0.86f, 1f, lift.value.coerceIn(0f, 1.2f))
            Box(
                modifier = Modifier
                    .width(anchoCarta)
                    .aspectRatio(0.686f)
                    .graphicsLayer {
                        rotationX = rotX.value
                        rotationY = rotY.value
                        scaleX = escala
                        scaleY = escala
                        translationY = -lift.value * 34f * density
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
                            scope.launch { rotY.snapTo((rotY.value + drag.x * 0.12f).coerceIn(-MAX_TILT, MAX_TILT)) }
                            scope.launch { rotX.snapTo((rotX.value - drag.y * 0.12f).coerceIn(-MAX_TILT, MAX_TILT)) }
                        }
                    }
            ) {
                AsyncImage(
                    model = urlImagen,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                // Brillo iridiscente que se desplaza con la inclinación (efecto holográfico).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brilloHolografico(rotX.value, rotY.value))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = acento.copy(alpha = 0.18f),
            contentColor = acento,
            shape = RoundedCornerShape(50),
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { invocar() }
        ) {
            Text(
                "⚡ Invocar",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }
    }
}

/** Brush iridiscente cuya posición se desplaza con la inclinación (más visible cuanto más se inclina). */
private fun brilloHolografico(rotX: Float, rotY: Float): Brush {
    val intensidad = ((abs(rotX) + abs(rotY)) / (2f * MAX_TILT)).coerceIn(0f, 1f) * 0.5f
    val desplaz = rotY / MAX_TILT * 300f // -300..300 px
    return Brush.linearGradient(
        colors = listOf(
            Color(0x00FFFFFF),
            Color(0xFF00E5FF).copy(alpha = intensidad),
            Color(0xFFFF00E5).copy(alpha = intensidad),
            Color(0xFFFFE500).copy(alpha = intensidad),
            Color(0x00FFFFFF)
        ),
        start = Offset(desplaz, 0f),
        end = Offset(desplaz + 360f, 620f)
    )
}

/** Dibuja un círculo de invocación con runas: dos aros, marcas radiales y un rombo interior. */
private fun DrawScope.dibujarCirculoMagico(centro: Offset, radio: Float, color: Color) {
    val grosor = Stroke(width = 3f)
    drawCircle(color, radius = radio, center = centro, style = grosor)
    drawCircle(color, radius = radio * 0.78f, center = centro, style = Stroke(width = 1.5f))

    // 12 marcas radiales entre los dos aros.
    for (i in 0 until 12) {
        val ang = Math.toRadians((i * 30).toDouble())
        val p1 = Offset(centro.x + (cos(ang) * radio * 0.78f).toFloat(), centro.y + (sin(ang) * radio * 0.78f).toFloat())
        val p2 = Offset(centro.x + (cos(ang) * radio).toFloat(), centro.y + (sin(ang) * radio).toFloat())
        drawLine(color, p1, p2, strokeWidth = 2f)
    }

    // Rombo interior (estrella de invocación simplificada).
    val r = radio * 0.78f
    val arriba = Offset(centro.x, centro.y - r)
    val derecha = Offset(centro.x + r, centro.y)
    val abajo = Offset(centro.x, centro.y + r)
    val izquierda = Offset(centro.x - r, centro.y)
    drawLine(color, arriba, derecha, strokeWidth = 2f)
    drawLine(color, derecha, abajo, strokeWidth = 2f)
    drawLine(color, abajo, izquierda, strokeWidth = 2f)
    drawLine(color, izquierda, arriba, strokeWidth = 2f)
}
