package com.example.yugiohscanner.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yugiohscanner.data.catalog.CardArt
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.data.repository.CardRepository
import com.example.yugiohscanner.data.scan.IdentificadorCarta
import com.example.yugiohscanner.data.scan.ResultadoIdentificacion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class EstadoBusqueda {
    object Inactivo : EstadoBusqueda()
    object Cargando : EstadoBusqueda()
    data class Exito(val cartas: List<CartaYuGiOh>) : EstadoBusqueda()
    data class Error(val mensaje: String) : EstadoBusqueda()
}

data class Filtros(
    val tipo: String = "",
    val nivel: String = "",
    val elemento: String = "",
    val raza: String = "",
    val arquetipo: String = "",
    val rangoAtk: String = "",
    val rangoDef: String = "",
    val rareza: String = ""
)

/** Opciones disponibles para los desplegables. Vienen rellenas con los valores que acepta la API. */
data class OpcionesFiltro(
    val tipos: List<String> = TIPOS_CARTA,
    val niveles: List<String> = NIVELES,
    val elementos: List<String> = ATRIBUTOS,
    val razas: List<String> = RAZAS,
    val rarezas: List<String> = RAREZAS
)

/** Rarezas más comunes (deben coincidir con los valores de `card_prints.rarity`). */
val RAREZAS = listOf(
    "Common", "Rare", "Super Rare", "Ultra Rare", "Secret Rare", "Ultimate Rare",
    "Ghost Rare", "Gold Rare", "Starlight Rare", "Collector's Rare",
    "Prismatic Secret Rare", "Quarter Century Secret Rare"
)

/** Razas/tipos de monstruo más comunes (deben coincidir con los valores del catálogo). */
val RAZAS = listOf(
    "Dragon", "Spellcaster", "Warrior", "Beast", "Beast-Warrior", "Machine", "Fiend",
    "Zombie", "Aqua", "Fish", "Sea Serpent", "Reptile", "Dinosaur", "Insect", "Plant",
    "Rock", "Pyro", "Thunder", "Winged Beast", "Fairy", "Psychic", "Wyrm", "Cyberse"
)

/** Tipos de carta más comunes (deben coincidir exactamente con los valores que entiende la API). */
val TIPOS_CARTA = listOf(
    "Effect Monster",
    "Normal Monster",
    "Fusion Monster",
    "Synchro Monster",
    "XYZ Monster",
    "Link Monster",
    "Ritual Monster",
    "Pendulum Effect Monster",
    "Spell Card",
    "Trap Card"
)

/** Niveles/rangos posibles de un monstruo. */
val NIVELES = (1..12).map { it.toString() }

/** Atributos (elementos) de los monstruos según la API. */
val ATRIBUTOS = listOf("DARK", "LIGHT", "EARTH", "WATER", "FIRE", "WIND", "DIVINE")

/** Rangos fijos de ATK/DEF que se ofrecen en los desplegables. */
val RANGOS_ATK_DEF = listOf(
    "0 - 999",
    "1000 - 1999",
    "2000 - 2499",
    "2500 - 2999",
    "3000 - 3999",
    "4000 o más"
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    // Búsqueda 100% local (Room). Ya no se llama a ningún backend.
    private val repo = CardRepository(application)

    // Escaneo passcode-first (Fase 2): identifica la carta por passcode o pHash.
    private val identificador by lazy { IdentificadorCarta(repo) }

    private val _estado = MutableStateFlow<EstadoBusqueda>(EstadoBusqueda.Inactivo)
    val estado: StateFlow<EstadoBusqueda> = _estado

    private val _filtros = MutableStateFlow(Filtros())
    val filtros: StateFlow<Filtros> = _filtros

    // Las opciones de los desplegables ya vienen rellenas desde el principio.
    private val _opciones = MutableStateFlow(OpcionesFiltro())
    val opciones: StateFlow<OpcionesFiltro> = _opciones

    private var cartasActuales: List<CartaYuGiOh> = emptyList()

    /** Búsqueda directa por nombre (cuando sí conoces la carta). Reinicia los filtros. */
    fun buscarCarta(nombre: String) {
        if (nombre.isBlank()) return
        _filtros.value = Filtros()
        viewModelScope.launch {
            _estado.value = EstadoBusqueda.Cargando
            buscar { repo.buscarPorNombre(nombre.trim()) }
        }
    }

    /** Búsqueda combinando el nombre (opcional) con los filtros seleccionados. */
    fun buscarPorFiltros(nombre: String = "") {
        val f = _filtros.value
        val nombreLimpio = nombre.trim().ifBlank { null }
        val tipo = f.tipo.ifBlank { null }
        val nivel = f.nivel.toIntOrNull()
        val atributo = f.elemento.ifBlank { null }
        val raza = f.raza.ifBlank { null }
        val arquetipo = f.arquetipo.trim().ifBlank { null }
        val atkMin = limiteInferior(f.rangoAtk)
        val defMin = limiteInferior(f.rangoDef)
        val rareza = f.rareza.ifBlank { null }

        // Hace falta al menos un criterio para buscar.
        if (nombreLimpio == null && tipo == null && nivel == null && atributo == null &&
            raza == null && arquetipo == null && atkMin == null && defMin == null && rareza == null
        ) {
            _estado.value = EstadoBusqueda.Error("Elige al menos un filtro o escribe un nombre para buscar")
            return
        }

        viewModelScope.launch {
            _estado.value = EstadoBusqueda.Cargando
            buscar { repo.buscarPorFiltros(nombreLimpio, tipo, nivel, atributo, raza, arquetipo, atkMin, defMin, rareza) }
        }
    }

    /**
     * Busca a partir de los textos leídos por el OCR de la cámara. La cámara manda VARIOS
     * candidatos (distintas hipótesis del nombre); aquí se prueban todos contra el catálogo
     * local y se devuelve el mejor ranking. Tolera erratas (Levenshtein/Jaro-Winkler).
     */
    fun buscarDesdeOcr(candidatos: List<String>) {
        val limpios = candidatos.map { it.trim() }.filter { it.isNotEmpty() }
        if (limpios.isEmpty()) return
        _filtros.value = Filtros()
        viewModelScope.launch {
            _estado.value = EstadoBusqueda.Cargando
            buscar { repo.buscarPorVariosNombres(limpios) }
        }
    }

    /**
     * Identifica una carta a partir de un frame de la cámara (passcode → pHash). El resultado
     * vuelve por [onResultado] en el hilo principal, así la pantalla decide: si se identificó,
     * abre el detalle; si no, cae al OCR del nombre.
     */
    fun identificarFrame(frame: Bitmap, onResultado: (ResultadoIdentificacion) -> Unit) {
        viewModelScope.launch {
            val resultado = identificador.identificar(frame)
            onResultado(resultado)
        }
    }

    /** Carga los artes (ilustraciones) de una carta para el selector de arte del detalle. */
    fun cargarArtes(passcode: Long, onCargado: (List<CardArt>) -> Unit) {
        viewModelScope.launch {
            onCargado(repo.obtenerArtesDeCarta(passcode))
        }
    }

    /** Ejecuta una búsqueda en el catálogo local y publica el resultado en el estado. */
    private suspend fun buscar(accion: suspend () -> List<CartaYuGiOh>) {
        try {
            cartasActuales = accion()
            aplicarRangosLocales()
        } catch (e: Exception) {
            _estado.value = EstadoBusqueda.Error("Error al buscar en el catálogo: ${e.message}")
        }
    }

    fun actualizarFiltro(nuevosFiltros: Filtros) {
        _filtros.value = nuevosFiltros
    }

    fun limpiarFiltros() {
        _filtros.value = Filtros()
    }

    /** Afina el resultado aplicando los rangos exactos de ATK/DEF en el cliente. */
    private fun aplicarRangosLocales() {
        if (cartasActuales.isEmpty()) {
            _estado.value = EstadoBusqueda.Error("No se encontraron cartas")
            return
        }
        val f = _filtros.value
        val filtradas = cartasActuales.filter { carta ->
            enRango(carta.atk, f.rangoAtk) && enRango(carta.def, f.rangoDef)
        }
        _estado.value = if (filtradas.isEmpty()) {
            EstadoBusqueda.Error("No hay cartas que coincidan con los filtros de ATK/DEF")
        } else {
            EstadoBusqueda.Exito(filtradas)
        }
    }

    /** Convierte un rango en su límite inferior (entero), que la consulta filtra con >=. */
    private fun limiteInferior(rango: String): Int? = when (rango) {
        "1000 - 1999" -> 1000
        "2000 - 2499" -> 2000
        "2500 - 2999" -> 2500
        "3000 - 3999" -> 3000
        "4000 o más" -> 4000
        else -> null // "0 - 999" o vacío: no filtramos en el servidor
    }

    private fun enRango(valor: Int?, rango: String): Boolean {
        if (rango.isBlank()) return true
        val v = valor ?: 0
        return when (rango) {
            "0 - 999" -> v in 0..999
            "1000 - 1999" -> v in 1000..1999
            "2000 - 2499" -> v in 2000..2499
            "2500 - 2999" -> v in 2500..2999
            "3000 - 3999" -> v in 3000..3999
            "4000 o más" -> v >= 4000
            else -> true
        }
    }
}
