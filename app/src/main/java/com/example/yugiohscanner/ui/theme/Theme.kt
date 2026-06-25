package com.example.yugiohscanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YuGiOhColorScheme = darkColorScheme(
    primary            = OroYuGiOh,
    onPrimary          = Color(0xFF1A1400),
    primaryContainer   = Color(0xFF2A2015),
    onPrimaryContainer = OroClaro,
    secondary          = RojoAccento,
    onSecondary        = Color.White,
    background         = AzulOscuro,
    onBackground       = TextoPrincipal,
    surface            = AzulMedio,
    onSurface          = TextoPrincipal,
    surfaceVariant     = AzulCarta,
    onSurfaceVariant   = TextoSecundario,
    outline            = BordeSutil,
    error              = Color(0xFFCF6679),
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
