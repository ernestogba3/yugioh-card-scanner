package com.example.yugiohscanner.data.remote

import android.content.Context
import com.example.yugiohscanner.data.db.AppDatabase
import com.example.yugiohscanner.data.model.CartaGuardada
import com.example.yugiohscanner.data.model.Deck
import com.example.yugiohscanner.data.model.DeckCard
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Resultado de una restauración: cuántas cartas y mazos se han traído de la nube. */
data class ResultadoRestauracion(val cartas: Int, val mazos: Int)

/**
 * Backup / restore MANUAL de los datos de usuario (colección + mazos) en Firestore.
 * Las cartas del catálogo NUNCA se suben: solo lo que el usuario posee y sus mazos.
 *
 * Estructura: users/{uid}/collection/{idLocal} y users/{uid}/decks/{deckId}.
 * "Subir" reemplaza lo que hubiera en la nube; "bajar" reemplaza lo local por la copia.
 */
class SyncRepository(context: Context) {

    private val appContext = context.applicationContext
    private val cartaDao = AppDatabase.getInstance(appContext).cartaDao()
    private val deckDao = AppDatabase.getInstance(appContext).deckDao()

    private fun configurado() = FirebaseApp.getApps(appContext).isNotEmpty()
    private fun firestore() = FirebaseFirestore.getInstance()

    /** Sube la colección y los mazos locales a users/{uid}. Devuelve cuántas cartas subió. */
    suspend fun subir(uid: String): Result<Int> {
        if (!configurado()) return Result.failure(IllegalStateException("Firebase no configurado"))
        return try {
            val userRef = firestore().collection("users").document(uid)

            // Colección: una copia = un documento (id = idLocal).
            val cartas = cartaDao.obtenerTodasUnaVez()
            val coleccionRef = userRef.collection("collection")
            borrarTodos(coleccionRef)
            for (lote in cartas.chunked(400)) {
                val batch = firestore().batch()
                for (c in lote) {
                    batch.set(
                        coleccionRef.document(c.idLocal.toString()),
                        mapOf(
                            "cardId" to c.cardId,
                            "nombre" to c.nombre,
                            "nombreEs" to c.nombreEs,
                            "tipo" to c.tipo,
                            "descripcion" to c.descripcion,
                            "ataque" to c.ataque,
                            "defensa" to c.defensa,
                            "urlImagen" to c.urlImagen,
                            "setNombre" to c.setNombre,
                            "setCodigo" to c.setCodigo,
                            "condicion" to c.condicion,
                            "rareza" to c.rareza,
                            "favorito" to c.favorito,
                            "chosenArtId" to c.chosenArtId,
                            "fechaGuardado" to c.fechaGuardado
                        )
                    )
                }
                batch.commit().await()
            }

            // Mazos: un documento por mazo, con sus cartas en un array.
            val decksRef = userRef.collection("decks")
            borrarTodos(decksRef)
            for (mazo in deckDao.obtenerMazosUnaVez()) {
                val cartasMazo = deckDao.cartasDeMazo(mazo.id)
                    .map { mapOf("cardId" to it.cardId, "quantity" to it.quantity) }
                decksRef.document(mazo.id.toString()).set(
                    mapOf(
                        "name" to mazo.name,
                        "description" to mazo.description,
                        "updatedAt" to mazo.updatedAt,
                        "cards" to cartasMazo
                    )
                ).await()
            }

            Result.success(cartas.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Trae la copia de la nube y reemplaza la colección y los mazos locales. */
    suspend fun bajar(uid: String): Result<ResultadoRestauracion> {
        if (!configurado()) return Result.failure(IllegalStateException("Firebase no configurado"))
        return try {
            val userRef = firestore().collection("users").document(uid)

            // Colección.
            val coleccionDocs = userRef.collection("collection").get().await()
            val cartas = coleccionDocs.documents.map { doc ->
                CartaGuardada(
                    cardId = (doc.getLong("cardId") ?: 0L).toInt(),
                    nombre = doc.getString("nombre") ?: "",
                    nombreEs = doc.getString("nombreEs"),
                    tipo = doc.getString("tipo") ?: "",
                    descripcion = doc.getString("descripcion") ?: "",
                    ataque = doc.getLong("ataque")?.toInt(),
                    defensa = doc.getLong("defensa")?.toInt(),
                    urlImagen = doc.getString("urlImagen") ?: "",
                    setNombre = doc.getString("setNombre") ?: "",
                    setCodigo = doc.getString("setCodigo") ?: "",
                    condicion = doc.getString("condicion"),
                    rareza = doc.getString("rareza"),
                    favorito = doc.getBoolean("favorito") ?: false,
                    chosenArtId = doc.getLong("chosenArtId"),
                    fechaGuardado = doc.getLong("fechaGuardado") ?: System.currentTimeMillis()
                )
            }
            cartaDao.eliminarTodas()
            cartaDao.insertarTodas(cartas)

            // Mazos.
            val deckDocs = userRef.collection("decks").get().await()
            deckDao.eliminarTodosLosMazos()
            for (doc in deckDocs.documents) {
                val nuevoId = deckDao.crearMazo(
                    Deck(
                        name = doc.getString("name") ?: "Mazo",
                        description = doc.getString("description"),
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                    )
                )
                val listaCartas = (doc.get("cards") as? List<*>).orEmpty()
                for (item in listaCartas) {
                    val m = item as? Map<*, *> ?: continue
                    val cardId = (m["cardId"] as? Number)?.toLong() ?: continue
                    val cantidad = (m["quantity"] as? Number)?.toInt() ?: 1
                    deckDao.guardarCartaEnMazo(DeckCard(nuevoId, cardId, cantidad))
                }
            }

            Result.success(ResultadoRestauracion(cartas.size, deckDocs.size()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Borra todos los documentos de una subcolección (por lotes). */
    private suspend fun borrarTodos(ref: CollectionReference) {
        val docs = ref.get().await()
        for (lote in docs.documents.chunked(400)) {
            val batch = firestore().batch()
            lote.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }
}
