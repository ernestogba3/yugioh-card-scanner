package com.example.yugiohscanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yugiohscanner.data.model.ValorSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface ValorHistoricoDao {

    // Guarda (o reemplaza) la foto del valor de HOY. La clave es la fecha, así que solo hay
    // una fila por día: si el valor cambia durante el día, se actualiza esa fila.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(snapshot: ValorSnapshot)

    // Histórico completo, de la fecha más antigua a la más reciente (para la gráfica).
    @Query("SELECT * FROM valor_historico ORDER BY fecha ASC")
    fun historico(): Flow<List<ValorSnapshot>>

    // Vacía el histórico (al restaurar una copia de seguridad de la colección).
    @Query("DELETE FROM valor_historico")
    suspend fun eliminarTodo()
}
