package com.example.yugiohscanner.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el filtrado de sets del navegador "Por set" ([SetBrowserLogica.filtrar]). Lógica
 * pura (sin Android): con consulta vacía devuelve todos; si no, filtra por nombre o código sin
 * distinguir mayúsculas.
 */
class SetBrowserLogicaTest {

    private val sets = listOf(
        SetCatalogo("Burst of Destiny", "BODE", 100),
        SetCatalogo("Structure Deck: Blue-Eyes White Destiny", "SDBE", 45),
        SetCatalogo("Legend of Blue Eyes White Dragon", "LOB", 126),
        SetCatalogo("Set sin código", null, 10)
    )

    @Test
    fun consulta_vacia_devuelve_todos() {
        assertEquals(sets, SetBrowserLogica.filtrar(sets, ""))
    }

    @Test
    fun consulta_solo_espacios_devuelve_todos() {
        assertEquals(sets, SetBrowserLogica.filtrar(sets, "   "))
    }

    @Test
    fun filtra_por_nombre_sin_distinguir_mayusculas() {
        val res = SetBrowserLogica.filtrar(sets, "blue eyes")
        // "Blue-Eyes" y "Blue Eyes" (guion vs espacio) NO son iguales: solo debe salir la que
        // contiene el texto "blue eyes" literal (Legend of Blue Eyes...).
        assertEquals(1, res.size)
        assertEquals("Legend of Blue Eyes White Dragon", res.first().setName)
    }

    @Test
    fun filtra_por_codigo_sin_distinguir_mayusculas() {
        val res = SetBrowserLogica.filtrar(sets, "sdbe")
        assertEquals(1, res.size)
        assertEquals("Structure Deck: Blue-Eyes White Destiny", res.first().setName)
    }

    @Test
    fun codigo_null_no_rompe_el_filtrado() {
        // Al buscar por un texto que solo está en el nombre del set sin código.
        val res = SetBrowserLogica.filtrar(sets, "sin código")
        assertEquals(1, res.size)
        assertEquals("Set sin código", res.first().setName)
    }

    @Test
    fun sin_coincidencias_lista_vacia() {
        assertTrue(SetBrowserLogica.filtrar(sets, "zzz-inexistente").isEmpty())
    }
}
