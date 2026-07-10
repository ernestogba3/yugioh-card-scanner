package com.example.yugiohscanner.data.scan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifica la distancia de Hamming del pHash (parte pura del fallback visual del escáner).
 *
 * `PHash.calcular` necesita un `Bitmap` de Android, así que aquí solo se prueba
 * `distanciaHamming`, que es lógica pura sobre las cadenas hex de 16 caracteres (64 bits) y
 * es justo el criterio con el que el escáner decide si dos cartas "se parecen" (UMBRAL_HAMMING).
 */
class PHashTest {

    @Test
    fun hashes_identicos_distancia_cero() {
        assertEquals(0, PHash.distanciaHamming("a1b2c3d4e5f60789", "a1b2c3d4e5f60789"))
    }

    @Test
    fun un_solo_bit_distinto_distancia_uno() {
        // El último nibble 0 (0000) vs 1 (0001) difiere en un bit.
        assertEquals(1, PHash.distanciaHamming("0000000000000000", "0000000000000001"))
    }

    @Test
    fun todos_los_bits_distintos_distancia_64() {
        // Cada nibble 0x0 (0000) vs 0xf (1111) difiere en sus 4 bits: 16 nibbles * 4 = 64.
        assertEquals(64, PHash.distanciaHamming("0000000000000000", "ffffffffffffffff"))
        assertEquals(64, PHash.distanciaHamming("f0f0f0f0f0f0f0f0", "0f0f0f0f0f0f0f0f"))
    }

    @Test
    fun es_simetrica() {
        val a = "a1b2c3d4e5f60789"
        val b = "0f1e2d3c4b5a6978"
        assertEquals(PHash.distanciaHamming(a, b), PHash.distanciaHamming(b, a))
    }

    @Test
    fun cuenta_bits_por_nibble() {
        // 0xf (1111) vs 0x0 (0000) en un nibble = 4; el resto igual → 4.
        assertEquals(4, PHash.distanciaHamming("f000000000000000", "0000000000000000"))
        // 0xa (1010) vs 0x5 (0101) = 4 bits distintos en un nibble.
        assertEquals(4, PHash.distanciaHamming("a000000000000000", "5000000000000000"))
    }

    @Test
    fun longitudes_distintas_devuelve_maximo() {
        // Cadenas de distinto largo no son comparables → se descartan con MAX_VALUE.
        assertEquals(Int.MAX_VALUE, PHash.distanciaHamming("abc", "abcd"))
    }
}
