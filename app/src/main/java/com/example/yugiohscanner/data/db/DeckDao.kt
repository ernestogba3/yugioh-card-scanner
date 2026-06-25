package com.example.yugiohscanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.yugiohscanner.data.model.Deck
import com.example.yugiohscanner.data.model.DeckCard
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    // --- Mazos ---

    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    fun obtenerMazos(): Flow<List<Deck>>

    // Snapshot puntual (no Flow), para subir los mazos a la nube (backup).
    @Query("SELECT * FROM decks")
    suspend fun obtenerMazosUnaVez(): List<Deck>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun obtenerMazo(id: Long): Deck?

    // Borra todos los mazos (CASCADE borra también sus deck_cards). Para restaurar.
    @Query("DELETE FROM decks")
    suspend fun eliminarTodosLosMazos()

    @Insert
    suspend fun crearMazo(deck: Deck): Long

    @Update
    suspend fun actualizarMazo(deck: Deck)

    @Delete
    suspend fun eliminarMazo(deck: Deck)

    // --- Cartas de un mazo ---

    @Query("SELECT * FROM deck_cards WHERE deckId = :deckId")
    suspend fun cartasDeMazo(deckId: Long): List<DeckCard>

    @Query("SELECT * FROM deck_cards WHERE deckId = :deckId AND cardId = :cardId")
    suspend fun cartaEnMazo(deckId: Long, cardId: Long): DeckCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarCartaEnMazo(carta: DeckCard)

    @Query("DELETE FROM deck_cards WHERE deckId = :deckId AND cardId = :cardId")
    suspend fun quitarCartaDeMazo(deckId: Long, cardId: Long)

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM deck_cards WHERE deckId = :deckId")
    suspend fun totalCartas(deckId: Long): Int
}
