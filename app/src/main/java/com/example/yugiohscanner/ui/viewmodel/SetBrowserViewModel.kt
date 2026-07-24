package com.example.yugiohscanner.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yugiohscanner.data.db.AppDatabase
import com.example.yugiohscanner.data.repository.CardRepository
import com.example.yugiohscanner.data.search.TextoUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Espera (ms) tras dejar de teclear antes de lanzar la búsqueda de sets. */
private const val DEBOUNCE_MS = 300L

/**
 * Un set del catálogo listado en el navegador "Por set". Si apareció porque contiene una carta
 * buscada (no por el nombre del set), [coincidenciaEs]/[coincidenciaEn] llevan esa carta en
 * español e inglés para mostrarla como pista bajo el set.
 */
data class SetCatalogo(
    val setName: String,
    val setCode: String?,
    val total: Int,
    val coincidenciaEs: String? = null,
    val coincidenciaEn: String? = null
)

/**
 * Lógica PURA del navegador de sets (sin Android), extraída para poder testearla en la JVM.
 */
object SetBrowserLogica {
    /**
     * Filtra los sets por [query] (nombre o código, sin distinguir mayúsculas). Con la consulta
     * vacía (o solo espacios) devuelve todos.
     */
    fun filtrar(sets: List<SetCatalogo>, query: String): List<SetCatalogo> {
        val q = query.trim()
        if (q.isEmpty()) return sets
        return sets.filter {
            it.setName.contains(q, ignoreCase = true) ||
                (it.setCode?.contains(q, ignoreCase = true) == true)
        }
    }
}

/**
 * ViewModel del navegador "Por set" (flujo para AÑADIR cartas por producto cerrado, sobre todo
 * Structure Decks). Está separado del [ColeccionViewModel] a propósito: su álbum es de ESCRITURA
 * (deja añadir cartas) y no debe mezclarse con el álbum de solo lectura de "Mis sets".
 *
 * Todo es local (catálogo Room), sin red, en línea con el rediseño offline-first.
 */
class SetBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = CardRepository(application)
    private val cartaDao = AppDatabase.getInstance(application).cartaDao()

    // Catálogo completo de sets (se carga una vez) y el texto de búsqueda que lo filtra.
    private val _todosLosSets = MutableStateFlow<List<SetCatalogo>>(emptyList())
    private val _busqueda = MutableStateFlow("")
    val busqueda: StateFlow<String> = _busqueda

    /**
     * Sets visibles. Primero las coincidencias directas por nombre/código del set (rápido, en
     * memoria); después, los sets que contienen una carta cuyo nombre (ES o EN) coincide con la
     * búsqueda (consulta al catálogo). Así se puede buscar un set escribiendo una carta en español.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val setsFiltrados: StateFlow<List<SetCatalogo>> =
        // debounce: espera a que el usuario deje de teclear ~300 ms antes de buscar, para no
        // lanzar la consulta al catálogo en cada pulsación. mapLatest cancela la búsqueda anterior.
        combine(_todosLosSets, _busqueda.debounce(DEBOUNCE_MS)) { sets, query -> sets to query }
            .mapLatest { (sets, query) -> buscarSets(sets, query) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private suspend fun buscarSets(sets: List<SetCatalogo>, query: String): List<SetCatalogo> {
        val q = query.trim()
        if (q.isEmpty()) return sets
        // Coincidencias directas por nombre/código del set (conservan el orden alfabético).
        val directos = SetBrowserLogica.filtrar(sets, q)
        val yaIncluidos = directos.mapTo(mutableSetOf()) { it.setName }
        // Sets que contienen una carta que coincide (permite buscar en español); se anota la carta.
        val porNombre = sets.associateBy { it.setName }
        val coincidencias = try {
            repo.buscarCoincidenciasEnSets(q)
        } catch (e: Exception) {
            emptyList()
        }
        val extra = coincidencias.mapNotNull { m ->
            if (m.setName in yaIncluidos) return@mapNotNull null
            porNombre[m.setName]?.copy(coincidenciaEs = m.nameEs, coincidenciaEn = m.nameEn)
        }
        return directos + extra
    }

    // Álbum del set abierto (todas sus cartas; marca en color las que ya tienes).
    private val _album = MutableStateFlow<EstadoAlbum>(EstadoAlbum.Inactivo)
    val album: StateFlow<EstadoAlbum> = _album

    // Mensaje de confirmación tras añadir (null = nada que mostrar).
    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje

    init {
        cargarSets()
    }

    private fun cargarSets() {
        viewModelScope.launch {
            try {
                _todosLosSets.value = repo.obtenerSetsCatalogo().map {
                    SetCatalogo(setName = it.setName, setCode = it.setCode, total = it.numOfCards)
                }
            } catch (e: Exception) {
                // Si el catálogo aún no está importado, la lista queda vacía.
            }
        }
    }

    fun buscar(texto: String) {
        _busqueda.value = texto
    }

    /**
     * Abre el álbum de un set: todas sus cartas del catálogo, marcando en color las que el
     * usuario ya posee (por cardId). A diferencia de "Mis sets", aquí el álbum sirve para AÑADIR.
     */
    fun abrirAlbum(setName: String) {
        viewModelScope.launch {
            _album.value = EstadoAlbum.Cargando
            try {
                val poseidos = cartaDao.obtenerCardIds().toSet()
                val cartasSet = repo.obtenerCartasDeSet(setName).map { card ->
                    val nombre = card.nameEs?.takeIf { it.isNotBlank() } ?: card.nameEn
                    CartaAlbum(
                        cardId = card.id.toInt(),
                        nombre = TextoUtil.decodificarHtml(nombre),
                        urlImagen = card.imageUrlSmall ?: card.imageUrl,
                        poseida = card.id.toInt() in poseidos
                    )
                }.sortedBy { it.nombre }
                _album.value = EstadoAlbum.Exito(
                    setName = setName,
                    cartas = cartasSet,
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

    /**
     * Añade a la colección las cartas indicadas del set (una copia de cada una). Es el motor tanto
     * de "Añadir todo el set" (cardIds = todas) como de la selección parcial. Según la decisión de
     * producto, añade una copia AUNQUE ya tengas la carta (el modelo permite copias repetidas).
     */
    fun anadirCartas(setName: String, cardIds: Set<Int>) {
        if (cardIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val guardables = repo.cartasGuardablesDeSet(setName)
                    .filter { it.cardId in cardIds }
                if (guardables.isEmpty()) return@launch
                cartaDao.insertarTodas(guardables)
                _mensaje.value = "✓ Añadidas ${guardables.size} carta(s) de \"$setName\""
            } catch (e: Exception) {
                _mensaje.value = "No se pudieron añadir las cartas: ${e.message}"
            }
        }
    }

    fun descartarMensaje() {
        _mensaje.value = null
    }
}
