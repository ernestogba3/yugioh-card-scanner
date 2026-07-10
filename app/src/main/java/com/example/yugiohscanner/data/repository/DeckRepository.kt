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
/**
 * Estado de una carta en la Forbidden & Limited List (TCG). Cada estado impone un máximo de
 * copias por mazo. `banTcg` viene del catálogo (YGOPRODeck: "Banned"/"Limited"/"Semi-Limited").
 */
enum class EstadoBanlist(val etiqueta: String, val maxCopias: Int) {
    PROHIBIDA("Prohibida", 0),
    LIMITADA("Limitada", 1),
    SEMILIMITADA("Semi-limitada", 2),
    LIBRE("Sin restricción", 3)
}

object ReglasMazo {
    const val PRINCIPAL_MIN = 40
    const val PRINCIPAL_MAX = 60
    const val EXTRA_MAX = 15
    /** Tope general de copias por carta (Sin restricción). Los límites por F&L pueden ser menores. */
    const val MAX_COPIAS = 3

    /** ¿Esta carta pertenece al Deck Extra (no cuenta para el 40–60 del Principal)? */
    fun esExtra(type: String): Boolean {
        val t = type.lowercase()
        return "fusion" in t || "synchro" in t || "xyz" in t || "link" in t
    }

    /** Traduce el campo `banTcg` del catálogo al estado de la ban list. */
    fun estadoBanlist(banTcg: String?): EstadoBanlist = when (banTcg?.trim()?.lowercase()) {
        "banned", "forbidden" -> EstadoBanlist.PROHIBIDA
        "limited" -> EstadoBanlist.LIMITADA
        "semi-limited" -> EstadoBanlist.SEMILIMITADA
        else -> EstadoBanlist.LIBRE
    }

    /** Máximo de copias legales de una carta según su estado F&L (0/1/2/3). */
    fun maxCopias(banTcg: String?): Int = estadoBanlist(banTcg).maxCopias
}

/** Una carta dentro de un mazo, ya lista para pintar: ficha + cuántas pide y cuántas posees. */
data class CartaEnMazo(
    val carta: CartaYuGiOh,
    val cantidad: Int,        // copias en el mazo
    val enColeccion: Int      // copias que el usuario tiene en su colección
) {
    val faltan: Int get() = (cantidad - enColeccion).coerceAtLeast(0)

    /** Estado F&L de la carta (Prohibida/Limitada/Semi/Libre). */
    val estadoBanlist: EstadoBanlist get() = ReglasMazo.estadoBanlist(carta.banTcg)

    /** true si el nº de copias supera el máximo legal de la ban list (mazo no legal). */
    val excedeLimite: Boolean get() = cantidad > estadoBanlist.maxCopias
}

/**
 * Sugerencia de mazo: un arquetipo que el usuario YA colecciona, con cuántas cartas distintas
 * tiene de él, cuántas existen en total y cuántas de ellas son "cartas potentes" (Limitadas/
 * Semi-limitadas por la ban list, buen proxy de fuerza en el meta). Sirve para proponer
 * "completa tu mazo de X" priorizando los arquetipos con núcleo más fuerte.
 */
data class SugerenciaArquetipo(
    val arquetipo: String,
    val poseidas: Int,        // cartas distintas de este arquetipo que el usuario tiene
    val totalCatalogo: Int,   // cartas distintas de este arquetipo que existen en el catálogo
    val potencia: Int = 0     // cartas potentes (Limitadas/Semi) del arquetipo que ya posees
) {
    /** % de cartas del arquetipo que ya posee (0..100). */
    val porcentaje: Int get() = if (totalCatalogo == 0) 0 else (poseidas * 100 / totalCatalogo)

    /** Puntuación meta: cuántas cartas tienes + peso extra por las potentes (staples del meta). */
    val puntuacionMeta: Int get() = poseidas + potencia * 2
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
     * Añade una copia de la carta al mazo respetando las reglas: ban list (Prohibida = 0,
     * Limitada = 1, Semi = 2, resto = 3), Principal ≤ 60 y Extra ≤ 15. Devuelve un texto de
     * error si NO se pudo añadir, o null si se añadió bien.
     */
    suspend fun anadirCarta(deckId: Long, cardId: Long): String? {
        val card = catalogDao.obtenerCartaPorId(cardId) ?: return "Carta no encontrada"
        val estado = ReglasMazo.estadoBanlist(card.banTcg)
        if (estado == EstadoBanlist.PROHIBIDA) return "«${card.nameEn}» está Prohibida (Forbidden)"

        val actual = deckDao.cartaEnMazo(deckId, cardId)
        val copias = actual?.quantity ?: 0
        if (copias >= estado.maxCopias) {
            return "Máximo ${estado.maxCopias} cop. de «${card.nameEn}» (${estado.etiqueta})"
        }

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
        if (delta > 0) {
            val card = catalogDao.obtenerCartaPorId(cardId)
            val estado = ReglasMazo.estadoBanlist(card?.banTcg)
            if (nueva > estado.maxCopias) {
                return "Máximo ${estado.maxCopias} copias (${estado.etiqueta})"
            }
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

        // Cartas poseídas con su ficha (arquetipo + estado F&L), en lotes (SQLite limita el IN a 999).
        val cartasPoseidas = idsPoseidos
            .map { it.toLong() }
            .chunked(900)
            .flatMap { catalogDao.obtenerCartasPorIds(it) }
            .filter { !it.archetype.isNullOrBlank() }
        if (cartasPoseidas.isEmpty()) return emptyList()

        val totales = catalogDao.obtenerConteoArquetipos().associate { it.archetype to it.total }

        // Por arquetipo: cuántas cartas distintas tienes y cuántas son "potentes" (Limitadas/Semi),
        // buen proxy de fuerza en el meta (las cartas que la ban list frena suelen ser las clave).
        return cartasPoseidas
            .groupBy { it.archetype!! }
            .map { (arq, cartas) ->
                val potencia = cartas.count {
                    ReglasMazo.estadoBanlist(it.banTcg) != EstadoBanlist.LIBRE
                }
                SugerenciaArquetipo(arq, cartas.size, totales[arq] ?: cartas.size, potencia)
            }
            .sortedWith(
                compareByDescending<SugerenciaArquetipo> { it.puntuacionMeta }
                    .thenByDescending { it.poseidas }
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

        // Orden "meta": primero las cartas potentes (Limitadas/Semi = clave del arquetipo), luego
        // los monstruos, y por último mágicas/trampas. Así el mazo generado prioriza el núcleo fuerte.
        val ordenadas = seleccion.sortedByDescending { card ->
            var peso = 0
            when (ReglasMazo.estadoBanlist(card.banTcg)) {
                EstadoBanlist.LIMITADA -> peso += 30
                EstadoBanlist.SEMILIMITADA -> peso += 20
                else -> {}
            }
            if (card.type.contains("Monster", ignoreCase = true)) peso += 5
            peso
        }

        val nuevoId = deckDao.crearMazo(
            Deck(name = arquetipo, description = "Mazo sugerido · $arquetipo")
        )
        // Vuelca las cartas respetando la ban list (máx. legal por carta) y los topes de zona
        // (Principal ≤ 60, Extra ≤ 15). Las Prohibidas se saltan (máx. legal = 0).
        var principal = 0
        var extra = 0
        ordenadas.forEach { card ->
            val maxLegal = ReglasMazo.maxCopias(card.banTcg)
            if (maxLegal == 0) return@forEach // Prohibida: no entra
            val deseadas =
                if (soloPoseidas) (poseidasPorId[card.id.toInt()] ?: 1).coerceAtMost(maxLegal)
                else maxLegal.coerceAtMost(1) // "todas": 1 copia de cada para caber más variedad
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
