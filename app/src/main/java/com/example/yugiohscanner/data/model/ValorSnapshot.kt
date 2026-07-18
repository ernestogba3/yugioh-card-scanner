package com.example.yugiohscanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Foto del valor total de la colección en un día concreto. Vive en la BD de USUARIO
 * (`AppDatabase`). La clave primaria es la fecha (yyyy-MM-dd), así que hay como mucho UNA fila
 * por día: al recalcular el valor el mismo día, se reemplaza. Con estas fotos se dibuja la
 * gráfica de evolución y se calcula la tendencia "esta semana".
 */
@Entity(tableName = "valor_historico")
data class ValorSnapshot(
    @PrimaryKey val fecha: String,   // "yyyy-MM-dd"
    val valorEur: Double,
    val timestamp: Long
)
