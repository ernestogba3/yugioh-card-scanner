package com.example.yugiohscanner.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica la parte pura del post-procesado del OCR del escáner (extraída de `CameraScreen`):
 *  - `limpiarNombre`: quita símbolos que mete el OCR y normaliza espacios.
 *  - `construirCandidatos`: genera varias hipótesis del nombre a partir de las líneas leídas.
 */
class CamaraUtilTest {

    // --- limpiarNombre ----------------------------------------------------------------------

    @Test
    fun limpia_simbolos_del_ocr_conservando_guion_y_apostrofe() {
        assertEquals("Blue-Eyes White Dragon", limpiarNombre("Blue-Eyes White Dragon!!!"))
        assertEquals("Harpie's Feather Duster", limpiarNombre("Harpie's Feather Duster*"))
    }

    @Test
    fun colapsa_espacios_multiples_y_recorta() {
        assertEquals("Mago Oscuro", limpiarNombre("   Mago    Oscuro   "))
    }

    // --- construirCandidatos ----------------------------------------------------------------

    @Test
    fun lista_vacia_no_genera_candidatos() {
        assertTrue(construirCandidatos(emptyList()).isEmpty())
    }

    @Test
    fun una_sola_linea_es_el_candidato_principal() {
        assertEquals(listOf("Blue-Eyes White Dragon"), construirCandidatos(listOf("Blue-Eyes White Dragon")))
    }

    @Test
    fun elige_la_linea_con_mas_letras_como_principal() {
        // "ELF" es ruido corto; el título real es la línea larga → debe ir primero.
        val candidatos = construirCandidatos(listOf("ELF", "Celtic Guardian Warrior"))
        assertEquals("Celtic Guardian Warrior", candidatos.first())
    }

    @Test
    fun une_titulo_partido_en_dos_lineas() {
        // Título largo + continuación (≥4 letras) → una hipótesis combinada.
        val candidatos = construirCandidatos(listOf("Elemental HERO", "Flame Wingman"))
        assertTrue("Esperaba la unión entre candidatos: $candidatos",
            candidatos.any { it.equals("Elemental HERO Flame Wingman", ignoreCase = true) })
    }

    @Test
    fun descarta_candidatos_demasiado_cortos() {
        // "OK" (2 letras) queda por debajo del mínimo de 3 caracteres.
        val candidatos = construirCandidatos(listOf("OK", "Dark Magician"))
        assertTrue(candidatos.none { it.length < 3 })
    }
}
