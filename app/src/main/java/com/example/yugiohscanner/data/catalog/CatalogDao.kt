package com.example.yugiohscanner.data.catalog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CatalogDao {

    // --- Importación (primer arranque) ---

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun contarCartas(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarCartas(cartas: List<Card>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarPrints(prints: List<CardPrint>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarSets(sets: List<CardSet>)

    // --- Reconstrucción del catálogo (actualización descargada) ---
    // Se borran cards/prints/sets/arts y se reimporta del JSON nuevo. Los pHash (card_hashes)
    // NO se tocan: vienen de phashes.json y no cambian con la actualización del catálogo.
    @Query("DELETE FROM cards")
    suspend fun borrarCartas()

    @Query("DELETE FROM card_prints")
    suspend fun borrarPrints()

    @Query("DELETE FROM sets")
    suspend fun borrarSets()

    @Query("DELETE FROM card_arts")
    suspend fun borrarArtes()

    @Query("SELECT COUNT(*) FROM card_hashes")
    suspend fun contarHashes(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarHashes(hashes: List<CardHash>)

    /**
     * Todos los pHash del catálogo. El repositorio los carga una vez y busca el más parecido
     * al de la carta escaneada por distancia de Hamming en Kotlin (fallback visual, Fase 2).
     */
    @Query("SELECT * FROM card_hashes")
    suspend fun obtenerTodosLosHashes(): List<CardHash>

    @Query("SELECT COUNT(*) FROM card_arts")
    suspend fun contarArtes(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarArtes(artes: List<CardArt>)

    /** Artes (ilustraciones) de una carta, para el selector de arte. */
    @Query("SELECT * FROM card_arts WHERE passcode = :passcode")
    suspend fun obtenerArtesDeCarta(passcode: Long): List<CardArt>

    // --- Consultas en runtime (todo local, sin red) ---

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun obtenerCartaPorId(id: Long): Card?

    @Query("SELECT * FROM cards WHERE id IN (:ids)")
    suspend fun obtenerCartasPorIds(ids: List<Long>): List<Card>

    @Query("SELECT * FROM card_prints WHERE cardId = :cardId")
    suspend fun obtenerPrints(cardId: Long): List<CardPrint>

    @Query("SELECT * FROM sets")
    suspend fun obtenerSets(): List<CardSet>

    /** Todas las cartas distintas que pertenecen a un set (para el álbum por sets). */
    @Query(
        """
        SELECT * FROM cards
        WHERE id IN (SELECT DISTINCT cardId FROM card_prints WHERE setName = :setName)
        ORDER BY nameEn
        """
    )
    suspend fun obtenerCartasDeSet(setName: String): List<Card>

    /** Ids de las cartas distintas de un set (para el % de completado, mismo criterio que el álbum). */
    @Query("SELECT DISTINCT cardId FROM card_prints WHERE setName = :setName")
    suspend fun obtenerCardIdsDeSet(setName: String): List<Long>

    /**
     * Índice ligero (solo id + nombres) de TODAS las cartas. El repositorio lo carga una vez
     * y hace sobre él el ranking fuzzy en memoria (Levenshtein / Jaro-Winkler) para tolerar
     * las erratas del OCR mejor que una búsqueda por LIKE o FTS.
     */
    @Query("SELECT id, nameEs, nameEn FROM cards")
    suspend fun obtenerIndiceNombres(): List<NombreCarta>

    /**
     * Búsqueda por filtros (tipo, nivel, atributo, ATK/DEF mínimos). Cualquier parámetro
     * null se ignora. `attribute` se compara sin distinguir mayúsculas (COLLATE NOCASE).
     */
    @Query(
        """
        SELECT * FROM cards
        WHERE (:nombre IS NULL OR nameEs LIKE '%' || :nombre || '%' OR nameEn LIKE '%' || :nombre || '%')
          AND (:tipo IS NULL OR type = :tipo)
          AND (:nivel IS NULL OR level = :nivel)
          AND (:atributo IS NULL OR attribute = :atributo COLLATE NOCASE)
          AND (:raza IS NULL OR race = :raza COLLATE NOCASE)
          AND (:arquetipo IS NULL OR archetype LIKE '%' || :arquetipo || '%' COLLATE NOCASE)
          AND (:atkMin IS NULL OR atk >= :atkMin)
          AND (:defMin IS NULL OR def >= :defMin)
          AND (:rareza IS NULL OR EXISTS (
                SELECT 1 FROM card_prints p
                WHERE p.cardId = cards.id AND p.rarity = :rareza COLLATE NOCASE))
        ORDER BY length(nameEn)
        LIMIT :limite
        """
    )
    suspend fun buscarPorFiltros(
        nombre: String?,
        tipo: String?,
        nivel: Int?,
        atributo: String?,
        raza: String?,
        arquetipo: String?,
        atkMin: Int?,
        defMin: Int?,
        rareza: String?,
        limite: Int = 100
    ): List<Card>

    /** Arquetipo de cada carta indicada (para estadísticas de colección). */
    @Query("SELECT id, archetype FROM cards WHERE id IN (:ids)")
    suspend fun obtenerArquetipos(ids: List<Long>): List<ArquetipoCarta>

    /**
     * Nº de cartas distintas por arquetipo en todo el catálogo (ignora las cartas sin
     * arquetipo). Sirve para calcular cuánto te falta de cada arquetipo que ya coleccionas.
     */
    @Query(
        """
        SELECT archetype AS archetype, COUNT(*) AS total
        FROM cards
        WHERE archetype IS NOT NULL AND archetype != ''
        GROUP BY archetype
        """
    )
    suspend fun obtenerConteoArquetipos(): List<ConteoArquetipo>

    /** Todas las cartas de un arquetipo (para previsualizar y crear un mazo sugerido). */
    @Query("SELECT * FROM cards WHERE archetype = :arquetipo ORDER BY nameEn")
    suspend fun obtenerCartasDeArquetipo(arquetipo: String): List<Card>
}
