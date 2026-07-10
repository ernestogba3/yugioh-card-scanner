package com.example.yugiohscanner.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica los algoritmos de similitud de cadenas (lógica pura del ranking fuzzy, Fase 2).
 * `BusquedaFuzzyTest` cubre el comportamiento de extremo a extremo; aquí se fijan las piezas
 * individuales (Levenshtein, Jaro-Winkler y la puntuación combinada) con valores conocidos.
 */
class SimilitudTest {

    // --- Levenshtein ------------------------------------------------------------------------

    @Test
    fun levenshtein_caso_clasico() {
        // "kitten" → "sitting" son 3 ediciones (k→s, e→i, +g). Valor de libro.
        assertEquals(3, Similitud.levenshtein("kitten", "sitting"))
    }

    @Test
    fun levenshtein_con_cadena_vacia_es_la_longitud_de_la_otra() {
        assertEquals(3, Similitud.levenshtein("", "abc"))
        assertEquals(3, Similitud.levenshtein("abc", ""))
        assertEquals(0, Similitud.levenshtein("", ""))
    }

    @Test
    fun levenshtein_identicos_es_cero() {
        assertEquals(0, Similitud.levenshtein("mago oscuro", "mago oscuro"))
    }

    @Test
    fun levenshteinSim_normaliza_a_0_1() {
        assertEquals(1.0, Similitud.levenshteinSim("abc", "abc"), 0.0001)
        assertEquals(1.0, Similitud.levenshteinSim("", ""), 0.0001)
        // 1 edición sobre longitud 3 → 1 - 1/3 ≈ 0.6667.
        assertEquals(1.0 - 1.0 / 3.0, Similitud.levenshteinSim("abc", "abd"), 0.0001)
    }

    // --- Jaro-Winkler -----------------------------------------------------------------------

    @Test
    fun jaroWinkler_identicos_es_uno() {
        assertEquals(1.0, Similitud.jaroWinkler("exodia", "exodia"), 0.0001)
    }

    @Test
    fun jaroWinkler_caso_clasico_martha_marhta() {
        // Valor de referencia conocido del Jaro-Winkler para "martha"/"marhta" ≈ 0.961.
        assertEquals(0.9611, Similitud.jaroWinkler("martha", "marhta"), 0.001)
    }

    @Test
    fun jaroWinkler_premia_prefijo_comun() {
        // Con el mismo nº de erratas, compartir prefijo debe puntuar más.
        val conPrefijo = Similitud.jaroWinkler("dragon", "dragoon")
        val sinPrefijo = Similitud.jaroWinkler("dragon", "aragon")
        assertTrue("prefijo=$conPrefijo sin=$sinPrefijo", conPrefijo > sinPrefijo)
    }

    @Test
    fun jaroWinkler_sin_nada_en_comun_es_cero() {
        assertEquals(0.0, Similitud.jaroWinkler("abc", "xyz"), 0.0001)
    }

    // --- Puntuación combinada ---------------------------------------------------------------

    @Test
    fun puntuar_identicos_es_uno() {
        assertEquals(1.0, Similitud.puntuarNormalizado("mago oscuro", "mago oscuro"), 0.0001)
    }

    @Test
    fun puntuar_con_cadena_vacia_es_cero() {
        assertEquals(0.0, Similitud.puntuarNormalizado("", "mago oscuro"), 0.0001)
        assertEquals(0.0, Similitud.puntuarNormalizado("mago oscuro", ""), 0.0001)
    }

    @Test
    fun puntuar_por_contencion_es_alto() {
        // "exodia" está contenido en "exodia el prohibido" → parecido alto por contención.
        assertTrue(Similitud.puntuarNormalizado("exodia", "exodia el prohibido") >= 0.9)
    }

    @Test
    fun puntuar_siempre_esta_en_rango_0_1() {
        val muestras = listOf(
            "dragon blanco" to "dragon negro",
            "mago" to "maga",
            "exodia" to "slifer el dragon del cielo"
        )
        for ((a, b) in muestras) {
            val s = Similitud.puntuarNormalizado(a, b)
            assertTrue("Fuera de rango: $s para '$a'/'$b'", s in 0.0..1.0)
        }
    }
}
