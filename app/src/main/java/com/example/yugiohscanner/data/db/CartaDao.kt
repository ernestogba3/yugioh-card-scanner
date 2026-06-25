package com.example.yugiohscanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.yugiohscanner.data.model.CartaGuardada
import kotlinx.coroutines.flow.Flow

@Dao
interface CartaDao {
    @Query("SELECT * FROM cartas_guardadas ORDER BY fechaGuardado DESC")
    fun obtenerTodas(): Flow<List<CartaGuardada>>

    // cardIds que el usuario posee (una entrada por copia). Para calcular cartas faltantes
    // de un mazo cruzando con la colección.
    @Query("SELECT cardId FROM cartas_guardadas")
    suspend fun obtenerCardIds(): List<Int>

    // Snapshot puntual (no Flow), para subir la colección a la nube (backup).
    @Query("SELECT * FROM cartas_guardadas")
    suspend fun obtenerTodasUnaVez(): List<CartaGuardada>

    // Sin REPLACE: cada inserción crea una fila nueva (permite copias repetidas).
    @Insert
    suspend fun insertar(carta: CartaGuardada): Long

    // Inserción por lotes (al restaurar una copia de seguridad).
    @Insert
    suspend fun insertarTodas(cartas: List<CartaGuardada>)

    // Vacía la colección local (al restaurar, se reemplaza por la copia de la nube).
    @Query("DELETE FROM cartas_guardadas")
    suspend fun eliminarTodas()

    // Borra una copia concreta (por su idLocal).
    @Delete
    suspend fun eliminar(carta: CartaGuardada)

    // Marca/desmarca como favorita TODAS las copias de la misma carta (mismo cardId).
    @Query("UPDATE cartas_guardadas SET favorito = :favorito WHERE cardId = :cardId")
    suspend fun marcarFavorito(cardId: Int, favorito: Boolean)
}
