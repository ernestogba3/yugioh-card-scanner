package com.example.yugiohscanner.data.catalog

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un arte (ilustración) de una carta. La misma carta puede tener varios artes (reediciones con
 * dibujo distinto); cada uno tiene su propio id (`artId`) en YGOPRODeck.
 *
 * Pertenece al catálogo de SOLO LECTURA (`catalog.db`). Se rellena en el primer arranque desde
 * el array `images` de cada carta en `catalog.json`. Sirve para el selector de arte: el usuario
 * elige cuál posee y se guarda como `chosenArtId` en su colección.
 *
 * `passcode` apunta a [Card.id] (clave lógica, sin FK porque la colección vive en otra base).
 */
@Entity(
    tableName = "card_arts",
    indices = [Index("passcode")]
)
data class CardArt(
    @PrimaryKey val artId: Long,   // id del arte en YGOPRODeck
    val passcode: Long,            // -> Card.id
    val url: String?,              // imagen grande
    val urlSmall: String?          // imagen pequeña (miniatura del selector)
)
