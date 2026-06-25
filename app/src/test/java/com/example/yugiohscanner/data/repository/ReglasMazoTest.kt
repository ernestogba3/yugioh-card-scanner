package com.example.yugiohscanner.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica las reglas de tamaño/clasificación de mazo (Fase 4). Lógica pura, sin Android:
 * - `esExtra` decide si una carta va al Deck Extra (Fusión/Sincronía/XYZ/Link) o al Principal.
 * - los topes del juego (40–60 principal, 15 extra, 3 copias) no deben cambiar por accidente.
 *
 * Los `type` de prueba son los strings reales de YGOPRODeck (los que llegan en `CartaYuGiOh.type`).
 */
class ReglasMazoTest {

    // --- esExtra: cartas que SÍ van al Deck Extra -------------------------------------------

    @Test
    fun fusion_synchro_xyz_link_son_extra() {
        assertTrue(ReglasMazo.esExtra("Fusion Monster"))
        assertTrue(ReglasMazo.esExtra("Synchro Monster"))
        assertTrue(ReglasMazo.esExtra("XYZ Monster"))
        assertTrue(ReglasMazo.esExtra("Link Monster"))
    }

    @Test
    fun pendulo_extra_tambien_es_extra() {
        // Un Sincronía/XYZ Péndulo sigue siendo carta de Extra Deck.
        assertTrue(ReglasMazo.esExtra("Synchro Pendulum Effect Monster"))
        assertTrue(ReglasMazo.esExtra("XYZ Pendulum Effect Monster"))
    }

    @Test
    fun es_insensible_a_mayusculas() {
        assertTrue(ReglasMazo.esExtra("fusion monster"))
        assertTrue(ReglasMazo.esExtra("LINK MONSTER"))
    }

    // --- esExtra: cartas que NO van al Extra (van al Principal) -----------------------------

    @Test
    fun monstruos_normales_y_de_efecto_no_son_extra() {
        assertFalse(ReglasMazo.esExtra("Normal Monster"))
        assertFalse(ReglasMazo.esExtra("Effect Monster"))
        assertFalse(ReglasMazo.esExtra("Ritual Monster"))
        assertFalse(ReglasMazo.esExtra("Pendulum Effect Monster"))
    }

    @Test
    fun magicas_y_trampas_no_son_extra() {
        assertFalse(ReglasMazo.esExtra("Spell Card"))
        assertFalse(ReglasMazo.esExtra("Trap Card"))
    }

    @Test
    fun cadena_vacia_no_es_extra() {
        assertFalse(ReglasMazo.esExtra(""))
    }

    // --- Constantes: guardan los límites del juego ------------------------------------------

    @Test
    fun los_topes_del_juego_son_los_correctos() {
        assertTrue(ReglasMazo.PRINCIPAL_MIN == 40)
        assertTrue(ReglasMazo.PRINCIPAL_MAX == 60)
        assertTrue(ReglasMazo.EXTRA_MAX == 15)
        assertTrue(ReglasMazo.MAX_COPIAS == 3)
        // Invariante: el mínimo nunca puede superar al máximo.
        assertTrue(ReglasMazo.PRINCIPAL_MIN < ReglasMazo.PRINCIPAL_MAX)
    }
}
