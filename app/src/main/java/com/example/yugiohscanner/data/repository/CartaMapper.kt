package com.example.yugiohscanner.data.repository

import com.example.yugiohscanner.data.catalog.Card
import com.example.yugiohscanner.data.catalog.CardPrint
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.data.model.ImagenCarta
import com.example.yugiohscanner.data.model.SetCarta

/**
 * Traduce una entidad del catálogo ([Card] + sus [CardPrint]) al modelo [CartaYuGiOh] que ya
 * usa toda la UI. Centralizado aquí para que lo compartan el catálogo y los mazos.
 */
fun Card.aCartaYuGiOh(prints: List<CardPrint> = emptyList()): CartaYuGiOh = CartaYuGiOh(
    id = id.toInt(),
    name = nameEn,
    nombreEs = nameEs,
    type = type,
    desc = description,
    atk = atk,
    def = def,
    level = level,
    race = race ?: "",
    attribute = attribute,
    imagenes = listOf(
        ImagenCarta(
            id = id.toInt(),
            urlImagen = imageUrl,
            urlImagenPequena = imageUrlSmall ?: imageUrl
        )
    ),
    sets = prints
        .map { SetCarta(nombre = it.setName, codigo = it.setCode, precio = it.price) }
        .ifEmpty { null },
    precioCmEur = priceCm,
    precioTcgUsd = priceTcg
)
