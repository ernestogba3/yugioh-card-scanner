package com.example.yugiohscanner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.yugiohscanner.data.model.CartaGuardada
import com.example.yugiohscanner.data.model.Deck
import com.example.yugiohscanner.data.model.DeckCard
import com.example.yugiohscanner.data.model.ValorSnapshot

/**
 * Migración 3 → 4: añade las tablas de mazos SIN borrar la colección del usuario.
 * El SQL es exactamente el que Room genera (ver app/schemas/.../4.json), por lo que el
 * identityHash cuadra y la migración es válida.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `decks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `description` TEXT, `updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deck_cards` (`deckId` INTEGER NOT NULL, `cardId` INTEGER NOT NULL, " +
                "`quantity` INTEGER NOT NULL, PRIMARY KEY(`deckId`, `cardId`), " +
                "FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_deck_cards_cardId` ON `deck_cards` (`cardId`)")
    }
}

/**
 * Migración 4 → 5: añade la columna `condicion` (estado físico de la carta) a la colección,
 * SIN borrar nada. Columna TEXT nullable, que es justo lo que Room espera de `String?`.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cartas_guardadas` ADD COLUMN `condicion` TEXT")
    }
}

/**
 * Migración 5 → 6: añade la columna `favorito` (carta marcada como favorita) a la colección,
 * SIN borrar nada. Room guarda los Boolean como INTEGER NOT NULL, por eso DEFAULT 0 (false).
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cartas_guardadas` ADD COLUMN `favorito` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Migración 6 → 7: añade la columna `rareza` (rareza de la impresión que posee el usuario) a la
 * colección, SIN borrar nada. Es el paso "fino" hacia el modelo CollectionItem (impresión/rareza
 * por carta) sin renombrar la tabla, para conservar colección, mazos y backups existentes.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cartas_guardadas` ADD COLUMN `rareza` TEXT")
    }
}

/**
 * Migración 7 → 8: añade la columna `chosenArtId` (arte elegido por el usuario) a la colección,
 * SIN borrar nada. Columna INTEGER nullable (Room guarda Long? como INTEGER), para permitir
 * copias de la misma carta con ilustraciones distintas (selector de arte, Fase 2).
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cartas_guardadas` ADD COLUMN `chosenArtId` INTEGER")
    }
}

/**
 * Migración 8 → 9: crea la tabla `valor_historico` (foto del valor de la colección por día),
 * SIN tocar la colección ni los mazos. El SQL es exactamente el que Room genera para
 * [ValorSnapshot] (ver app/schemas/.../9.json), por lo que el identityHash cuadra.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `valor_historico` (`fecha` TEXT NOT NULL, " +
                "`valorEur` REAL NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`fecha`))"
        )
    }
}

/**
 * Base de datos del USUARIO (colección + mazos). Es la "user.db" del diseño: datos que el
 * usuario crea y que, en la Fase 6, se sincronizarán opcionalmente con Firebase.
 * Separada del catálogo (`CatalogDatabase`), que es de solo lectura.
 */
@Database(
    entities = [CartaGuardada::class, Deck::class, DeckCard::class, ValorSnapshot::class],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cartaDao(): CartaDao
    abstract fun deckDao(): DeckDao
    abstract fun valorHistoricoDao(): ValorHistoricoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yugioh_db"
                )
                    // Migración real para 3→4 (conserva colección y mazos). El borrado
                    // destructivo queda solo como red de seguridad para saltos de versión muy
                    // antiguos (1/2 → 4) que no tienen migración definida.
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
