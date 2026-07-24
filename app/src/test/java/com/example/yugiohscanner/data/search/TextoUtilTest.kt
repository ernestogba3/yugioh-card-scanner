package com.example.yugiohscanner.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifica la decodificación de entidades HTML de los nombres del catálogo (YGOPRODeck los
 * devuelve escapados). Lógica pura, sin Android.
 */
class TextoUtilTest {

    @Test
    fun decodifica_apostrofo() {
        assertEquals("Legendary 5D's Decks", TextoUtil.decodificarHtml("Legendary 5D&apos;s Decks"))
    }

    @Test
    fun decodifica_ampersand() {
        assertEquals("Att&ck", TextoUtil.decodificarHtml("Att&amp;ck"))
    }

    @Test
    fun decodifica_comillas_y_signos() {
        assertEquals("\"Hola\"", TextoUtil.decodificarHtml("&quot;Hola&quot;"))
        assertEquals("a < b > c", TextoUtil.decodificarHtml("a &lt; b &gt; c"))
    }

    @Test
    fun decodifica_entidades_numericas() {
        assertEquals("'", TextoUtil.decodificarHtml("&#39;"))   // decimal
        assertEquals("'", TextoUtil.decodificarHtml("&#x27;"))  // hexadecimal
        assertEquals("&", TextoUtil.decodificarHtml("&#38;"))
    }

    @Test
    fun texto_sin_entidades_no_cambia() {
        assertEquals("Blue-Eyes White Dragon", TextoUtil.decodificarHtml("Blue-Eyes White Dragon"))
        assertEquals("", TextoUtil.decodificarHtml(""))
    }

    @Test
    fun ampersand_se_resuelve_al_final_sin_re_decodificar() {
        // "&amp;lt;" debe quedar como "&lt;" literal, no convertirse en "<".
        assertEquals("&lt;", TextoUtil.decodificarHtml("&amp;lt;"))
    }
}
