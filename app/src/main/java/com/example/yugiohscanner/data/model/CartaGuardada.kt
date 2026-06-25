package com.example.yugiohscanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cartas_guardadas")
data class CartaGuardada(
    // Clave local autogenerada: permite guardar varias copias de la misma carta.
    @PrimaryKey(autoGenerate = true) val idLocal: Int = 0,
    val cardId: Int,
    val nombre: String,
    val nombreEs: String? = null,
    val tipo: String,
    val descripcion: String,
    val ataque: Int?,
    val defensa: Int?,
    val urlImagen: String,
    val setNombre: String,
    val setCodigo: String,
    // Estado físico de la carta (null = sin especificar). Lo elige el usuario al guardarla.
    val condicion: String? = null,
    // Rareza de la IMPRESIÓN concreta que posee el usuario (null = sin especificar).
    val rareza: String? = null,
    // Marcada como favorita por el usuario. Se aplica a todas las copias de la misma carta.
    val favorito: Boolean = false,
    // Arte elegido por el usuario (id del arte en YGOPRODeck). null = arte por defecto de la carta.
    // Permite guardar copias de la misma carta con ilustraciones distintas.
    val chosenArtId: Long? = null,
    val fechaGuardado: Long = System.currentTimeMillis()
) {
    /**
     * Reconstruye una CartaYuGiOh con lo que hay guardado localmente. Se usa como
     * respaldo para la pantalla de detalle cuando no hay conexión con el backend.
     * Los campos que no se guardan (nivel, atributo...) quedan vacíos.
     */
    fun aCarta(): CartaYuGiOh = CartaYuGiOh(
        id = cardId,
        name = nombre,
        nombreEs = nombreEs,
        type = tipo,
        desc = descripcion,
        atk = ataque,
        def = defensa,
        level = null,
        race = "",
        attribute = null,
        imagenes = listOf(ImagenCarta(id = cardId, urlImagen = urlImagen, urlImagenPequena = urlImagen)),
        sets = if (setNombre.isNotBlank() && setNombre != "Sin set") {
            listOf(SetCarta(nombre = setNombre, codigo = setCodigo))
        } else {
            null
        }
    )

    companion object {
        fun desde(
            carta: CartaYuGiOh,
            condicion: String? = null,
            rareza: String? = null,
            chosenArtId: Long? = null,
            urlImagenArte: String? = null
        ): CartaGuardada {
            // Set principal = el primero que devuelve la API (normalmente la edición original).
            val setPrincipal = carta.sets?.firstOrNull()
            return CartaGuardada(
                cardId = carta.id,
                nombre = carta.name,
                nombreEs = carta.nombreEs,
                tipo = carta.type,
                descripcion = carta.desc,
                ataque = carta.atk,
                defensa = carta.def,
                // Si el usuario eligió un arte concreto, se guarda su imagen; si no, la principal.
                urlImagen = urlImagenArte ?: carta.imagenes.firstOrNull()?.urlImagenPequena ?: "",
                setNombre = setPrincipal?.nombre ?: "Sin set",
                setCodigo = setPrincipal?.codigo ?: "",
                condicion = condicion,
                rareza = rareza,
                chosenArtId = chosenArtId
            )
        }
    }
}
