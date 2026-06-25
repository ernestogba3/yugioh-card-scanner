package com.example.yugiohscanner.data.repository

import android.content.Context
import com.example.yugiohscanner.data.catalog.CatalogDatabase
import com.example.yugiohscanner.data.db.AppDatabase
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.data.model.Deck
import com.example.yugiohscanner.data.model.DeckCard
import kotlinx.coroutines.flow.Flow

/**
 * Reglas de tamaño de un mazo según Yu-Gi-Oh. La app trata el mazo como Deck Principal + Extra
 * (no hay Side). Las cartas de Fusión/Sincronía/XYZ/Link van al Extra; el resto al Principal.
 */
object ReglasMazo {
    const val PRINCIPAL_MIN = 40
    const val PRINCIPAL_MAX = 60
    const val EXTRA_MAX = 15
    const val MAX_COPIAS = 3

    /** ¿Esta carta pertenece al Deck Extra (no cuenta para el 40–60 del Principal)? */
    fun esExtra(type: String): Boolean {
        val t = type.lowercase()
        return "fusion" in t || "synchro" in t || "xyz" in t || "link" in t
    }
}

/** Una carta dentro de un mazo, ya lista para pintar: ficha + cuántas pide y cuántas posees. */
data class CartaEnMazo(
    val carta: CartaYuGiOh,
    val cantidad: Int,        // copias en el mazo (1..3)
    val enColeccion: Int      // copias que el usuario tiene en su colección
) {
    val faltan: Int get() = (cantidad - enColeccion).coerceAtLeast(0)
}

/**
 * Sugerencia de mazo: un arquetipo que el usuario YA colecciona, con cuántas cartas distintas
 * tiene de él y cuántas existen en total. Sirve para proponer "completa tu mazo de X".
 */
data class SugerenciaArquetipo(
    val arquetipo: String,
    val poseidas: Int,        // cartas distintas de este arquetipo que el usuario tiene
    val totalCatalogo: Int    // cartas distintas de este arquetipo que existen en el catálogo
) {
    /** % de cartas del arquetipo que ya posee (0..100). */
    val porcentaje: Int get() = if (totalCatalogo == 0) 0 else (poseidas * 100 / totalCatalogo)
}

/** Una carta de un arquetipo al previsualizarlo: su ficha y cuántas copias posee el usuario. */
data class CartaArquetipo(
    val carta: CartaYuGiOh,
    val enColeccion: Int
)

/**
 * Lógica de mazos. Cruza tres orígenes de datos: los mazos (user.db), las fichas de carta
 * (catálogo, solo lectura) y la colección (user.db) para calcular las cartas que faltan.
 */
class DeckRepository(context: Context) {

    private val deckDao = AppDatabase.getInstance(context).deckDao()
    private val cartaDao = AppDatabase.getInstance(context).cartaDao()
    private val catalogDao = CatalogDatabase.getInstance(context).catalogDao()

    fun mazos(): Flow<List<Deck>> = deckDao.obtenerMazos()

    suspend fun crearMazo(nombre: String, descripcion: String?): Long =
        deckDao.crearMazo(
            Deck(name = nombre.trim(), description = descripcion?.trim()?.ifBlank { null })
        )

    suspend fun eliminarMazo(deck: Deck) = deckDao.eliminarMazo(deck)

    /** Renombra/edita la descripción de un mazo existente (conserva sus cartas). */
    suspend fun renombrarMazo(deckId: Long, nombre: String, descripcion: String?) {
        val mazo = deckDao.obtenerMazo(deckId) ?: return
        deckDao.actualizarMazo(
            mazo.copy(
                name = nombre.trim().ifBlank { mazo.name },
                description = descripcion?.trim()?.ifBlank { null },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Crea una copia del mazo (mismas cartas y cantidades) con el sufijo "(copia)". */
    suspend fun duplicarMazo(deckId: Long): Long {
        val orig = deckDao.obtenerMazo(deckId) ?: return -1L
        val nuevoId = deckDao.crearMazo(
            Deck(name = "${orig.name} (copia)", description = orig.description)
        )
        deckDao.cartasDeMazo(deckId).forEach { dc ->
            deckDao.guardarCartaEnMazo(DeckCard(nuevoId, dc.cardId, dc.quantity))
        }
        return nuevoId
    }

    /**
     * Añade una copia de la carta al mazo respetando las reglas (máx. 3 copias, Principal ≤ 60,
     * Extra ≤ 15). Devuelve un texto de error si NO se pudo añadir, o null si se añadió bien.
     */
    suspend fun anadirCarta(deckId: Long, cardId: Long): String? {
        val card = catalogDao.obtenerCartaPorId(cardId) ?: return "Carta no encontrada"
        val actual = deckDao.cartaEnMazo(deckId, cardId)
        val copias = actual?.quantity ?: 0
        if (copias >= ReglasMazo.MAX_COPIAS) return "Máximo ${ReglasMazo.MAX_COPIAS} copias por carta"

        comprobarHueco(deckId, card.type)?.let { return it }

        deckDao.guardarCartaEnMazo(DeckCard(deckId, cardId, copias + 1))
        tocar(deckId)
        return null
    }

    /** Suma/resta copias respetando las reglas; si baja de 1, quita la carta. Devuelve error o null. */
    suspend fun cambiarCantidad(deckId: Long, cardId: Long, delta: Int): String? {
        val actual = deckDao.cartaEnMazo(deckId, cardId) ?: return null
        val nueva = actual.quantity + delta
        if (nueva <= 0) {
            deckDao.quitarCartaDeMazo(deckId, cardId)
            tocar(deckId)
            return null
        }
        if (nueva > ReglasMazo.MAX_COPIAS) return "Máximo ${ReglasMazo.MAX_COPIAS} copias por carta"
        if (delta > 0) {
            val card = catalogDao.obtenerCartaPorId(cardId)
            if (card != null) comprobarHueco(deckId, card.type)?.let { return it }
        }
        deckDao.guardarCartaEnMazo(actual.copy(quantity = nueva))
        tocar(deckId)
        return null
    }

    /**
     * Comprueba si cabe una carta más de [type] en el mazo. Devuelve el texto de error si la
     * zona (Principal o Extra) está llena, o null si hay hueco.
     */
    private suspend fun comprobarHueco(deckId: Long, type: String): String? {
        val (principal, extra) = totalesPorZona(deckId)
        return if (ReglasMazo.esExtra(type)) {
            if (extra >= ReglasMazo.EXTRA_MAX) "Deck Extra lleno (máx. ${ReglasMazo.EXTRA_MAX})" else null
        } else {
            if (principal >= ReglasMazo.PRINCIPAL_MAX) "Deck Principal lleno (máx. ${ReglasMazo.PRINCIPAL_MAX})" else null
        }
    }

    /** Suma de copias del mazo separadas en (Deck Principal, Deck Extra). */
    private suspend fun totalesPorZona(deckId: Long): Pair<Int, Int> {
        val enMazo = deckDao.cartasDeMazo(deckId)
        if (enMazo.isEmpty()) return 0 to 0
        val tipos = catalogDao.obtenerCartasPorIds(enMazo.map { it.cardId }).associate { it.id to it.type }
        var principal = 0
        var extra = 0
        enMazo.forEach { dc ->
            if (ReglasMazo.esExtra(tipos[dc.cardId] ?: "")) extra += dc.quantity else principal += dc.quantity
        }
        return principal to extra
    }

    suspend fun quitarCarta(deckId: Long, cardId: Long) {
        deckDao.quitarCartaDeMazo(deckId, cardId)
        tocar(deckId)
    }

    /** Cartas del mazo listas para la UI, con la info de catálogo y las que faltan. */
    suspend fun detalleMazo(deckId: Long): List<CartaEnMazo> {
        val enMazo = deckDao.cartasDeMazo(deckId)
        if (enMazo.isEmpty()) return emptyList()

        val fichas = catalogDao.obtenerCartasPorIds(enMazo.map { it.cardId }).associateBy { it.id }
        val poseidasPorId = cartaDao.obtenerCardIds().groupingBy { it }.eachCount()

        return enMazo.mapNotNull { dc ->
            val card = fichas[dc.cardId] ?: return@mapNotNull null
            CartaEnMazo(
                carta = card.aCartaYuGiOh(),
                cantidad = dc.quantity,
                enColeccion = poseidasPorId[dc.cardId.toInt()] ?: 0
            )
        }.sortedBy { it.carta.nombreEs ?: it.carta.name }
    }

    // --- Sugerencias de mazos por arquetipo (Fase 4) ---

    /**
     * Mira los arquetipos que el usuario YA colecciona y los propone como mazos a completar,
     * ordenados por cuántas cartas distintas tiene de cada uno. Todo local: cruza la colección
     * (user.db) con los arquetipos del catálogo (catalog.db).
     */
    suspend fun sugerenciasArquetipos(maximo: Int = 8): List<SugerenciaArquetipo> {
        val idsPoseidos = cartaDao.obtenerCardIds().distinct()
        if (idsPoseidos.isEmpty()) return emptyList()

        // Arquetipo de cada carta poseída (en lotes: SQLite limita el IN a 999 variables).
        val poseidasPorArquetipo = idsPoseidos
            .map { it.toLong() }
            .chunked(900)
            .flatMap { catalogDao.obtenerArquetipos(it) }
            .mapNotNull { it.archetype?.takeIf { a -> a.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
        if (poseidasPorArquetipo.isEmpty()) return emptyList()

        val totales = catalogDao.obtenerConteoArquetipos().associate { it.archetype to it.total }

        return poseidasPorArquetipo
            .map { (arq, n) -> SugerenciaArquetipo(arq, n, totales[arq] ?: n) }
            .sortedWith(
                compareByDescending<SugerenciaArquetipo> { it.poseidas }
                    .thenByDescending { it.porcentaje }
            )
            .take(maximo)
    }

    /** Cartas de un arquetipo para previsualizarlo: primero las que ya tienes. */
    suspend fun cartasDeArquetipo(arquetipo: String): List<CartaArquetipo> {
        val poseidasPorId = cartaDao.obtenerCardIds().groupingBy { it }.eachCount()
        return catalogDao.obtenerCartasDeArquetipo(arquetipo)
            .map { card ->
                CartaArquetipo(
                    carta = card.aCartaYuGiOh(),
                    enColeccion = poseidasPorId[card.id.toInt()] ?: 0
                )
            }
            .sortedWith(
                compareByDescending<CartaArquetipo> { it.enColeccion }
                    .thenBy { it.carta.nombreEs ?: it.carta.name }
            )
    }

    /**
     * Crea un mazo a partir de un arquetipo. Si [soloPoseidas] es true añade solo las cartas que
     * el usuario tiene (con las copias que posee, máx. 3); si es false añade todas las del
     * arquetipo con 1 copia. Devuelve el id del mazo nuevo.
     */
    suspend fun crearMazoDesdeArquetipo(arquetipo: String, soloPoseidas: Boolean): Long {
        val cartas = catalogDao.obtenerCartasDeArquetipo(arquetipo)
        val poseidasPorId = cartaDao.obtenerCardIds().groupingBy { it }.eachCount()
        val seleccion =
            if (soloPoseidas) cartas.filter { (poseidasPorId[it.id.toInt()] ?: 0) > 0 } else cartas

        val nuevoId = deckDao.crearMazo(
            Deck(name = arquetipo, description = "Mazo sugerido · $arquetipo")
        )
        // Vuelca las cartas sin pasarse de los topes (Principal ≤ 60, Extra ≤ 15).
        var principal = 0
        var extra = 0
        seleccion.forEach { card ->
            val deseadas =
                if (soloPoseidas) (poseidasPorId[card.id.toInt()] ?: 1).coerceIn(1, ReglasMazo.MAX_COPIAS) else 1
            val esExtra = ReglasMazo.esExtra(card.type)
            val hueco =
                if (esExtra) ReglasMazo.EXTRA_MAX - extra else ReglasMazo.PRINCIPAL_MAX - principal
            val copias = deseadas.coerceAtMost(hueco)
            if (copias > 0) {
                deckDao.guardarCartaEnMazo(DeckCard(nuevoId, card.id, copias))
                if (esExtra) extra += copias else principal += copias
            }
        }
        return nuevoId
    }

    /** Actualiza la marca de tiempo del mazo (para ordenarlos por "último editado" y sync). */
    private suspend fun tocar(deckId: Long) {
        deckDao.obtenerMazo(deckId)?.let {
            deckDao.actualizarMazo(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}
