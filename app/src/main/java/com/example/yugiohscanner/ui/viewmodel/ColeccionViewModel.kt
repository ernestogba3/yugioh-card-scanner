package com.example.yugiohscanner.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yugiohscanner.data.db.AppDatabase
import com.example.yugiohscanner.data.model.CartaGuardada
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.data.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la pantalla de detalle abierta desde la colección. */
sealed class EstadoDetalle {
    object Inactivo : EstadoDetalle()
    object Cargando : EstadoDetalle()
    // parcial = true cuando los datos vienen del respaldo local (sin conexión).
    data class Exito(val carta: CartaYuGiOh, val parcial: Boolean = false) : EstadoDetalle()
}

/** Resumen de la colección para la sección de estadísticas. */
data class Estadisticas(
    val total: Int = 0,
    val distintas: Int = 0,
    val porTipo: List<Pair<String, Int>> = emptyList(),
    val porArquetipo: List<Pair<String, Int>> = emptyList()
)

/** Una carta dentro del álbum de un set: indica si el usuario la posee o no. */
data class CartaAlbum(
    val cardId: Int,
    val nombre: String,
    val urlImagen: String,
    val poseida: Boolean
)

/** Una "caja" (set) de la colección: miniatura + poseídas/total para el % (mismo criterio que el álbum). */
data class CajaSet(
    val setName: String,
    val poseidas: Int,
    val total: Int?,
    val imagen: String
)

/** Estado del álbum de un set (todas sus cartas, en color las que tienes y en gris las que no). */
sealed class EstadoAlbum {
    object Inactivo : EstadoAlbum()
    object Cargando : EstadoAlbum()
    data class Exito(
        val setName: String,
        val cartas: List<CartaAlbum>,
        val poseidas: Int,
        val total: Int
    ) : EstadoAlbum()
}

class ColeccionViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).cartaDao()
    private val repo = CardRepository(application)

    val cartas: StateFlow<List<CartaGuardada>> = dao.obtenerTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** cardIds marcados como favoritos (al menos una copia favorita). */
    val favoritos: StateFlow<Set<Int>> = dao.obtenerTodas()
        .map { lista -> lista.filter { it.favorito }.map { it.cardId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Estadísticas de la colección, recalculadas cada vez que cambia la lista. */
    val estadisticas: StateFlow<Estadisticas> = dao.obtenerTodas()
        .map { calcularEstadisticas(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Estadisticas())

    /** Cajas (sets) de la colección con % calculado desde el catálogo (card_prints). */
    val cajas: StateFlow<List<CajaSet>> = cartas
        .map { calcularCajas(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Mapa nombre de set -> número total de cartas de ese set (para los porcentajes).
    private val _totalesPorSet = MutableStateFlow<Map<String, Int>>(emptyMap())
    val totalesPorSet: StateFlow<Map<String, Int>> = _totalesPorSet

    // Ficha completa que se está mostrando (se pide al backend al tocar una carta).
    private val _detalle = MutableStateFlow<EstadoDetalle>(EstadoDetalle.Inactivo)
    val detalle: StateFlow<EstadoDetalle> = _detalle

    // Álbum del set abierto (todas sus cartas, marcando cuáles posee el usuario).
    private val _album = MutableStateFlow<EstadoAlbum>(EstadoAlbum.Inactivo)
    val album: StateFlow<EstadoAlbum> = _album

    init {
        cargarTotalesDeSets()
    }

    /**
     * Saca del catálogo local la ficha completa de la carta (efecto, sets, nivel...).
     * Si por lo que sea no estuviera en el catálogo, muestra como respaldo lo guardado.
     */
    fun abrirDetalle(guardada: CartaGuardada) {
        viewModelScope.launch {
            _detalle.value = EstadoDetalle.Cargando
            try {
                val carta = repo.obtenerCartaPorId(guardada.cardId)
                _detalle.value = if (carta != null) {
                    EstadoDetalle.Exito(carta)
                } else {
                    EstadoDetalle.Exito(guardada.aCarta(), parcial = true)
                }
            } catch (e: Exception) {
                _detalle.value = EstadoDetalle.Exito(guardada.aCarta(), parcial = true)
            }
        }
    }

    fun cerrarDetalle() {
        _detalle.value = EstadoDetalle.Inactivo
    }

    /** Abre el detalle de una carta a partir de su id de catálogo (p.ej. desde el álbum). */
    fun abrirDetallePorId(cardId: Int) {
        viewModelScope.launch {
            _detalle.value = EstadoDetalle.Cargando
            try {
                val carta = repo.obtenerCartaPorId(cardId)
                _detalle.value = if (carta != null) EstadoDetalle.Exito(carta) else EstadoDetalle.Inactivo
            } catch (e: Exception) {
                _detalle.value = EstadoDetalle.Inactivo
            }
        }
    }

    /**
     * Carga el álbum de un set: todas sus cartas del catálogo, marcando en color las que el
     * usuario posee (por cardId) y dejando en gris las que le faltan.
     */
    fun abrirAlbumSet(setName: String) {
        viewModelScope.launch {
            _album.value = EstadoAlbum.Cargando
            try {
                val poseidos = cartas.value.map { it.cardId }.toSet()
                val cartasSet = repo.obtenerCartasDeSet(setName).map { card ->
                    CartaAlbum(
                        cardId = card.id.toInt(),
                        nombre = card.nameEs?.takeIf { it.isNotBlank() } ?: card.nameEn,
                        urlImagen = card.imageUrlSmall ?: card.imageUrl,
                        poseida = card.id.toInt() in poseidos
                    )
                }
                // Las que tienes primero (en color), luego las que faltan (en gris).
                val ordenadas = cartasSet.sortedByDescending { it.poseida }
                _album.value = EstadoAlbum.Exito(
                    setName = setName,
                    cartas = ordenadas,
                    poseidas = cartasSet.count { it.poseida },
                    total = cartasSet.size
                )
            } catch (e: Exception) {
                _album.value = EstadoAlbum.Inactivo
            }
        }
    }

    fun cerrarAlbum() {
        _album.value = EstadoAlbum.Inactivo
    }

    private fun cargarTotalesDeSets() {
        viewModelScope.launch {
            try {
                _totalesPorSet.value = repo.obtenerTotalesPorSet()
            } catch (e: Exception) {
                // Si el catálogo aún no está importado, los porcentajes no estarán disponibles.
            }
        }
    }

    fun guardarCarta(
        carta: CartaYuGiOh,
        condicion: String? = null,
        rareza: String? = null,
        chosenArtId: Long? = null,
        urlArte: String? = null
    ) {
        viewModelScope.launch {
            dao.insertar(CartaGuardada.desde(carta, condicion, rareza, chosenArtId, urlArte))
        }
    }

    /** Construye la lista de cajas con poseídas/total calculados desde card_prints. */
    private suspend fun calcularCajas(lista: List<CartaGuardada>): List<CajaSet> {
        if (lista.isEmpty()) return emptyList()
        val poseidos = lista.map { it.cardId }.toSet()
        return lista.groupBy { it.setNombre }.map { (setName, cartasSet) ->
            val idsDelSet = try {
                repo.obtenerCardIdsDeSet(setName)
            } catch (e: Exception) {
                emptyList()
            }
            val total = idsDelSet.size
            // Mismo criterio que el álbum: cuántas cartas del set posee el usuario (por cardId).
            val poseidas = if (total > 0) {
                idsDelSet.count { it.toInt() in poseidos }
            } else {
                cartasSet.distinctBy { it.cardId }.size
            }
            CajaSet(
                setName = setName,
                poseidas = poseidas,
                total = total.takeIf { it > 0 },
                imagen = cartasSet.first().urlImagen
            )
        }.sortedByDescending { it.poseidas }
    }

    fun eliminarCarta(carta: CartaGuardada) {
        viewModelScope.launch {
            dao.eliminar(carta)
        }
    }

    /** Alterna el favorito de una carta (afecta a todas sus copias). */
    fun toggleFavorito(cardId: Int) {
        viewModelScope.launch {
            val eraFavorito = cartas.value.any { it.cardId == cardId && it.favorito }
            dao.marcarFavorito(cardId, !eraFavorito)
        }
    }

    private suspend fun calcularEstadisticas(lista: List<CartaGuardada>): Estadisticas {
        if (lista.isEmpty()) return Estadisticas()
        val distintas = lista.distinctBy { it.cardId }
        val porTipo = lista.groupingBy { it.tipo }.eachCount()
            .toList().sortedByDescending { it.second }
        // El arquetipo no se guarda en la colección: se consulta al catálogo local.
        val arqPorId = repo.arquetiposPorId(distintas.map { it.cardId })
        val porArquetipo = distintas
            .mapNotNull { arqPorId[it.cardId]?.takeIf { a -> a.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
            .take(6)
        return Estadisticas(
            total = lista.size,
            distintas = distintas.size,
            porTipo = porTipo,
            porArquetipo = porArquetipo
        )
    }
}
