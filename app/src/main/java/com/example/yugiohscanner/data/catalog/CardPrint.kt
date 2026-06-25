package com.example.yugiohscanner.data.catalog

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una fila por IMPRESIÓN de una carta: la misma carta aparece en muchos sets con distinta
 * rareza (LOB-001 Ultra Rare, SDK-E001 Ultra Rare, LDS2 Secret Rare...).
 *
 * `cardId` es una clave LÓGICA hacia [Card.id]: no es una foreign key de Room porque, en el
 * diseño final, la colección del usuario vive en otra base de datos y Room no valida FKs
 * entre bases distintas. La integridad la garantiza el importador.
 */
@Entity(
    tableName = "card_prints",
    indices = [Index("cardId"), Index("setCode")]
)
data class CardPrint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,         // -> Card.id (clave lógica)
    val setCode: String,      // "LOB-001", "SDK-E001"
    val setName: String,      // "Legend of Blue Eyes White Dragon"
    val rarity: String?,      // "Ultra Rare", "Secret Rare"
    val edition: String? = null,  // "1st Edition" / "Unlimited" (la API no siempre lo trae)
    val imageUrl: String? = null,
    val price: String? = null     // TCGPlayer USD de esta impresión concreta
)
