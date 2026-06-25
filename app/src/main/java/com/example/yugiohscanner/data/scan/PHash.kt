package com.example.yugiohscanner.data.scan

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.PI

/**
 * pHash (perceptual hash) DCT de 64 bits, idéntico al que calcula el backend en
 * `generate-phash.js`. Sirve para el fallback visual del escáner: si el passcode no se lee,
 * se compara el pHash de la carta escaneada contra los del catálogo ([CardHash]) por distancia
 * de Hamming y se elige el más parecido.
 *
 * IMPORTANTE: el algoritmo (tamaño 32, bloque 8x8, mediana sin el término DC, orden de bits)
 * debe coincidir EXACTAMENTE con el del backend, o los hashes no serán comparables.
 */
object PHash {

    private const val TAM = 32   // la imagen se reduce a 32x32 grises
    private const val BLOQUE = 8 // se quedan las 8x8 frecuencias más bajas

    // Coeficientes del coseno precalculados para la DCT-II 1D de longitud TAM.
    private val COS: Array<DoubleArray> = Array(TAM) { u ->
        DoubleArray(TAM) { x -> cos((2 * x + 1) * u * PI / (2 * TAM)) }
    }

    /** Calcula el pHash (16 caracteres hex = 64 bits) de un bitmap. */
    fun calcular(bitmap: Bitmap): String {
        val grises = aGris32(bitmap)
        val bloque = dct8x8(grises)

        // Mediana de los 64 coeficientes EXCLUYENDO el término DC [0] (brillo medio).
        val sinDC = DoubleArray(BLOQUE * BLOQUE - 1) { bloque[it + 1] }
        sinDC.sort()
        val mediana = (sinDC[31] + sinDC[32]) / 2.0

        val sb = StringBuilder(16)
        for (nibble in 0 until 16) {
            var v = 0
            for (b in 0 until 4) {
                val i = nibble * 4 + b
                val bit = if (i == 0) 0 else if (bloque[i] > mediana) 1 else 0
                v = (v shl 1) or bit
            }
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    /** Distancia de Hamming entre dos pHash hex de 16 caracteres (nº de bits distintos, 0..64). */
    fun distanciaHamming(a: String, b: String): Int {
        if (a.length != b.length) return Int.MAX_VALUE
        var dist = 0
        for (i in a.indices) {
            val xor = Character.digit(a[i], 16) xor Character.digit(b[i], 16)
            dist += Integer.bitCount(xor)
        }
        return dist
    }

    /** Reduce el bitmap a 32x32 y devuelve la luminancia (0..255) de cada píxel. */
    private fun aGris32(bitmap: Bitmap): Array<DoubleArray> {
        val pequeno = Bitmap.createScaledBitmap(bitmap, TAM, TAM, true)
        val pixeles = IntArray(TAM * TAM)
        pequeno.getPixels(pixeles, 0, TAM, 0, 0, TAM, TAM)
        if (pequeno != bitmap) pequeno.recycle()
        return Array(TAM) { y ->
            DoubleArray(TAM) { x ->
                val p = pixeles[y * TAM + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val bl = p and 0xFF
                // Misma luminancia ITU-R BT.601 que usa sharp.grayscale().
                0.299 * r + 0.587 * g + 0.114 * bl
            }
        }
    }

    /** DCT-II 2D separable de una matriz TAM x TAM; devuelve solo el bloque BLOQUE x BLOQUE. */
    private fun dct8x8(pixeles: Array<DoubleArray>): DoubleArray {
        // Paso 1: DCT por filas -> temp[fila][u]
        val temp = Array(TAM) { DoubleArray(BLOQUE) }
        for (y in 0 until TAM) {
            val fila = pixeles[y]
            for (u in 0 until BLOQUE) {
                var s = 0.0
                val cu = COS[u]
                for (x in 0 until TAM) s += fila[x] * cu[x]
                temp[y][u] = s
            }
        }
        // Paso 2: DCT por columnas sobre temp -> bloque[v][u]
        val bloque = DoubleArray(BLOQUE * BLOQUE)
        for (u in 0 until BLOQUE) {
            for (v in 0 until BLOQUE) {
                var s = 0.0
                val cv = COS[v]
                for (y in 0 until TAM) s += temp[y][u] * cv[y]
                bloque[v * BLOQUE + u] = s
            }
        }
        return bloque
    }
}
