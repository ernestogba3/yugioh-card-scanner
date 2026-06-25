package com.example.yugiohscanner.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Degradado de fondo de la app (atmósfera "bóveda del Milenio"). */
val FondoGradiente = Brush.verticalGradient(
    colors = listOf(AzulOscuro, AzulMedio, AzulCarta)
)

/**
 * Color de acento según el tipo de carta (el texto viene en inglés de la API).
 * Se usa para la franja lateral de cada carta, dándole identidad visual de un vistazo.
 */
fun colorPorTipo(tipo: String): Color {
    val t = tipo.lowercase()
    return when {
        "normal" in t   -> ColorNormal
        "fusion" in t   -> ColorFusion
        "ritual" in t   -> ColorRitual
        "synchro" in t  -> ColorSincronia
        "xyz" in t      -> ColorXYZ
        "link" in t     -> ColorLink
        "spell" in t    -> ColorMagico
        "trap" in t     -> ColorTrampa
        "effect" in t   -> ColorEfecto
        else            -> OroYuGiOh
    }
}
