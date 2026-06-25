package com.example.yugiohscanner.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icono de cámara dibujado a mano (ImageVector) para no depender de material-icons-extended.
 * El color real lo aplica el componente Icon mediante su 'tint'.
 */
val IconoCamara: ImageVector by lazy {
    ImageVector.Builder(
        name = "Camara",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Cuerpo de la cámara + anillo del objetivo.
        addPath(
            pathData = PathParser().parsePathString(
                "M9 2L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
        // Lente interior.
        addPath(
            pathData = PathParser().parsePathString(
                "M12 9c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}
