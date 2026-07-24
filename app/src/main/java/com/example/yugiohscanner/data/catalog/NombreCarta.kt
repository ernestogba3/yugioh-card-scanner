package com.example.yugiohscanner.data.catalog

/**
 * Fila ligera del índice de nombres (proyección de [Card]): solo lo necesario para el
 * ranking fuzzy. Evita cargar las descripciones y el resto de campos de las 14k cartas.
 */
data class NombreCarta(
    val id: Long,
    val nameEs: String?,
    val nameEn: String
)

/** Proyección id -> arquetipo, para las estadísticas de colección. */
data class ArquetipoCarta(
    val id: Long,
    val archetype: String?
)

/** Nº de cartas distintas que tiene cada arquetipo en el catálogo (para sugerir mazos). */
data class ConteoArquetipo(
    val archetype: String,
    val total: Int
)

/**
 * Un set que coincide con una búsqueda por nombre de carta, junto con UNA carta representativa
 * que provocó la coincidencia (en español e inglés). Permite mostrar en el navegador "Por set"
 * por qué apareció ese set aunque su nombre solo exista en inglés.
 */
data class SetCartaCoincidencia(
    val setName: String,
    val nameEs: String?,
    val nameEn: String
)

/** Proyección (cardId, setName) de `card_prints`: en qué sets aparece cada carta. */
data class CartaEnSet(
    val cardId: Long,
    val setName: String
)
