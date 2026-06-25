package com.example.yugiohscanner.data.repository

import android.content.Context
import android.util.Log
import com.example.yugiohscanner.data.catalog.CatalogDatabase
import com.example.yugiohscanner.data.catalog.CatalogImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualización del catálogo desde la nube (Fase 10 del roadmap).
 *
 * La app es offline-first: el catálogo viaja dentro del APK. Pero Konami saca cartas nuevas
 * cada pocas semanas, así que este repositorio permite **refrescar el catálogo local sin
 * republicar la app**:
 *
 *  1. Descarga un `manifest.json` pequeño con la versión más reciente y la URL del catálogo.
 *  2. Si la versión remota es distinta de la local, descarga el `catalog.json` completo.
 *  3. Reconstruye el catálogo en Room ([CatalogImporter.reemplazarCatalogo]).
 *
 * El catálogo lo genera un GitHub Action que tira directamente de la API de YGOPRODeck (ver
 * `backend/src/export-catalog.js` y `.github/workflows/update-catalog.yml`).
 *
 * Todo es OPORTUNISTA: sin internet, o si el servidor falla, la app sigue con el catálogo que
 * ya tenga. No rompe el offline-first.
 */
class CatalogUpdateRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Versión del catálogo local (la del manifest aplicado, o la empaquetada por defecto). */
    val versionLocal: String get() = prefs.getString(KEY_VERSION, VERSION_EMPAQUETADA) ?: VERSION_EMPAQUETADA

    /** Resultado de comprobar/aplicar una actualización. */
    sealed interface Estado {
        data class HayActualizacion(val version: String, val cartas: Int, val url: String) : Estado
        data object AlDia : Estado
        data class Error(val motivo: String) : Estado
        data class Aplicada(val cartas: Int, val version: String) : Estado
    }

    /** Comprueba si hay una versión más nueva. NO descarga el catálogo todavía. */
    suspend fun comprobar(): Estado = withContext(Dispatchers.IO) {
        try {
            val manifestTexto = descargarTexto(MANIFEST_URL)
            val manifest = JSONObject(manifestTexto)
            val version = manifest.getString("version")
            val cartas = manifest.optInt("cards", 0)
            val url = manifest.optString("url").ifBlank { CATALOG_URL_FALLBACK }
            if (version == versionLocal) Estado.AlDia
            else Estado.HayActualizacion(version, cartas, url)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo comprobar la actualización", e)
            Estado.Error("No se pudo conectar. Revisa tu internet o inténtalo más tarde.")
        }
    }

    /** Descarga el catálogo completo de [url] y reconstruye Room. Marca la nueva versión. */
    suspend fun aplicar(version: String, url: String): Estado = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "catalog_descarga.json")
        try {
            descargarArchivo(url, tmp)
            val total = tmp.inputStream().use { CatalogImporter.reemplazarCatalogo(context, it) }
            prefs.edit().putString(KEY_VERSION, version).apply()
            CardRepository.invalidarCaches()
            Estado.Aplicada(total, version)
        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando la actualización del catálogo", e)
            Estado.Error("La descarga falló o el archivo no era válido. El catálogo anterior se conserva.")
        } finally {
            tmp.delete()
        }
    }

    /** Nº de cartas que hay ahora mismo en el catálogo local (para mostrarlo en Ajustes). */
    suspend fun cartasLocales(): Int = withContext(Dispatchers.IO) {
        CatalogDatabase.getInstance(context).catalogDao().contarCartas()
    }

    private fun descargarTexto(urlStr: String): String {
        val conn = abrir(urlStr)
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun descargarArchivo(urlStr: String, destino: File) {
        val conn = abrir(urlStr)
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            conn.inputStream.use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida, 64 * 1024) }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun abrir(urlStr: String): HttpURLConnection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }

    companion object {
        private const val TAG = "CatalogUpdate"
        private const val PREFS = "catalogo"
        private const val KEY_VERSION = "version"

        // Versión que viaja empaquetada en el APK. El backend la escribe en el manifest como
        // "version"; mientras la local sea esta, se ofrece la primera actualización disponible.
        private const val VERSION_EMPAQUETADA = "empaquetada"

        // URL del manifest publicado por el GitHub Action (repo: ernestogba3/yugioh-card-scanner).
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/ernestogba3/yugioh-card-scanner/main/app/src/main/assets/database/manifest.json"

        // Respaldo por si el manifest no trae "url".
        private const val CATALOG_URL_FALLBACK =
            "https://raw.githubusercontent.com/ernestogba3/yugioh-card-scanner/main/app/src/main/assets/database/catalog.json"
    }
}
