package com.example.yugiohscanner.data.catalog

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/**
 * Carga el catálogo de cartas en Room la PRIMERA vez que se abre la app.
 *
 * Lee `assets/database/catalog.json` en streaming (token a token) e inserta por lotes, para
 * no cargar las ~13.000 cartas en memoria de golpe. Es idempotente: si el catálogo ya está
 * importado (hay cartas en la tabla), no hace nada.
 *
 * El JSON lo genera el script `backend/src/export-catalog.js` (`npm run export-catalog`).
 * Estrategia B del roadmap; antes de publicar se sustituirá por una `.db` prempaquetada.
 */
object CatalogImporter {

    private const val TAG = "CatalogImporter"
    private const val ASSET = "database/catalog.json"
    private const val ASSET_HASHES = "database/phashes.json"
    private const val LOTE = 500

    suspend fun importarSiHaceFalta(context: Context) = withContext(Dispatchers.IO) {
        val db = CatalogDatabase.getInstance(context)
        val dao = db.catalogDao()
        importarCatalogo(context, db, dao)
        importarHashes(context, db, dao)
    }

    private suspend fun importarCatalogo(context: Context, db: CatalogDatabase, dao: CatalogDao) {
        if (dao.contarCartas() > 0) return // ya importado

        try {
            // Transacción única: si la importación se interrumpe (Activity recreada, etc.),
            // Room hace rollback y no queda un catálogo a medias ni prints duplicados.
            db.withTransaction {
                context.assets.open(ASSET).use { input ->
                    parsearCatalogo(input, dao)
                }
            }
            Log.i(TAG, "Catálogo importado: ${dao.contarCartas()} cartas.")
        } catch (e: java.io.FileNotFoundException) {
            // El catálogo aún no se ha generado. La app arranca igual; la búsqueda no
            // devolverá resultados hasta ejecutar `npm run export-catalog` y reinstalar.
            Log.w(TAG, "No se encontró '$ASSET'. Genera el catálogo con: npm run export-catalog")
        } catch (e: Exception) {
            Log.e(TAG, "Error importando el catálogo", e)
        }
    }

    /**
     * Reconstruye TODO el catálogo a partir de un JSON descargado (actualización desde la nube).
     * Borra cards/prints/sets/arts y los reimporta dentro de una sola transacción; si algo falla,
     * Room hace rollback y se conserva el catálogo anterior. Los pHash NO se tocan.
     *
     * Devuelve el nº de cartas resultante. Lanza excepción si el JSON está corrupto (el llamador
     * la captura y avisa al usuario).
     */
    suspend fun reemplazarCatalogo(context: Context, input: java.io.InputStream): Int =
        withContext(Dispatchers.IO) {
            val db = CatalogDatabase.getInstance(context)
            val dao = db.catalogDao()
            db.withTransaction {
                dao.borrarCartas()
                dao.borrarPrints()
                dao.borrarSets()
                dao.borrarArtes()
                parsearCatalogo(input, dao)
            }
            val total = dao.contarCartas()
            Log.i(TAG, "Catálogo actualizado: $total cartas.")
            total
        }

    /** Lee el objeto raíz del catálogo ({ sets:[...], cards:[...] }) e inserta por lotes. */
    private suspend fun parsearCatalogo(input: java.io.InputStream, dao: CatalogDao) {
        JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "sets" -> leerSets(reader, dao)
                    "cards" -> leerCards(reader, dao)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
    }

    /**
     * Importa los pHash (fallback visual del escáner) desde `phashes.json`. Es independiente
     * del catálogo: si aún no has generado los hashes (`npm run phash`), la app funciona igual
     * y simplemente no habrá fallback visual.
     */
    private suspend fun importarHashes(context: Context, db: CatalogDatabase, dao: CatalogDao) {
        if (dao.contarHashes() > 0) return // ya importado

        try {
            db.withTransaction {
                context.assets.open(ASSET_HASHES).use { input ->
                    JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                        leerHashes(reader, dao)
                    }
                }
            }
            Log.i(TAG, "Hashes importados: ${dao.contarHashes()} artes.")
        } catch (e: java.io.FileNotFoundException) {
            Log.w(TAG, "No se encontró '$ASSET_HASHES'. Genera los hashes con: npm run phash")
        } catch (e: Exception) {
            Log.e(TAG, "Error importando los pHash", e)
        }
    }

    private suspend fun leerHashes(reader: JsonReader, dao: CatalogDao) {
        val buffer = ArrayList<CardHash>(LOTE)
        reader.beginArray()
        while (reader.hasNext()) {
            var artId = 0L
            var passcode = 0L
            var pHash: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "artId" -> artId = reader.nextLong()
                    "passcode" -> passcode = reader.nextLong()
                    "pHash" -> pHash = reader.nextStringOrNull()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (artId != 0L && pHash != null) buffer.add(CardHash(artId, passcode, pHash))
            if (buffer.size >= LOTE) {
                dao.insertarHashes(buffer.toList()); buffer.clear()
            }
        }
        reader.endArray()
        if (buffer.isNotEmpty()) dao.insertarHashes(buffer)
    }

    private suspend fun leerSets(reader: JsonReader, dao: CatalogDao) {
        val buffer = ArrayList<CardSet>(LOTE)
        reader.beginArray()
        while (reader.hasNext()) {
            var setName = ""
            var setCode: String? = null
            var num = 0
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "setName" -> setName = reader.nextString()
                    "setCode" -> setCode = reader.nextStringOrNull()
                    "numOfCards" -> num = reader.nextInt()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (setName.isNotBlank()) buffer.add(CardSet(setName, setCode, num))
            if (buffer.size >= LOTE) {
                dao.insertarSets(buffer.toList()); buffer.clear()
            }
        }
        reader.endArray()
        if (buffer.isNotEmpty()) dao.insertarSets(buffer)
    }

    private suspend fun leerCards(reader: JsonReader, dao: CatalogDao) {
        val cards = ArrayList<Card>(LOTE)
        val prints = ArrayList<CardPrint>(LOTE * 2)
        val arts = ArrayList<CardArt>(LOTE)
        reader.beginArray()
        while (reader.hasNext()) {
            leerUnaCarta(reader, cards, prints, arts)
            if (cards.size >= LOTE) {
                dao.insertarCartas(cards.toList()); cards.clear()
                dao.insertarPrints(prints.toList()); prints.clear()
                dao.insertarArtes(arts.toList()); arts.clear()
            }
        }
        reader.endArray()
        if (cards.isNotEmpty()) dao.insertarCartas(cards)
        if (prints.isNotEmpty()) dao.insertarPrints(prints)
        if (arts.isNotEmpty()) dao.insertarArtes(arts)
    }

    private fun leerUnaCarta(
        reader: JsonReader,
        cards: MutableList<Card>,
        prints: MutableList<CardPrint>,
        arts: MutableList<CardArt>
    ) {
        var id = 0L
        var nameEn = ""
        var nameEs: String? = null
        var desc = ""
        var type = ""
        var frameType: String? = null
        var attribute: String? = null
        var race: String? = null
        var level: Int? = null
        var atk: Int? = null
        var def: Int? = null
        var archetype: String? = null
        var img = ""
        var imgSmall: String? = null
        var priceCm: String? = null
        var priceTcg: String? = null
        val printsCarta = ArrayList<CardPrint>()
        val artesCarta = ArrayList<CardArt>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextLong()
                "nameEn" -> nameEn = reader.nextString()
                "nameEs" -> nameEs = reader.nextStringOrNull()
                "desc" -> desc = reader.nextStringOrNull() ?: ""
                "type" -> type = reader.nextStringOrNull() ?: ""
                "frameType" -> frameType = reader.nextStringOrNull()
                "attribute" -> attribute = reader.nextStringOrNull()
                "race" -> race = reader.nextStringOrNull()
                "level" -> level = reader.nextIntOrNull()
                "atk" -> atk = reader.nextIntOrNull()
                "def" -> def = reader.nextIntOrNull()
                "archetype" -> archetype = reader.nextStringOrNull()
                "img" -> img = reader.nextStringOrNull() ?: ""
                "imgSmall" -> imgSmall = reader.nextStringOrNull()
                "priceCm" -> priceCm = reader.nextStringOrNull()
                "priceTcg" -> priceTcg = reader.nextStringOrNull()
                "images" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var artId = 0L
                        var url: String? = null
                        var urlSmall: String? = null
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "artId" -> artId = reader.nextLong()
                                "url" -> url = reader.nextStringOrNull()
                                "urlSmall" -> urlSmall = reader.nextStringOrNull()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        // passcode se rellena al conocer el id (independiente del orden del JSON).
                        if (artId != 0L) artesCarta.add(CardArt(artId, 0, url, urlSmall))
                    }
                    reader.endArray()
                }
                "prints" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var code = ""
                        var set = ""
                        var rarity: String? = null
                        var price: String? = null
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "code" -> code = reader.nextStringOrNull() ?: ""
                                "set" -> set = reader.nextStringOrNull() ?: ""
                                "rarity" -> rarity = reader.nextStringOrNull()
                                "price" -> price = reader.nextStringOrNull()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        printsCarta.add(CardPrint(cardId = 0, setCode = code, setName = set, rarity = rarity, price = price))
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        cards.add(
            Card(
                id = id,
                nameEs = nameEs,
                nameEn = nameEn,
                description = desc,
                type = type,
                frameType = frameType,
                attribute = attribute,
                race = race,
                level = level,
                atk = atk,
                def = def,
                archetype = archetype,
                imageUrl = img,
                imageUrlSmall = imgSmall,
                priceCm = priceCm,
                priceTcg = priceTcg
            )
        )
        // Asigna el cardId ahora que conocemos el id (independiente del orden del JSON).
        printsCarta.forEach { if (it.setCode.isNotBlank()) prints.add(it.copy(cardId = id)) }
        artesCarta.forEach { arts.add(it.copy(passcode = id)) }
    }

    private fun JsonReader.nextStringOrNull(): String? =
        if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()

    private fun JsonReader.nextIntOrNull(): Int? =
        if (peek() == JsonToken.NULL) { nextNull(); null } else nextInt()
}
