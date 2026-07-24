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

    private val ENTIDAD_NUMERICA = Regex("&#(x?[0-9a-fA-F]+);")

    /**
     * Decodifica las entidades HTML más comunes que llegan en los nombres del catálogo
     * (YGOPRODeck los devuelve escapados: "5D&apos;s" -> "5D's", "Att&amp;ck" -> "Att&ck").
     * Cubre las entidades con nombre habituales y las numéricas (decimales y hexadecimales).
     * `&amp;` se resuelve al final para no re-decodificar una doble codificación.
     */
    fun decodificarHtml(texto: String): String {
        if (texto.indexOf('&') < 0) return texto  // atajo: sin '&' no hay nada que decodificar
        var r = texto
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        r = ENTIDAD_NUMERICA.replace(r) { m ->
            val cod = m.groupValues[1]
            val n = if (cod.startsWith("x") || cod.startsWith("X")) {
                cod.substring(1).toIntOrNull(16)
            } else {
                cod.toIntOrNull()
            }
            n?.toChar()?.toString() ?: m.value
        }
        return r.replace("&amp;", "&")
    }
}
