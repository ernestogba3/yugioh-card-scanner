package com.example.yugiohscanner.data.search

import java.text.Normalizer

/**
 * Normalización de texto para comparar nombres de cartas con tolerancia a erratas del OCR.
 *
 * Pasa todo a una forma canónica: minúsculas, sin acentos ni "ñ" (se quedan en n), sin signos
 * de puntuación y con los espacios colapsados. Así "Dragón Blanco de Ojos Azules" y
 * "dragon blanco de ojos azules" se comparan igual.
 */
object TextoUtil {

    private val DIACRITICOS = Regex("\\p{Mn}+")     // marcas de acento tras descomponer (NFD)
    private val NO_ALFANUM = Regex("[^a-z0-9 ]")    // todo lo que no sea letra/dígito/espacio
    private val ESPACIOS = Regex("\\s+")

    fun normalizar(texto: String): String {
        val sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replace(DIACRITICOS, "")
        return sinAcentos
            .lowercase()
            .replace(NO_ALFANUM, " ")
            .replace(ESPACIOS, " ")
            .trim()
    }
}
