package it.cartella.tcg.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit

data class CtExpansion(val id: Int, val gameId: Int, val code: String, val name: String)

data class CtBlueprint(
    val id: Int,
    val name: String,
    val version: String?,
    val cardMarketIds: List<Int>,
    val collectorNumber: String?,
)

/**
 * Client per l'API ufficiale di CardTrader (https://www.cardtrader.com/docs/api/full).
 *
 * Serve un token personale, che ogni utente prende gratis dalle impostazioni del suo
 * profilo CardTrader e incolla nell'app. CardTrader è l'unica fonte aperta che filtra
 * le offerte per lingua della carta (parametro `language`).
 *
 * Giochi, espansioni e blueprint cambiano di rado: li teniamo in cache su disco per 7 giorni,
 * così per ogni carta serve di solito una sola chiamata (le offerte).
 */
class CardTraderApi(context: Context) {

    private val cacheDir = File(context.filesDir, "cardtrader").apply { mkdirs() }
    private val mutex = Mutex()
    private var pokemonGameId: Int? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private suspend fun getText(token: String, path: String, query: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val url = "$BASE/$path".toHttpUrl().newBuilder().apply {
                query.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/json")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    when {
                        resp.code == 401 -> throw ApiException("Token CardTrader non valido")
                        resp.code == 404 -> throw NotFoundException()
                        resp.code == 429 -> throw ApiException("Troppe richieste a CardTrader, riprova tra poco")
                        !resp.isSuccessful -> throw ApiException("Errore CardTrader (${resp.code})")
                        else -> body
                    }
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw ApiException("Connessione a CardTrader non riuscita", e)
            }
        }

    private fun parse(text: String): JsonElement = try {
        Json.parseToJsonElement(text)
    } catch (e: SerializationException) {
        throw ApiException("Risposta non valida da CardTrader", e)
    }

    /** Legge da cache (se recente) o scarica e salva. */
    private suspend fun cached(token: String, file: String, path: String, query: Map<String, String> = emptyMap()): JsonElement {
        val f = File(cacheDir, file)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < CACHE_MS
        if (fresh) {
            runCatching { return parse(withContext(Dispatchers.IO) { f.readText() }) }
        }
        val text = getText(token, path, query)
        val json = parse(text)
        withContext(Dispatchers.IO) { runCatching { f.writeText(text) } }
        return json
    }

    // Alcuni endpoint rispondono con un array, altri lo avvolgono in un oggetto: gestiamo entrambi.
    private fun JsonElement.items(): List<JsonObject> = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> (this["array"] as? JsonArray ?: values.firstOrNull { it is JsonArray } as? JsonArray)
            ?.mapNotNull { it as? JsonObject }.orEmpty()
        else -> emptyList()
    }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull

    /** Verifica il token: restituisce il nome dell'app/account associato. */
    suspend fun verify(token: String): String {
        val o = parse(getText(token, "info")) as? JsonObject
        return o?.get("name").str() ?: "OK"
    }

    private suspend fun gameId(token: String): Int = mutex.withLock {
        pokemonGameId?.let { return it }
        val games = cached(token, "games.json", "games").items()
        val id = games.firstOrNull { g ->
            val n = (g["name"].str() + " " + g["display_name"].str()).lowercase()
            "pok" in n
        }?.get("id").int() ?: throw ApiException("Pokémon non trovato tra i giochi di CardTrader")
        pokemonGameId = id
        id
    }

    suspend fun expansions(token: String): List<CtExpansion> {
        val game = gameId(token)
        return cached(token, "expansions.json", "expansions").items().mapNotNull { o ->
            val id = o["id"].int() ?: return@mapNotNull null
            val gid = o["game_id"].int() ?: return@mapNotNull null
            if (gid != game) return@mapNotNull null
            CtExpansion(id, gid, o["code"].str().orEmpty(), o["name"].str().orEmpty())
        }
    }

    suspend fun blueprints(token: String, expansionId: Int): List<CtBlueprint> =
        cached(token, "bp_$expansionId.json", "blueprints/export", mapOf("expansion_id" to expansionId.toString()))
            .items().mapNotNull { o ->
                val id = o["id"].int() ?: return@mapNotNull null
                val fixed = o["fixed_properties"] as? JsonObject
                CtBlueprint(
                    id = id,
                    name = o["name"].str().orEmpty(),
                    version = o["version"].str(),
                    cardMarketIds = (o["card_market_ids"] as? JsonArray)?.mapNotNull { it.int() }.orEmpty(),
                    collectorNumber = fixed?.entries?.firstOrNull { "collector" in it.key || "number" in it.key }
                        ?.value.str(),
                )
            }

    /**
     * Trova la carta su CardTrader:
     * 1. id già noto a TCGdex;
     * 2. espansione per nome inglese del set, poi carta per id Cardmarket (preciso);
     * 3. ripiego: numero della carta, poi nome inglese se è l'unico nel set.
     */
    suspend fun findBlueprint(token: String, card: CardDetail, englishSetName: String, englishName: String?): Int? {
        card.cardtraderId?.let { return it }
        val exp = CardMatching.matchExpansion(expansions(token), englishSetName) ?: return null
        val bps = blueprints(token, exp.id)
        if (card.cardmarketIds.isNotEmpty()) {
            bps.firstOrNull { bp -> bp.cardMarketIds.any { it in card.cardmarketIds } }?.let { return it.id }
        }
        val num = CardMatching.normNumber(card.localId)
        if (num.isNotEmpty()) {
            bps.filter { CardMatching.normNumber(it.collectorNumber) == num }
                .singleOrNull()?.let { return it.id }
        }
        if (englishName != null) {
            bps.filter { CardMatching.norm(it.name) == CardMatching.norm(englishName) }
                .singleOrNull()?.let { return it.id }
        }
        return null
    }

    /** Le offerte più economiche (max 25) di una carta, nella lingua indicata. */
    suspend fun offers(token: String, blueprintId: Int, lang: String): List<CtOffer> {
        val json = parse(
            getText(
                token, "marketplace/products",
                mapOf("blueprint_id" to blueprintId.toString(), "language" to ctLanguage(lang)),
            ),
        )
        val products: List<JsonObject> = when (json) {
            is JsonObject -> json.values.flatMap { v -> (v as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty() }
            is JsonArray -> json.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
        return products.mapNotNull { p ->
            val price = p["price"] as? JsonObject
            val cents = price?.get("cents").int() ?: return@mapNotNull null
            val props = p["properties_hash"] as? JsonObject
            fun boolProp(match: (String) -> Boolean): Boolean? =
                props?.entries?.firstOrNull { match(it.key.lowercase()) }
                    ?.let { (it.value as? JsonPrimitive)?.booleanOrNull }
            val onVacation = (p["on_vacation"] as? JsonPrimitive)?.booleanOrNull ?: false
            if (onVacation) return@mapNotNull null
            CtOffer(
                price = cents / 100.0,
                currency = price?.get("currency").str() ?: "EUR",
                condition = props?.get("condition").str(),
                reverse = boolProp { "reverse" in it },
                firstEdition = boolProp { "first_edition" in it || "1st" in it },
                graded = (p["graded"] as? JsonPrimitive)?.booleanOrNull ?: false,
                bundle = p["bundle_size"].int() ?: 1,
                quantity = p["quantity"].int() ?: 1,
            )
        }
    }

    companion object {
        private const val BASE = "https://api.cardtrader.com/api/v2"
        private const val CACHE_MS = 7L * 24 * 60 * 60 * 1000

        /** Codici lingua di CardTrader (2 lettere). */
        fun ctLanguage(lang: String): String = when (lang) {
            "pt-br", "pt-pt" -> "pt"
            else -> lang.take(2)
        }

        fun cardUrl(blueprintId: Int) = "https://www.cardtrader.com/cards/$blueprintId"
    }
}

/** Funzioni pure di abbinamento (testate in CardMatchingTest). */
object CardMatching {
    fun norm(s: String?): String {
        if (s == null) return ""
        val noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return noAccents.lowercase()
            .replace("&", " and ")
            .replace(Regex("\\b(pokemon|tcg|set)\\b"), " ")
            .replace(Regex("[^a-z0-9]"), "")
    }

    /** "007" -> "7", "149/147" -> "149", "H14" -> "h14". */
    fun normNumber(s: String?): String {
        if (s == null) return ""
        val first = s.substringBefore('/').trim().lowercase()
        val digits = first.trimStart('0')
        return if (first.isNotEmpty() && digits.isEmpty()) "0" else digits
    }

    fun matchExpansion(all: List<CtExpansion>, englishSetName: String): CtExpansion? {
        val target = norm(englishSetName)
        if (target.isEmpty()) return null
        all.firstOrNull { norm(it.name) == target }?.let { return it }
        // Nomi leggermente diversi ("Base Set" / "Base"): il più simile che contiene l'altro.
        return all
            .filter { val n = norm(it.name); n.isNotEmpty() && (n.contains(target) || target.contains(n)) }
            .filter { kotlin.math.abs(norm(it.name).length - target.length) <= 8 }
            .minByOrNull { kotlin.math.abs(norm(it.name).length - target.length) }
    }
}
