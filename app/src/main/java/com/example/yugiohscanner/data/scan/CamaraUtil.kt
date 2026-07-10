package com.example.yugiohscanner.data.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Utilidades de imagen y texto del escáner, extraídas de `CameraScreen` para aligerarla y poder
 * probar la parte pura (por ejemplo [construirCandidatos] y [limpiarNombre]) sin cámara ni Compose.
 */

/** Convierte el frame capturado a un Bitmap orientado verticalmente. */
internal fun ImageProxy.aBitmapVertical(): Bitmap {
    val bmp = toBitmap()
    val grados = imageInfo.rotationDegrees
    if (grados == 0) return bmp
    val matriz = Matrix().apply { postRotate(grados.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matriz, true)
}

/**
 * A partir de las líneas que el OCR leyó del recuadro, construye varias hipótesis ("candidatos")
 * del nombre de la carta. La búsqueda local las probará todas y se quedará con la que mejor
 * encaje en el catálogo, de modo que un fallo en una letra ya no tira toda la lectura.
 *
 * Candidatos que genera (en orden de probabilidad):
 *  1. La línea con más letras (lo más habitual para un título de una sola línea).
 *  2. Esa línea unida a la siguiente (títulos largos que ocupan dos líneas).
 *  3. La anterior unida a la principal (por si el OCR partió el título al revés).
 *  4. Las dos líneas con más letras por separado, como respaldo.
 */
internal fun construirCandidatos(lineas: List<String>): List<String> {
    if (lineas.isEmpty()) return emptyList()

    val candidatos = mutableListOf<String>()
    val idx = lineas.indices.maxByOrNull { i -> lineas[i].count { it.isLetter() } }!!
    val principal = lineas[idx]
    candidatos.add(principal)

    val siguiente = lineas.getOrNull(idx + 1)
    if (principal.count { it.isLetter() } >= 8 &&
        siguiente != null && siguiente.count { it.isLetter() } >= 4
    ) {
        candidatos.add("$principal $siguiente")
    }

    val anterior = lineas.getOrNull(idx - 1)
    if (anterior != null && anterior.count { it.isLetter() } >= 4) {
        candidatos.add("$anterior $principal")
    }

    // Respaldo: las dos líneas con más letras, por si la "principal" no era el título.
    lineas.sortedByDescending { linea -> linea.count { it.isLetter() } }
        .take(2)
        .forEach { candidatos.add(it) }

    return candidatos
        .map { limpiarNombre(it) }
        .filter { it.length >= 3 }
        .distinct()
}

/**
 * Preprocesa el recorte antes de pasarlo al OCR. Si el recuadro salió pequeño (carta lejos),
 * lo amplía para que ML Kit lea mejor las letras. Si ya es grande, lo deja igual.
 */
internal fun prepararParaOcr(bitmap: Bitmap): Bitmap {
    val altoMinimo = 320
    if (bitmap.height >= altoMinimo || bitmap.height <= 0) return bitmap
    val factor = altoMinimo.toFloat() / bitmap.height
    val nuevoAncho = (bitmap.width * factor).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, nuevoAncho, altoMinimo, true)
}

/**
 * Recorte de RESPALDO para cuando OpenCV no logra detectar la carta. Asume que la carta está
 * más o menos centrada en el marco y recorta la franja donde suele quedar el nombre.
 */
internal fun recortarZonaNombre(bitmap: Bitmap): Bitmap {
    val fx = 0.16f
    val ftop = 0.26f
    val fancho = 0.68f
    val falto = 0.10f
    val x = (bitmap.width * fx).toInt().coerceIn(0, bitmap.width - 1)
    val top = (bitmap.height * ftop).toInt().coerceIn(0, bitmap.height - 1)
    val ancho = (bitmap.width * fancho).toInt().coerceAtMost(bitmap.width - x)
    val alto = (bitmap.height * falto).toInt().coerceAtMost(bitmap.height - top)
    if (ancho <= 0 || alto <= 0) return bitmap
    return Bitmap.createBitmap(bitmap, x, top, ancho, alto)
}

/** Distancia media (0..1) entre las esquinas correspondientes de dos contornos. */
internal fun distanciaMediaEsquinas(a: CuadrilateroNorm, b: CuadrilateroNorm): Float {
    if (a.puntos.size != 4 || b.puntos.size != 4) return 1f
    var suma = 0f
    for (i in 0 until 4) {
        val dx = a.puntos[i].x - b.puntos[i].x
        val dy = a.puntos[i].y - b.puntos[i].y
        suma += sqrt(dx * dx + dy * dy)
    }
    return suma / 4f
}

/** Mezcla dos contornos (media exponencial) para suavizar el temblor entre frames. */
internal fun mezclarEsquinas(prev: CuadrilateroNorm, nuevo: CuadrilateroNorm, alpha: Float): CuadrilateroNorm {
    val pts = ArrayList<PointF>(4)
    for (i in 0 until 4) {
        val x = prev.puntos[i].x + (nuevo.puntos[i].x - prev.puntos[i].x) * alpha
        val y = prev.puntos[i].y + (nuevo.puntos[i].y - prev.puntos[i].y) * alpha
        pts.add(PointF(x, y))
    }
    return CuadrilateroNorm(pts, nuevo.aspecto)
}

/** Reduce el bitmap si su lado mayor supera [ladoMax]px (limita memoria del procesado OpenCV). */
internal fun reducirSiEsEnorme(bitmap: Bitmap, ladoMax: Int): Bitmap {
    val lado = max(bitmap.width, bitmap.height)
    if (lado <= ladoMax) return bitmap
    val factor = ladoMax.toFloat() / lado
    val w = (bitmap.width * factor).toInt().coerceAtLeast(1)
    val h = (bitmap.height * factor).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, w, h, true)
}

/** Quita símbolos que el OCR suele añadir y normaliza los espacios. */
internal fun limpiarNombre(texto: String): String =
    texto.replace(Regex("[^A-Za-z0-9 '\\-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
