package com.example.yugiohscanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Esquema oscuro cálido "binder": cuero como fondo, oro envejecido como acento,
// granate como secundario y pergamino para el texto.
private val YuGiOhColorScheme = darkColorScheme(
    primary            = OroEnvejecido,
    onPrimary          = Color(0xFF241A06),
    primaryContainer   = Color(0xFF3A2C18),
    onPrimaryContainer = OroClaroCalido,
    secondary          = Granate,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFF45161D),
    onSecondaryContainer = Color(0xFFF3C6CB),
    background         = CueroFondo,
    onBackground       = Pergamino,
    surface            = CueroMedio,
    onSurface          = Pergamino,
    surfaceVariant     = CueroClaro,
    onSurfaceVariant   = PergaminoTenue,
    outline            = BordeCuero,
    error              = RojoCalido,
    onError            = Color.White
)

@Composable
fun YuGiOhScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YuGiOhColorScheme,
        typography = Typography,
        content = content
    )
}
