package com.example.yugiohscanner.data.model

/**
 * Modelo de carta que consume la UI. Ya no se deserializa de ninguna API: lo construye
 * `CartaMapper` a partir del catálogo local (Room). Por eso no lleva anotaciones de Gson.
 */
data class CartaYuGiOh(
    val id: Int,
    val name: String,
    val nombreEs: String? = null,
    val type: String,
    val desc: String,
    val atk: Int?,
    val def: Int?,
    val level: Int?,
    val race: String,
    val attribute: String?,
    val imagenes: List<ImagenCarta>,
    val sets: List<SetCarta>? = null,
    // Precios promedio de la carta (offline, del catálogo). null = sin dato.
    val precioCmEur: String? = null,   // CardMarket EUR
    val precioTcgUsd: String? = null   // TCGPlayer USD
)

data class ImagenCarta(
    val id: Int,
    val urlImagen: String,
    val urlImagenPequena: String
)

/** Set (edición) en el que aparece una carta. */
data class SetCarta(
    val nombre: String,
    val codigo: String,
    val precio: String? = null   // TCGPlayer USD de esta impresión (null = sin dato)
)
