package com.example.yugiohscanner.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.data.model.Deck
import com.example.yugiohscanner.data.repository.CardRepository
import com.example.yugiohscanner.data.repository.CartaArquetipo
import com.example.yugiohscanner.data.repository.CartaEnMazo
import com.example.yugiohscanner.data.repository.DeckRepository
import com.example.yugiohscanner.data.repository.ReglasMazo
import com.example.yugiohscanner.data.repository.SugerenciaArquetipo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Resumen de un mazo: totales, reparto por categoría y por zona (Principal/Extra). */
data class EstadisticasMazo(
    val total: Int = 0,
    val distintas: Int = 0,
    val monstruos: Int = 0,
    val magicas: Int = 0,
    val trampas: Int = 0,
    val faltan: Int = 0,
    val principal: Int = 0,    // cartas del Deck Principal (debe estar entre 40 y 60)
    val extra: Int = 0         // cartas del Deck Extra (máx. 15)
) {
    /** El Deck Principal cumple la regla 40–60. */
    val principalValido: Boolean
        get() = principal in ReglasMazo.PRINCIPAL_MIN..ReglasMazo.PRINCIPAL_MAX
    /** El Deck Extra cumple la regla (≤ 15). */
    val extraValido: Boolean get() = extra <= ReglasMazo.EXTRA_MAX
}

class DeckViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DeckRepository(application)
    private val cardRepo = CardRepository(application)

    /** Lista de mazos (reactiva: se actualiza sola al crear/borrar). */
    val mazos: StateFlow<List<Deck>> = repo.mazos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Detalle del mazo abierto ---
    private var mazoActualId: Long? = null

    private val _detalle = MutableStateFlow<List<CartaEnMazo>>(emptyList())
    val detalle: StateFlow<List<CartaEnMazo>> = _detalle

    /** Estadísticas del mazo abierto, recalculadas cuando cambia su contenido. */
    val estadisticas: StateFlow<EstadisticasMazo> = _detalle
        .map { calcularEstadisticas(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EstadisticasMazo())

    // --- Buscador para añadir cartas al mazo ---
    private val _resultados = MutableStateFlow<List<CartaYuGiOh>>(emptyList())
    val resultados: StateFlow<List<CartaYuGiOh>> = _resultados

    /** Aviso puntual (p. ej. "Deck Principal lleno"); la UI lo muestra y luego lo limpia. */
    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje
    fun limpiarMensaje() { _mensaje.value = null }

    /** Id del mazo recién creado desde una sugerencia, para que la pantalla lo abra. */
    private val _mazoCreadoId = MutableStateFlow<Long?>(null)
    val mazoCreadoId: StateFlow<Long?> = _mazoCreadoId
    fun consumirMazoCreado() { _mazoCreadoId.value = null }

    // --- Sugerencias de mazos por arquetipo (Fase 4) ---
    private val _sugerencias = MutableStateFlow<List<SugerenciaArquetipo>>(emptyList())
    val sugerencias: StateFlow<List<SugerenciaArquetipo>> = _sugerencias

    /** Cartas del arquetipo que se está previsualizando (null = ninguno abierto). */
    private val _arquetipoCartas = MutableStateFlow<List<CartaArquetipo>?>(null)
    val arquetipoCartas: StateFlow<List<CartaArquetipo>?> = _arquetipoCartas

    /** Recalcula las sugerencias a partir de la colección actual. */
    fun cargarSugerencias() {
        viewModelScope.launch { _sugerencias.value = repo.sugerenciasArquetipos() }
    }

    /** Carga las cartas de un arquetipo para previsualizarlo antes de crear el mazo. */
    fun abrirArquetipo(arquetipo: String) {
        _arquetipoCartas.value = null
        viewModelScope.launch { _arquetipoCartas.value = repo.cartasDeArquetipo(arquetipo) }
    }

    fun cerrarArquetipo() {
        _arquetipoCartas.value = null
    }

    /** Crea un mazo a partir del arquetipo (solo las que tienes, o todas) y lo deja listo para abrir. */
    fun crearMazoDesdeArquetipo(arquetipo: String, soloPoseidas: Boolean) {
        viewModelScope.launch {
            val id = repo.crearMazoDesdeArquetipo(arquetipo, soloPoseidas)
            _arquetipoCartas.value = null
            _mazoCreadoId.value = id
        }
    }

    fun crearMazo(nombre: String, descripcion: String?) {
        if (nombre.isBlank()) return
        viewModelScope.launch { repo.crearMazo(nombre, descripcion) }
    }

    fun eliminarMazo(deck: Deck) {
        viewModelScope.launch { repo.eliminarMazo(deck) }
    }

    fun renombrarMazo(deckId: Long, nombre: String, descripcion: String?) {
        if (nombre.isBlank()) return
        viewModelScope.launch { repo.renombrarMazo(deckId, nombre, descripcion) }
    }

    fun duplicarMazo(deckId: Long) {
        viewModelScope.launch { repo.duplicarMazo(deckId) }
    }

    /** Texto plano del mazo (para compartir): "Nx Nombre" por carta. */
    fun textoExportable(nombreMazo: String): String {
        val cartas = _detalle.value
        return buildString {
            appendLine("Mazo: $nombreMazo")
            appendLine("Total: ${cartas.sumOf { it.cantidad }} cartas")
            appendLine()
            cartas.forEach { c ->
                appendLine("${c.cantidad}x ${c.carta.nombreEs?.takeIf { it.isNotBlank() } ?: c.carta.name}")
            }
        }.trimEnd()
    }

    private fun calcularEstadisticas(detalle: List<CartaEnMazo>): EstadisticasMazo {
        var monstruos = 0
        var magicas = 0
        var trampas = 0
        var principal = 0
        var extra = 0
        detalle.forEach { c ->
            val t = c.carta.type
            when {
                t.contains("Spell", ignoreCase = true) -> magicas += c.cantidad
                t.contains("Trap", ignoreCase = true) -> trampas += c.cantidad
                else -> monstruos += c.cantidad
            }
            if (ReglasMazo.esExtra(t)) extra += c.cantidad else principal += c.cantidad
        }
        return EstadisticasMazo(
            total = detalle.sumOf { it.cantidad },
            distintas = detalle.size,
            monstruos = monstruos,
            magicas = magicas,
            trampas = trampas,
            faltan = detalle.sumOf { it.faltan },
            principal = principal,
            extra = extra
        )
    }

    fun abrirMazo(deckId: Long) {
        mazoActualId = deckId
        _resultados.value = emptyList()
        recargarDetalle()
    }

    fun anadirCarta(carta: CartaYuGiOh) {
        val id = mazoActualId ?: return
        viewModelScope.launch {
            repo.anadirCarta(id, carta.id.toLong())?.let { _mensaje.value = it }
            recargarDetalle()
        }
    }

    fun cambiarCantidad(carta: CartaYuGiOh, delta: Int) {
        val id = mazoActualId ?: return
        viewModelScope.launch {
            repo.cambiarCantidad(id, carta.id.toLong(), delta)?.let { _mensaje.value = it }
            recargarDetalle()
        }
    }

    fun quitarCarta(carta: CartaYuGiOh) {
        val id = mazoActualId ?: return
        viewModelScope.launch {
            repo.quitarCarta(id, carta.id.toLong())
            recargarDetalle()
        }
    }

    fun buscarParaAnadir(nombre: String) {
        if (nombre.isBlank()) {
            _resultados.value = emptyList()
            return
        }
        viewModelScope.launch {
            _resultados.value = cardRepo.buscarPorNombre(nombre)
        }
    }

    fun limpiarBusqueda() {
        _resultados.value = emptyList()
    }

    private fun recargarDetalle() {
        val id = mazoActualId ?: return
        viewModelScope.launch {
            _detalle.value = repo.detalleMazo(id)
        }
    }
}
