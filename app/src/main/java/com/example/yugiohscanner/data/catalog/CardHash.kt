package com.example.yugiohscanner.data.catalog

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * pHash (perceptual hash) de UN arte de una carta, para el fallback visual del escáner cuando
 * el passcode no se lee (foils, artes alternativos, cartas antiguas).
 *
 * Pertenece al catálogo de SOLO LECTURA (`catalog.db`). Se rellena en el primer arranque desde
 * `assets/database/phashes.json`, que genera el backend (`npm run phash`). El matching por
 * distancia de Hamming se hace en Kotlin (Fase 2), no en SQL.
 *
 * `artId` es la clave (cada arte de YGOPRODeck tiene id propio); `passcode` apunta a [Card.id]
 * (clave lógica) para recuperar la carta una vez identificado el arte.
 */
@Entity(
    tableName = "card_hashes",
    indices = [Index("passcode")]
)
data class CardHash(
    @PrimaryKey val artId: Long,   // id del arte en YGOPRODeck
    val passcode: Long,            // -> Card.id (clave lógica)
    val pHash: String              // 64 bits en hexadecimal (16 caracteres)
)
