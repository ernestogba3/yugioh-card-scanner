package com.example.yugiohscanner.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mazo del usuario. Vive en la base de datos de USUARIO (`AppDatabase`/yugioh_db), junto a la
 * colección. `updatedAt` servirá para la sincronización opcional con Firebase (Fase 6).
 */
@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Relación mazo ↔ carta. `cardId` es una clave LÓGICA hacia el catálogo (otra BD); `deckId`
 * sí es foreign key real: al borrar un mazo se borran sus cartas (CASCADE).
 * `quantity` 1..3 según las reglas del juego.
 */
@Entity(
    tableName = "deck_cards",
    primaryKeys = ["deckId", "cardId"],
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cardId")]
)
data class DeckCard(
    val deckId: Long,
    val cardId: Long,
    val quantity: Int = 1
)
