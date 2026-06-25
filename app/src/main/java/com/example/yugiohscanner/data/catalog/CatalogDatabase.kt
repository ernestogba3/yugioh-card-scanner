package com.example.yugiohscanner.data.catalog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos del CATÁLOGO (solo lectura desde la app). Contiene las cartas, sus
 * impresiones y los sets. Se rellena en el primer arranque desde el JSON empaquetado.
 *
 * Está separada de la base de datos del usuario (colección/mazos) a propósito: así se puede
 * actualizar el catálogo en futuras versiones sin tocar los datos del usuario. Ver
 * `docs/ARQUITECTURA.md` §5.1.
 */
@Database(
    entities = [Card::class, CardPrint::class, CardSet::class, CardHash::class, CardArt::class],
    version = 4,
    exportSchema = false
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        @Volatile
        private var INSTANCE: CatalogDatabase? = null

        fun getInstance(context: Context): CatalogDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    CatalogDatabase::class.java,
                    "catalog.db"
                )
                    // Al subir la versión del catálogo, se reconstruye desde el JSON nuevo.
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
