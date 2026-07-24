package com.example.yugiohscanner.data.repository

import com.example.yugiohscanner.data.catalog.Card
import com.example.yugiohscanner.data.catalog.CardPrint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifica el mapeo carta del catálogo -> [com.example.yugiohscanner.data.model.CartaGuardada] al
 * añadir desde un set (motor de "Añadir todo el set" y de la selección parcial). Lógica pura, sin
 * Room ni Android: comprueba que el set, el código y la rareza salen de la IMPRESIÓN correcta.
 */
class SetGuardadoTest {

    // Helper: carta de catálogo de prueba (solo los campos que importan aquí).
    private fun fakeCard(
        id: Long,
        imageUrl: String = "grande.jpg",
        imageUrlSmall: String? = "pequena.jpg"
    ) = Card(
        id = id,
        nameEs = "Carta $id ES",
        nameEn = "Card $id",
        description = "desc",
        type = "Effect Monster",
        frameType = "effect",
        attribute = "DARK",
        race = "Dragon",
        level = 4,
        atk = 1000,
        def = 1000,
        archetype = null,
        imageUrl = imageUrl,
        imageUrlSmall = imageUrlSmall
    )

    private fun fakePrint(cardId: Long, setName: String, setCode: String, rarity: String?) =
        CardPrint(cardId = cardId, setCode = setCode, setName = setName, rarity = rarity)

    @Test
    fun usa_el_setName_pasado_como_setNombre() {
        val card = fakeCard(1)
        val prints = listOf(fakePrint(1, "Set A", "AAA-001", "Rare"))

        val guardada = SetGuardado.cartaGuardable(card, prints, "Set A")

        assertEquals("Set A", guardada.setNombre)
        assertEquals(1, guardada.cardId)
        assertEquals("Card 1", guardada.nombre)
        assertEquals("Carta 1 ES", guardada.nombreEs)
    }

    @Test
    fun toma_codigo_y_rareza_de_la_impresion_de_ese_set() {
        val card = fakeCard(1)
        // La misma carta está en dos sets con rareza distinta; debe coger la del set pedido.
        val prints = listOf(
            fakePrint(1, "Set A", "AAA-001", "Common"),
            fakePrint(1, "Set B", "BBB-050", "Secret Rare")
        )

        val guardada = SetGuardado.cartaGuardable(card, prints, "Set B")

        assertEquals("BBB-050", guardada.setCodigo)
        assertEquals("Secret Rare", guardada.rareza)
    }

    @Test
    fun sin_impresion_del_set_codigo_vacio_y_rareza_null() {
        val card = fakeCard(1)
        val prints = listOf(fakePrint(1, "Otro Set", "XXX-001", "Rare"))

        val guardada = SetGuardado.cartaGuardable(card, prints, "Set Buscado")

        assertEquals("", guardada.setCodigo)
        assertNull(guardada.rareza)
        // Aun así, se guarda con el nombre del set pedido.
        assertEquals("Set Buscado", guardada.setNombre)
    }

    @Test
    fun usa_la_imagen_pequena_si_existe() {
        val card = fakeCard(1, imageUrl = "grande.jpg", imageUrlSmall = "pequena.jpg")
        val guardada = SetGuardado.cartaGuardable(card, emptyList(), "Set A")
        assertEquals("pequena.jpg", guardada.urlImagen)
    }

    @Test
    fun cae_a_la_imagen_grande_si_no_hay_pequena() {
        val card = fakeCard(1, imageUrl = "grande.jpg", imageUrlSmall = null)
        val guardada = SetGuardado.cartaGuardable(card, emptyList(), "Set A")
        assertEquals("grande.jpg", guardada.urlImagen)
    }
}
