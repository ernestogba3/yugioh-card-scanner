package com.example.yugiohscanner.data.scan

import android.graphics.Bitmap
import android.util.Log
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.data.repository.CardRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Cómo se identificó la carta (para informar al usuario y decidir el respaldo). */
enum class ViaIdentificacion { PASSCODE, PHASH, NINGUNO }

/**
 * Resultado de identificar una carta a partir de un frame de la cámara.
 * Si [carta] es null y [via] es NINGUNO, la pantalla cae al OCR del nombre (respaldo verificado).
 */
data class ResultadoIdentificacion(
    val carta: CartaYuGiOh?,
    val via: ViaIdentificacion,
    val artId: Long? = null,        // arte que coincidió (solo en el fallback visual por pHash)
    val setCode: String? = null,    // edición leída del set code (opcional, para afinar rareza)
    val distanciaHamming: Int? = null,
    // true si OpenCV llegó a detectar una carta (aunque no se identificara). Permite al
    // auto-disparo no lanzar búsquedas falsas cuando no hay ninguna carta en el encuadre.
    val huboCarta: Boolean = true
)

/**
 * Escaneo passcode-first (Fase 2). Sobre la carta ya rectificada por [DetectorCarta], intenta
 * identificarla en cascada:
 *  1) PASSCODE: los 8 dígitos de la esquina inferior-izquierda → id de la carta. Lo más fiable.
 *  2) pHASH: si el passcode no se lee (foils, artes raros), compara el aspecto de la carta con
 *     el catálogo por distancia de Hamming.
 *  3) Si nada funciona, devuelve NINGUNO y la cámara usa el OCR del nombre como último respaldo.
 *
 * Además lee el SET CODE (esquina inferior-derecha) para afinar la edición/rareza.
 */
class IdentificadorCarta(private val repo: CardRepository) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Identifica la carta de [frame] (ya orientado y de tamaño razonable). */
    suspend fun identificar(frame: Bitmap): ResultadoIdentificacion = withContext(Dispatchers.Default) {
        // Rectificado a alta resolución: el passcode son dígitos diminutos.
        val carta = DetectorCarta.detectarYRectificar(frame, DetectorCarta.CARD_W_ALTA)
            ?: return@withContext ResultadoIdentificacion(null, ViaIdentificacion.NINGUNO, huboCarta = false)

        try {
            // Set code (mejor esfuerzo): no identifica, solo afina la edición.
            val setCode = leerSetCode(DetectorCarta.recortarSetCodeDe(carta))

            // 1) PASSCODE
            val passcodes = leerPasscodes(DetectorCarta.recortarPasscodeDe(carta))
            for (pc in passcodes) {
                val encontrada = repo.buscarPorPasscode(pc)
                if (encontrada != null) {
                    Log.d(TAG, "Identificada por passcode $pc")
                    return@withContext ResultadoIdentificacion(
                        encontrada, ViaIdentificacion.PASSCODE, setCode = setCode
                    )
                }
            }

            // 2) pHASH (fallback visual)
            val hash = PHash.calcular(carta)
            val porHash = repo.buscarPorHash(hash)
            if (porHash != null) {
                Log.d(TAG, "Identificada por pHash (dist=${porHash.distancia}, art=${porHash.artId})")
                return@withContext ResultadoIdentificacion(
                    porHash.carta, ViaIdentificacion.PHASH,
                    artId = porHash.artId, setCode = setCode,
                    distanciaHamming = porHash.distancia
                )
            }

            // 3) Sin identificar: la cámara usará el OCR del nombre.
            ResultadoIdentificacion(null, ViaIdentificacion.NINGUNO, setCode = setCode)
        } finally {
            carta.recycle()
        }
    }

    /** OCR del recorte del passcode → lista de candidatos numéricos (7-8 dígitos) a probar. */
    private suspend fun leerPasscodes(recorte: Bitmap): List<Long> {
        val texto = ocr(ampliar(recorte, 4))
        // Los passcodes impresos pueden llevar ceros a la izquierda; toLong() los descarta, que
        // es justo cómo está guardado el id en el catálogo.
        return Regex("\\d{7,8}").findAll(texto)
            .mapNotNull { it.value.toLongOrNull() }
            .distinct()
            .toList()
    }

    /** OCR del recorte del set code → primer código con forma de edición (p. ej. "LOB-EN001"), o null. */
    private suspend fun leerSetCode(recorte: Bitmap): String? {
        val texto = ocr(ampliar(recorte, 4)).uppercase()
        return Regex("[A-Z0-9]{2,6}-[A-Z]{1,3}\\d{2,4}").find(texto)?.value
    }

    /** Pasa un bitmap por ML Kit y devuelve todo el texto reconocido (vacío si falla). */
    private suspend fun ocr(bitmap: Bitmap): String = try {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
    } catch (e: Exception) {
        ""
    }

    /** Amplía un recorte pequeño para que el OCR lea mejor los caracteres diminutos. */
    private fun ampliar(bitmap: Bitmap, factor: Int): Bitmap {
        if (factor <= 1 || bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        return Bitmap.createScaledBitmap(bitmap, bitmap.width * factor, bitmap.height * factor, true)
    }

    companion object {
        private const val TAG = "IdentificadorCarta"
    }
}
