package com.example.yugiohscanner.data.repository

import com.example.yugiohscanner.data.catalog.Card
import com.example.yugiohscanner.data.catalog.CardPrint
import com.example.yugiohscanner.data.model.CartaGuardada

/**
 * Lógica PURA (sin Room ni Android) para convertir una carta del catálogo en una [CartaGuardada]
 * lista para añadir a la colección desde un set concreto. Se extrae aquí para poder testearla en
 * la JVM (igual que [ReglasMazo]); el repositorio solo la alimenta con los datos de la BD.
 */
object SetGuardado {

    /**
     * Construye la [CartaGuardada] de una carta tal como se añadiría desde [setName]:
     * - `setNombre` es el set desde el que se añade (no el "set principal" de la API).
     * - `setCodigo` y `rareza` salen de la IMPRESIÓN de esa carta en ese set (su [CardPrint]).
     * - Si la carta no tuviera impresión registrada en el set, el código queda vacío y la rareza null.
     */
    fun cartaGuardable(card: Card, prints: List<CardPrint>, setName: String): CartaGuardada {
        val print = prints.firstOrNull { it.setName == setName }
        return CartaGuardada(
            cardId = card.id.toInt(),
            nombre = card.nameEn,
            nombreEs = card.nameEs,
            tipo = card.type,
            descripcion = card.description,
            ataque = card.atk,
            defensa = card.def,
            urlImagen = card.imageUrlSmall ?: card.imageUrl,
            setNombre = setName,
            setCodigo = print?.setCode ?: "",
            rareza = print?.rarity
        )
    }
}
