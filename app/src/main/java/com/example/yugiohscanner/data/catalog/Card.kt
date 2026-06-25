package com.example.yugiohscanner.data.catalog

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una fila por carta ÚNICA del catálogo (datos de YGOPRODeck).
 *
 * Pertenece a la base de datos de SOLO LECTURA (`catalog.db`). Se rellena una vez en el
 * primer arranque a partir de `assets/database/catalog.json` (ver [CatalogImporter]).
 * Nunca la escribe el usuario.
 */
@Entity(
    tableName = "cards",
    indices = [Index("nameEn"), Index("nameEs"), Index("archetype")]
)
data class Card(
    @PrimaryKey val id: Long,        // id de YGOPRODeck (estable y único)
    val nameEs: String?,             // puede faltar traducción
    val nameEn: String,
    val description: String,
    val type: String,                // "Effect Monster", "Spell Card"...
    val frameType: String?,          // normal/effect/spell/trap... (color del marco)
    val attribute: String?,          // DARK, LIGHT... (null en mágicas/trampas)
    val race: String?,               // "Dragon", "Spellcaster" / tipo de mágica o trampa
    val level: Int?,                 // nivel / rango / link
    val atk: Int?,
    val def: Int?,
    val archetype: String?,
    val imageUrl: String,            // imagen grande (Coil la descarga si hay red)
    val imageUrlSmall: String?,
    val priceCm: String? = null,     // CardMarket EUR (promedio de la carta)
    val priceTcg: String? = null     // TCGPlayer USD (promedio de la carta)
)
