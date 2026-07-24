package com.example.yugiohscanner.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yugiohscanner.data.db.AppDatabase
import com.example.yugiohscanner.data.model.CartaGuardada
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.data.model.ValorSnapshot
import com.example.yugiohscanner.data.repository.CardRepository
import com.example.yugiohscanner.data.search.TextoUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Estado de la pantalla de detalle abierta desde la colección. */
sealed class EstadoDetalle {
    object Inactivo : EstadoDetalle()
    object Cargando : EstadoDetalle()
    // parcial = true cuando los datos vienen del respaldo local (sin conexión).
    data class Exito(val carta: CartaYuGiOh, val parcial: Boolean = false) : EstadoDetalle()
}

/**
 * Valor de la colección para el widget: total actual y, si hay histórico suficiente, cuánto ha
 * cambiado respecto a ~7 días atrás. [cambioSemana]/[porcentaje] son null si aún no hay un dato
 * anterior con el que comparar.
 */
data class ResumenValor(
    val total: Double = 0.0,
    val cambioSemana: Double? = null,
    val porcentaje: Double? = null
)

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

/**
 * Aviso (Toast) que se muestra tras guardar una carta desde el escáner o el detalle.
 * Lo observa [MainScreen] para pintarlo flotando sobre cualquier pantalla.
 */
data class EventoToast(
    val idLocal: Int,        // fila recién insertada (para "Deshacer")
    val nombre: String,      // nombre a mostrar (ES si existe)
    val subtitulo: String,   // rareza o tipo de la carta
    val urlImagen: String,   // miniatura de la carta guardada
    val copias: Int,         // nº de copias que tienes tras guardar
    val esDuplicada: Boolean // true = ya tenías al menos una copia
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
    private val deckDao = AppDatabase.getInstance(application).deckDao()
    private val valorDao = AppDatabase.getInstance(application).valorHistoricoDao()
    private val repo = CardRepository(application)

    val cartas: StateFlow<List<CartaGuardada>> = dao.obtenerTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** cardIds que el usuario usa en algún mazo (para el chip "EN UN MAZO" de la colección). */
    val cardIdsEnMazos: StateFlow<Set<Int>> = deckDao.cardIdsEnMazos()
        .map { ids -> ids.map(Long::toInt).toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * Valor total de la colección en euros (precio medio de CardMarket × nº de copias). Como
     * `cartas` tiene una fila por copia, basta sumar el precio de cada fila. Las cartas sin
     * precio conocido en el catálogo cuentan como 0. Cada vez que se recalcula, guarda la foto
     * del día en el histórico (una fila por fecha).
     */
    val valorColeccion: StateFlow<Double> = cartas
        .map { lista -> calcularValorTotal(lista) }
        .onEach { valor -> registrarSnapshotHoy(valor) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Histórico del valor (una foto por día), de la más antigua a la más reciente. */
    val historicoValor: StateFlow<List<ValorSnapshot>> = valorDao.historico()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Valor actual + tendencia respecto a ~7 días atrás (para el widget). */
    val resumenValor: StateFlow<ResumenValor> = combine(valorColeccion, historicoValor) { total, hist ->
        calcularResumen(total, hist)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResumenValor())

    private suspend fun calcularValorTotal(lista: List<CartaGuardada>): Double {
        if (lista.isEmpty()) return 0.0
        val precios = repo.preciosPorId(lista.map { it.cardId })
        return lista.sumOf { precios[it.cardId] ?: 0.0 }
    }

    private suspend fun registrarSnapshotHoy(valor: Double) {
        valorDao.guardar(
            ValorSnapshot(fecha = LocalDate.now().toString(), valorEur = valor, timestamp = System.currentTimeMillis())
        )
    }

    /**
     * Calcula la tendencia comparando el valor de hoy con el de referencia: la foto más reciente
     * de hace 7 días o más; si no la hay, la más antigua de un día anterior. Si solo existe la
     * foto de hoy, no hay tendencia (null).
     */
    private fun calcularResumen(total: Double, hist: List<ValorSnapshot>): ResumenValor {
        if (hist.isEmpty()) return ResumenValor(total)
        val hoy = LocalDate.now()
        val hace7 = hoy.minusDays(7)
        val referencia = hist.filter { LocalDate.parse(it.fecha) <= hace7 }.maxByOrNull { it.fecha }
            ?: hist.firstOrNull { LocalDate.parse(it.fecha) < hoy }
            ?: return ResumenValor(total)
        val cambio = total - referencia.valorEur
        val pct = if (referencia.valorEur != 0.0) cambio / referencia.valorEur * 100 else null
        return ResumenValor(total, cambio, pct)
    }

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

    // Aviso pendiente de mostrar tras guardar una carta (null = no hay aviso visible).
    private val _eventoToast = MutableStateFlow<EventoToast?>(null)
    val eventoToast: StateFlow<EventoToast?> = _eventoToast

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
                    val nombre = card.nameEs?.takeIf { it.isNotBlank() } ?: card.nameEn
                    CartaAlbum(
                        cardId = card.id.toInt(),
                        nombre = TextoUtil.decodificarHtml(nombre),
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
            // Copias previas (consulta directa: no depende de que el Flow esté suscrito).
            val copiasPrevias = dao.contarPorCardId(carta.id)
            val guardada = CartaGuardada.desde(carta, condicion, rareza, chosenArtId, urlArte)
            val idLocal = dao.insertar(guardada)
            _eventoToast.value = EventoToast(
                idLocal = idLocal.toInt(),
                nombre = carta.nombreEs?.takeIf { it.isNotBlank() } ?: carta.name,
                subtitulo = rareza?.takeIf { it.isNotBlank() } ?: carta.type,
                urlImagen = guardada.urlImagen,
                copias = copiasPrevias + 1,
                esDuplicada = copiasPrevias > 0
            )
        }
    }

    /** Oculta el aviso (autocierre a los pocos segundos o al deslizarlo). */
    fun descartarToast() {
        _eventoToast.value = null
    }

    /** Deshace el último guardado: borra la copia recién insertada y oculta el aviso. */
    fun deshacerGuardado(idLocal: Int) {
        viewModelScope.launch {
            dao.eliminarPorIdLocal(idLocal)
            _eventoToast.value = null
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
