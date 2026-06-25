package com.example.yugiohscanner.data.repository

import android.content.Context
import com.example.yugiohscanner.data.catalog.Card
import com.example.yugiohscanner.data.catalog.CardArt
import com.example.yugiohscanner.data.catalog.CardHash
import com.example.yugiohscanner.data.catalog.CatalogDatabase
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.data.scan.PHash
import com.example.yugiohscanner.data.search.Similitud
import com.example.yugiohscanner.data.search.TextoUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Única puerta de entrada a los datos de cartas para los ViewModels.
 *
 * En el rediseño offline-first la app NO llama a ningún backend para buscar: todo se
 * resuelve en local contra el catálogo de Room. El repositorio también traduce las entidades
 * del catálogo ([Card] + [CardPrint]) al modelo [CartaYuGiOh] que la UI ya conoce, de modo
 * que las pantallas no necesitan cambiar.
 */
class CardRepository(context: Context) {

    private val dao = CatalogDatabase.getInstance(context).catalogDao()

    /**
     * Búsqueda por nombre (escaneo OCR o texto escrito) tolerante a erratas.
     *
     * Puntúa la consulta contra el índice de nombres (ES y EN) con Jaro-Winkler / Levenshtein
     * y comparación por palabras, se queda con las mejores y las ordena de más a menos
     * parecida. La primera es la "mejor coincidencia". Todo en local, sin red.
     */
    suspend fun buscarPorNombre(nombre: String): List<CartaYuGiOh> = withContext(Dispatchers.Default) {
        val consulta = TextoUtil.normalizar(nombre)
        if (consulta.isEmpty()) return@withContext emptyList()
        rankearYRecuperar(listOf(consulta))
    }

    /**
     * Búsqueda tolerante probando VARIOS candidatos de nombre a la vez (p.ej. las distintas
     * líneas que el OCR pudo leer del título de la carta). Cada carta se puntúa con el MEJOR
     * de todos los candidatos, así basta con que uno acierte para que la carta salga arriba.
     *
     * Es la base del "OCR avanzado": en vez de jugárselo todo a una sola lectura, la cámara
     * manda varias hipótesis y aquí elegimos la que mejor encaja con el catálogo.
     */
    suspend fun buscarPorVariosNombres(candidatos: List<String>): List<CartaYuGiOh> =
        withContext(Dispatchers.Default) {
            val consultas = candidatos
                .map { TextoUtil.normalizar(it) }
                .filter { it.isNotEmpty() }
                .distinct()
            if (consultas.isEmpty()) return@withContext emptyList()
            rankearYRecuperar(consultas)
        }

    /**
     * Puntúa el índice contra una o varias consultas (ya normalizadas), se queda con las
     * mejores por encima del umbral y recupera sus fichas completas en orden de parecido.
     */
    private suspend fun rankearYRecuperar(consultas: List<String>): List<CartaYuGiOh> {
        val mejores = indiceNombres()
            .map { entrada ->
                // Mejor parecido entre todas las consultas y los nombres ES/EN de la carta.
                val parecido = consultas.maxOf { c ->
                    maxOf(
                        entrada.normEs?.let { Similitud.puntuarNormalizado(c, it) } ?: 0.0,
                        Similitud.puntuarNormalizado(c, entrada.normEn)
                    )
                }
                entrada to parecido
            }
            .filter { it.second >= UMBRAL }
            .sortedWith(
                compareByDescending<Pair<EntradaIndice, Double>> { it.second }
                    .thenBy { it.first.normEn.length }
            )
            .take(MAX_RESULTADOS)

        if (mejores.isEmpty()) return emptyList()

        // Recupera las fichas completas y las devuelve en el mismo orden de parecido.
        val ids = mejores.map { it.first.id }
        val porId = dao.obtenerCartasPorIds(ids).associateBy { it.id }
        return mejores.mapNotNull { porId[it.first.id]?.aCartaConPrints() }
    }

    /** Búsqueda por filtros. Cualquier parámetro null se ignora; atk/def son mínimos. */
    suspend fun buscarPorFiltros(
        nombre: String?,
        tipo: String?,
        nivel: Int?,
        atributo: String?,
        raza: String?,
        arquetipo: String?,
        atkMin: Int?,
        defMin: Int?,
        rareza: String?
    ): List<CartaYuGiOh> =
        dao.buscarPorFiltros(nombre, tipo, nivel, atributo, raza, arquetipo, atkMin, defMin, rareza)
            .map { it.aCartaConPrints() }

    /** Mapa cardId -> arquetipo (para las estadísticas de la colección). */
    suspend fun arquetiposPorId(ids: List<Int>): Map<Int, String?> =
        // En lotes de 900: SQLite limita a 999 las variables de un IN (...).
        ids.map { it.toLong() }
            .chunked(900)
            .flatMap { dao.obtenerArquetipos(it) }
            .associate { it.id.toInt() to it.archetype }

    /** Ficha completa de una carta por su id (pantalla de detalle). */
    suspend fun obtenerCartaPorId(id: Int): CartaYuGiOh? =
        dao.obtenerCartaPorId(id.toLong())?.aCartaConPrints()

    // --- Escaneo passcode-first (Fase 2) ---

    /**
     * Identifica una carta por su passcode (los 8 dígitos del escáner). El passcode ES el id de
     * la carta en YGOPRODeck, así que es una búsqueda directa: la vía más fiable e independiente
     * del idioma.
     */
    suspend fun buscarPorPasscode(passcode: Long): CartaYuGiOh? =
        dao.obtenerCartaPorId(passcode)?.aCartaConPrints()

    /**
     * Fallback visual: busca el arte del catálogo cuyo pHash más se parece al de la carta
     * escaneada (menor distancia de Hamming). Devuelve la carta, el arte concreto y la distancia,
     * o null si no hay ninguno por debajo del umbral (o si aún no se han importado los hashes).
     */
    suspend fun buscarPorHash(pHash: String, umbral: Int = UMBRAL_HAMMING): ResultadoHash? =
        withContext(Dispatchers.Default) {
            var mejor: CardHash? = null
            var mejorDist = Int.MAX_VALUE
            for (h in hashes()) {
                val d = PHash.distanciaHamming(pHash, h.pHash)
                if (d < mejorDist) {
                    mejorDist = d
                    mejor = h
                }
            }
            val ganador = mejor ?: return@withContext null
            if (mejorDist > umbral) return@withContext null
            val carta = dao.obtenerCartaPorId(ganador.passcode)?.aCartaConPrints()
                ?: return@withContext null
            ResultadoHash(carta, ganador.artId, mejorDist)
        }

    /** Artes (ilustraciones) de una carta, para el selector de arte. */
    suspend fun obtenerArtesDeCarta(passcode: Long): List<CardArt> =
        dao.obtenerArtesDeCarta(passcode)

    /** Resultado del fallback visual: la carta, el arte que coincidió y su distancia de Hamming. */
    data class ResultadoHash(val carta: CartaYuGiOh, val artId: Long, val distancia: Int)

    /** Todos los pHash del catálogo, cargados una sola vez y compartidos (no cambian). */
    private suspend fun hashes(): List<CardHash> {
        hashesCache?.let { return it }
        return hashMutex.withLock {
            hashesCache ?: dao.obtenerTodosLosHashes().also { hashesCache = it }
        }
    }

    /** Mapa nombre de set -> nº total de cartas (para los porcentajes de colección). */
    suspend fun obtenerTotalesPorSet(): Map<String, Int> =
        dao.obtenerSets().associate { it.setName to it.numOfCards }

    /** Todas las cartas (id, nombre, imagen) de un set, para el álbum por sets. */
    suspend fun obtenerCartasDeSet(setName: String): List<Card> =
        dao.obtenerCartasDeSet(setName)

    /** Ids de las cartas de un set (para el % de completado de la caja, igual que el álbum). */
    suspend fun obtenerCardIdsDeSet(setName: String): List<Long> =
        dao.obtenerCardIdsDeSet(setName)

    /**
     * Índice de nombres ya normalizados, cargado una sola vez y compartido por toda la app
     * (el catálogo no cambia tras importarse). El mutex evita que dos búsquedas a la vez lo
     * construyan dos veces.
     */
    private suspend fun indiceNombres(): List<EntradaIndice> {
        indice?.let { return it }
        return mutex.withLock {
            indice ?: dao.obtenerIndiceNombres().map {
                EntradaIndice(
                    id = it.id,
                    normEs = it.nameEs?.let(TextoUtil::normalizar),
                    normEn = TextoUtil.normalizar(it.nameEn)
                )
            }.also { indice = it }
        }
    }

    /** Entrada del índice en memoria con los nombres ya normalizados (no se re-normaliza). */
    private data class EntradaIndice(val id: Long, val normEs: String?, val normEn: String)

    // --- Mapeo catálogo -> modelo de UI (incluye los sets de la carta) ---

    private suspend fun Card.aCartaConPrints(): CartaYuGiOh =
        aCartaYuGiOh(dao.obtenerPrints(id))

    companion object {
        // Umbral mínimo de parecido (0..1) para considerar una carta como resultado.
        private const val UMBRAL = 0.62
        // Máximo de resultados que se devuelven, ordenados de más a menos parecido.
        private const val MAX_RESULTADOS = 30
        // Distancia de Hamming máxima (de 64 bits) para aceptar un match visual por pHash.
        // Más bajo = más estricto. Puede necesitar ajuste fino tras probar en el dispositivo.
        private const val UMBRAL_HAMMING = 10

        private val mutex = Mutex()
        @Volatile
        private var indice: List<EntradaIndice>? = null

        private val hashMutex = Mutex()
        @Volatile
        private var hashesCache: List<CardHash>? = null

        /**
         * Tira las cachés en memoria (índice de nombres y hashes). Hay que llamarlo tras
         * reconstruir el catálogo (actualización desde la nube) para que las búsquedas usen los
         * datos nuevos en vez de los antiguos.
         */
        fun invalidarCaches() {
            indice = null
            hashesCache = null
        }
    }
}
