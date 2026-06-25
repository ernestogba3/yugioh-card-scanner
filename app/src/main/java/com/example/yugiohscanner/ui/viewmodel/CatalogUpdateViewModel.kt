package com.example.yugiohscanner.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yugiohscanner.data.repository.CatalogUpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Pantalla de "Actualizar catálogo" (Ajustes). Orquesta la comprobación y la descarga del
 * catálogo nuevo a través de [CatalogUpdateRepository].
 */
class CatalogUpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = CatalogUpdateRepository(application)

    /** Estados de la UI de actualización del catálogo. */
    sealed interface Estado {
        data object Inactivo : Estado
        data object Comprobando : Estado
        data object AlDia : Estado
        data class Disponible(val version: String, val cartas: Int, val url: String) : Estado
        data object Descargando : Estado
        data class Aplicada(val cartas: Int) : Estado
        data class Error(val motivo: String) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Inactivo)
    val estado: StateFlow<Estado> = _estado

    /** Nº de cartas en el catálogo local (se muestra como referencia). */
    private val _cartasLocales = MutableStateFlow(0)
    val cartasLocales: StateFlow<Int> = _cartasLocales

    val versionLocal: String get() = repo.versionLocal

    init {
        viewModelScope.launch { _cartasLocales.value = repo.cartasLocales() }
    }

    fun comprobar() {
        _estado.value = Estado.Comprobando
        viewModelScope.launch {
            _estado.value = when (val r = repo.comprobar()) {
                is CatalogUpdateRepository.Estado.AlDia -> Estado.AlDia
                is CatalogUpdateRepository.Estado.HayActualizacion ->
                    Estado.Disponible(r.version, r.cartas, r.url)
                is CatalogUpdateRepository.Estado.Error -> Estado.Error(r.motivo)
                else -> Estado.Inactivo
            }
        }
    }

    fun descargar(version: String, url: String) {
        _estado.value = Estado.Descargando
        viewModelScope.launch {
            _estado.value = when (val r = repo.aplicar(version, url)) {
                is CatalogUpdateRepository.Estado.Aplicada -> {
                    _cartasLocales.value = r.cartas
                    Estado.Aplicada(r.cartas)
                }
                is CatalogUpdateRepository.Estado.Error -> Estado.Error(r.motivo)
                else -> Estado.Inactivo
            }
        }
    }
}
