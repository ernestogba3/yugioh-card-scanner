package com.example.yugiohscanner.data.search

import kotlin.math.max
import kotlin.math.min

/**
 * Algoritmos de similitud de cadenas para encontrar la carta más parecida a un nombre mal
 * leído por el OCR. Todas las funciones esperan texto YA normalizado (ver [TextoUtil]).
 *
 * Combina:
 *  - **Jaro-Winkler**: ideal para erratas cortas y prefijos correctos.
 *  - **Levenshtein** (distancia de edición), normalizado a 0..1.
 *  - **Comparación por palabras (tokens)**: tolera palabras de más/menos y reordenadas,
 *    típico del OCR ("dragon blanco ojos azulez" vs "dragon blanco de ojos azules").
 *
 * [puntuarNormalizado] devuelve 0.0 (nada que ver) .. 1.0 (idénticos).
 */
object Similitud {

    /** Puntuación combinada entre una consulta y un nombre, ambos ya normalizados. */
    fun puntuarNormalizado(query: String, nombre: String): Double {
        if (query.isEmpty() || nombre.isEmpty()) return 0.0
        if (query == nombre) return 1.0
        // Coincidencia por contención: el nombre contiene la consulta (o al revés).
        if (nombre.contains(query) || query.contains(nombre)) return 0.92

        val global = max(jaroWinkler(query, nombre), levenshteinSim(query, nombre))
        val porPalabras = puntuarPorPalabras(query, nombre)
        return max(global, porPalabras)
    }

    /** Para cada palabra de la consulta, su mejor parecido con alguna palabra del nombre. */
    private fun puntuarPorPalabras(query: String, nombre: String): Double {
        val palabrasQ = query.split(' ').filter { it.isNotEmpty() }
        val palabrasN = nombre.split(' ').filter { it.isNotEmpty() }
        if (palabrasQ.isEmpty() || palabrasN.isEmpty()) return 0.0
        var suma = 0.0
        for (q in palabrasQ) {
            var mejor = 0.0
            for (n in palabrasN) mejor = max(mejor, jaroWinkler(q, n))
            suma += mejor
        }
        return suma / palabrasQ.size
    }

    // --- Jaro-Winkler ---

    fun jaroWinkler(s1: String, s2: String): Double {
        val j = jaro(s1, s2)
        if (j < 0.7) return j // Winkler solo premia prefijos cuando ya hay buen parecido
        var prefijo = 0
        val maxPrefijo = min(4, min(s1.length, s2.length))
        for (i in 0 until maxPrefijo) {
            if (s1[i] == s2[i]) prefijo++ else break
        }
        return j + prefijo * 0.1 * (1 - j)
    }

    private fun jaro(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0

        val ventana = max(0, max(len1, len2) / 2 - 1)
        val marcado1 = BooleanArray(len1)
        val marcado2 = BooleanArray(len2)

        var coincidencias = 0
        for (i in 0 until len1) {
            val inicio = max(0, i - ventana)
            val fin = min(i + ventana + 1, len2)
            for (k in inicio until fin) {
                if (marcado2[k] || s1[i] != s2[k]) continue
                marcado1[i] = true
                marcado2[k] = true
                coincidencias++
                break
            }
        }
        if (coincidencias == 0) return 0.0

        // Transposiciones: pares coincidentes en distinto orden.
        var transposiciones = 0.0
        var k = 0
        for (i in 0 until len1) {
            if (!marcado1[i]) continue
            while (!marcado2[k]) k++
            if (s1[i] != s2[k]) transposiciones++
            k++
        }
        transposiciones /= 2

        val m = coincidencias.toDouble()
        return ((m / len1) + (m / len2) + ((m - transposiciones) / m)) / 3
    }

    // --- Levenshtein ---

    fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val coste = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + coste)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }

    /** Levenshtein convertido a parecido 0..1 (1 = idénticos). */
    fun levenshteinSim(a: String, b: String): Double {
        val max = max(a.length, b.length)
        if (max == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / max
    }
}
