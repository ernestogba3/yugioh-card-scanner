package com.example.yugiohscanner.data.catalog

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Catálogo de sets (ediciones) con su número total de cartas. Sirve para calcular el
 * porcentaje de colección por set sin depender de ninguna API.
 */
@Entity(tableName = "sets")
data class CardSet(
    @PrimaryKey val setName: String,
    val setCode: String?,
    val numOfCards: Int,
    val tcgDate: String? = null
)
