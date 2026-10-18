package it.cartella.tcg.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

open class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
class NotFoundException : ApiException("Non trovato")

/**
 * Client minimale per l'API REST pubblica di TCGdex (https://tcgdex.dev/rest).
 * Nessuna chiave richiesta. I campi sono letti in modo tollerante: se TCGdex
 * aggiunge o toglie qualcosa, l'app non si rompe.
 */
object TcgdexApi {
    private const val BASE = "https://api.tcgdex.net/v2"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun get(
        lang: String,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): JsonElement = withContext(Dispatchers.IO) {
        val url = "$BASE/$lang/$path".toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when {
                    resp.code == 404 -> throw NotFoundException()
                    !resp.isSuccessful -> throw ApiException("Errore del server (${resp.code})")
                    else -> Json.parseToJsonElement(body)
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("Connessione non riuscita. Controlla internet e riprova.", e)
        } catch (e: SerializationException) {
            throw ApiException("Risposta non valida da TCGdex", e)
        }
    }

    suspend fun searchCards(lang: String, name: String, page: Int, perPage: Int): List<CardBrief> {
        val json = try {
            get(
                lang, "cards",
                mapOf(
                    "name" to name,
                    "pagination:page" to page.toString(),
                    "pagination:itemsPerPage" to perPage.toString(),
                ),
            )
        } catch (e: NotFoundException) {
            return emptyList()
        }
        return json.asArray().mapNotNull { it.asObject()?.toBrief() }
    }

    suspend fun sets(lang: String): List<SetBrief> {
        val json = get(lang, "sets")
        return json.asArray().mapNotNull { el ->
            val o = el.asObject() ?: return@mapNotNull null
            val id = o["id"].str() ?: return@mapNotNull null
            val count = o["cardCount"] as? JsonObject
            SetBrief(
                id = id,
                name = o["name"].str() ?: id,
                logoBase = o["logo"].str(),
                total = count?.get("total").int() ?: count?.get("official").int() ?: 0,
            )
        }
    }

    suspend fun setCards(lang: String, setId: String): List<CardBrief> {
        val json = get(lang, "sets/$setId")
        val cards = (json as? JsonObject)?.get("cards") as? JsonArray ?: return emptyList()
        return cards.mapNotNull { it.asObject()?.toBrief() }
    }

    suspend fun card(lang: String, id: String): CardDetail {
        val o = get(lang, "cards/$id").asObject() ?: throw ApiException("Risposta non valida da TCGdex")
        val set = o["set"] as? JsonObject
        val setCount = set?.get("cardCount") as? JsonObject
        val variants = o["variants"] as? JsonObject
        fun flag(key: String) = (variants?.get(key) as? JsonPrimitive)?.booleanOrNull ?: false
        return CardDetail(
            id = o["id"].str() ?: id,
            localId = o["localId"].str() ?: "",
            name = o["name"].str() ?: id,
            imageBase = o["image"].str(),
            setName = set?.get("name").str() ?: "",
            setTotal = setCount?.get("official").int() ?: setCount?.get("total").int(),
            rarity = o["rarity"].str(),
            illustrator = o["illustrator"].str(),
            hasNormal = flag("normal"),
            hasReverse = flag("reverse"),
            hasHolo = flag("holo"),
            hasFirstEdition = flag("firstEdition"),
            pricing = parsePricing(o["pricing"]),
            variantPricing = parseVariants(o["variants_detailed"]),
        )
    }

    /**
     * Come [card], ma se nella lingua scelta manca l'immagine o il prezzo
     * li prende dalla versione inglese (stesso id carta).
     */
    suspend fun cardWithFallback(lang: String, id: String): CardDetail {
        val card = card(lang, id)
        if (lang == "en" || (card.imageBase != null && card.pricing != null)) return card
        val en = try {
            card("en", id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return card
        }
        return card.copy(
            imageBase = card.imageBase ?: en.imageBase,
            imageFromEnglish = card.imageBase == null && en.imageBase != null,
            pricing = card.pricing ?: en.pricing,
            variantPricing = card.variantPricing.ifEmpty { en.variantPricing },
        )
    }

    // ---- parsing helpers (tolleranti: i campi mancanti diventano null) ----

    private fun JsonElement.asArray(): JsonArray =
        this as? JsonArray ?: throw ApiException("Risposta non valida da TCGdex")

    private fun JsonElement.asObject(): JsonObject? = this as? JsonObject

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull

    private fun JsonObject.toBrief(): CardBrief? {
        val id = this["id"].str() ?: return null
        return CardBrief(
            id = id,
            localId = this["localId"].str() ?: "",
            name = this["name"].str() ?: id,
            imageBase = this["image"].str(),
        )
    }

    private fun parseVariants(el: JsonElement?): List<VariantPricing> {
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val type = o["type"].str() ?: return@mapNotNull null
            val stamps = (o["stamp"] as? JsonArray)?.mapNotNull { it.str() }.orEmpty()
            VariantPricing(
                type = type,
                firstEdition = stamps.any { it.startsWith("1st-edition") },
                pricing = parsePricing(o["pricing"]),
            )
        }
    }

    private fun parsePricing(el: JsonElement?): Pricing? {
        val p = el as? JsonObject ?: return null
        // Campi non di prezzo restituiti da TCGdex (es. Cardmarket include idProduct).
        val skip = setOf("updated", "unit", "idProduct", "productId")

        val cm = (p["cardmarket"] as? JsonObject)
            ?.entries
            ?.filter { it.key !in skip }
            ?.mapNotNull { (k, v) -> v.dbl()?.let { k to it } }
            ?.toMap()
            .orEmpty()

        val tcg = (p["tcgplayer"] as? JsonObject)
            ?.entries
            ?.filter { it.key !in skip && it.value is JsonObject }
            ?.associate { (variant, v) ->
                variant to (v as JsonObject).entries
                    .filter { it.key !in skip }
                    .mapNotNull { (k, x) -> x.dbl()?.let { k to it } }
                    .toMap()
            }
            .orEmpty()

        return if (cm.isEmpty() && tcg.isEmpty()) null else Pricing(cm, tcg)
    }
}

/** I campi "logo"/"image" di TCGdex non hanno estensione: aggiungiamo webp. */
fun assetUrl(base: String?): String? = base?.let {
    if (it.endsWith(".png") || it.endsWith(".webp") || it.endsWith(".jpg")) it else "$it.webp"
}

fun cardImageUrl(base: String?, high: Boolean): String? =
    base?.let { "$it/${if (high) "high" else "low"}.webp" }
