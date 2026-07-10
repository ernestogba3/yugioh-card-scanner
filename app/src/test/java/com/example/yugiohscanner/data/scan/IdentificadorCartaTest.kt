package com.example.yugiohscanner.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el parseo de texto OCR del escáner (lógica pura, sin cámara ni ML Kit):
 *  - `extraerPasscodes`: los 8 dígitos de la carta → id del catálogo.
 *  - `extraerSetCode`: el código de edición (p. ej. "LOB-EN001").
 *
 * El OCR es ruidoso, así que estos regex tienen que ser tolerantes pero no colar basura.
 */
class IdentificadorCartaTest {

    // --- Passcode (7-8 dígitos) -------------------------------------------------------------

    @Test
    fun extrae_passcode_de_8_digitos() {
        assertEquals(listOf(89631139L), IdentificadorCarta.extraerPasscodes("ATK/3000 89631139"))
    }

    @Test
    fun extrae_passcode_de_7_digitos() {
        assertEquals(listOf(4031928L), IdentificadorCarta.extraerPasscodes("basura 4031928 mas"))
    }

    @Test
    fun ceros_a_la_izquierda_se_normalizan_como_el_catalogo() {
        // "00081439" impreso → 81439L, que es como está guardado el id.
        assertEquals(listOf(81439L), IdentificadorCarta.extraerPasscodes("00081439"))
    }

    @Test
    fun ignora_numeros_demasiado_cortos() {
        // ATK/DEF (4 dígitos) y niveles no deben confundirse con un passcode.
        assertTrue(IdentificadorCarta.extraerPasscodes("ATK 3000 DEF 2500 LV 8").isEmpty())
        assertTrue(IdentificadorCarta.extraerPasscodes("123456").isEmpty())
    }

    @Test
    fun elimina_duplicados_conservando_el_orden() {
        assertEquals(
            listOf(89631139L, 46986414L),
            IdentificadorCarta.extraerPasscodes("89631139 46986414 89631139")
        )
    }

    @Test
    fun texto_sin_numeros_devuelve_lista_vacia() {
        assertTrue(IdentificadorCarta.extraerPasscodes("Blue-Eyes White Dragon").isEmpty())
    }

    // --- Set code (edición) -----------------------------------------------------------------

    @Test
    fun extrae_set_code_clasico() {
        assertEquals("LOB-EN001", IdentificadorCarta.extraerSetCode("ruido LOB-EN001 mas ruido"))
    }

    @Test
    fun set_code_se_normaliza_a_mayusculas() {
        assertEquals("SDK-EN005", IdentificadorCarta.extraerSetCode("sdk-en005"))
    }

    @Test
    fun extrae_set_code_moderno_de_tres_letras() {
        // Ediciones modernas: prefijo largo + idioma de 2 letras (p. ej. "MP23-SP123").
        assertEquals("MP23-SP123", IdentificadorCarta.extraerSetCode("MP23-SP123"))
    }

    @Test
    fun sin_set_code_devuelve_null() {
        assertNull(IdentificadorCarta.extraerSetCode("solo texto y 89631139"))
    }
}
