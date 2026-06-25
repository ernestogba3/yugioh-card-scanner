package com.example.yugiohscanner

import com.example.yugiohscanner.data.search.Similitud
import com.example.yugiohscanner.data.search.TextoUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el ranking fuzzy de la Fase 2: que un nombre mal leído por el OCR puntúa alto
 * contra la carta correcta y bajo contra cartas distintas.
 */
class BusquedaFuzzyTest {

    private fun puntuar(consulta: String, nombre: String): Double =
        Similitud.puntuarNormalizado(TextoUtil.normalizar(consulta), TextoUtil.normalizar(nombre))

    @Test
    fun normalizar_quita_acentos_y_signos() {
        assertEquals("dragon blanco de ojos azules", TextoUtil.normalizar("¡Dragón Blanco de Ojos Azules!"))
    }

    @Test
    fun ocr_con_erratas_puntua_alto_contra_la_carta_correcta() {
        val score = puntuar("Dragon Blanco Ojos Azulez", "Dragón Blanco de Ojos Azules")
        assertTrue("Esperaba parecido alto pero fue $score", score >= 0.85)
    }

    @Test
    fun nombre_distinto_puntua_bajo() {
        val score = puntuar("Dragon Blanco Ojos Azulez", "Mago Oscuro")
        assertTrue("Esperaba parecido bajo pero fue $score", score < 0.62)
    }

    @Test
    fun coincidencia_exacta_es_uno() {
        assertEquals(1.0, puntuar("Mago Oscuro", "Mago Oscuro"), 0.0001)
    }

    @Test
    fun una_palabra_parcial_encuentra_la_carta() {
        // "exodia" debe parecerse mucho a "Exodia el Prohibido" (contención de palabra).
        val score = puntuar("exodia", "Exodia el Prohibido")
        assertTrue("Esperaba parecido alto pero fue $score", score >= 0.7)
    }
}
