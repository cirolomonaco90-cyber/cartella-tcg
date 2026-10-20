package it.cartella.tcg.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Dati di una lingua pubblicati dal job notturno (vedi prezzi/src/build.js). */
class LangData(
    val lang: String,
    val updated: String?,
    val coefAll: LearnedCoef?,
    val coefByExp: Map<Int, LearnedCoef>,
    /** idProdotto Cardmarket -> variante ('n', 'r', 'f') -> [prezzo NM, offerte] */
    val prices: Map<Int, Map<Char, Pair<Double, Int>>>,
)

/** Prezzi per lingua di una carta, pronti per il calcolo. */
data class CardLangPrice(
    val lang: String,
    /** Stime per variante dalle offerte CardTrader nella lingua della copia. */
    val estimates: Map<Variant, CtEstimate>,
    /** Stime per variante delle offerte in inglese (riferimento per il rapporto). */
    val enEstimates: Map<Variant, CtEstimate>,
    /** Rapporto medio lingua/inglese del set (o della lingua), calcolato ogni notte. */
    val coef: LearnedCoef?,
    /** true se il rapporto medio è quello del set, false se quello della lingua. */
    val coefFromSet: Boolean,
    val updated: String?,
    /**
     * false se il job non ha pubblicato un rapporto per la lingua (troppe poche offerte
     * su CardTrader per un confronto affidabile con l'inglese): niente correzione.
     */
    val reliable: Boolean = true,
) {
    /** Rapporto da applicare a Cardmarket per questa variante. */
    fun ratio(variant: Variant): RatioInfo =
        if (reliable) {
            PriceEngine.languageRatio(lang, estimates[variant], enEstimates[variant], coef, coefFromSet)
        } else {
            PriceEngine.languageRatio(lang, null, null, null, false)
        }
}

/**
 * Legge i prezzi per lingua pubblicati ogni notte da GitHub Actions sul branch "prezzi".
 * Nessuna chiave o configurazione: file pubblici, scaricati al bisogno e tenuti in cache
 * (riscaricati dopo [MAX_AGE_MS]; se la rete manca si usa l'ultima copia).
 */
class LanguagePrices(context: Context) {
    private val dir = File(context.filesDir, "prezzi").apply { mkdirs() }
    private val mutex = Mutex()
    private var catalog: Map<Int, Int>? = null
    private val langs = HashMap<String, LangData>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Scarica (o legge dalla cache) un file; null se non disponibile. */
    private suspend fun fileText(name: String): String? = withContext(Dispatchers.IO) {
        val f = File(dir, name)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS
        if (fresh) return@withContext f.readText()
        val downloaded = runCatching {
            client.newCall(Request.Builder().url(BASE + name).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.getOrNull()
        if (downloaded != null && downloaded.startsWith("{")) {
            runCatching { f.writeText(downloaded) }
            downloaded
        } else if (f.exists()) {
            f.readText() // copia vecchia: meglio di niente
        } else {
            null
        }
    }

    private suspend fun catalog(): Map<Int, Int> = mutex.withLock {
        catalog?.let { return it }
        val text = fileText("catalog.json") ?: return emptyMap()
        val cm = runCatching { (Json.parseToJsonElement(text) as JsonObject)["cm"] as? JsonObject }.getOrNull()
        val map = cm?.entries?.mapNotNull { (k, v) ->
            val id = k.toIntOrNull() ?: return@mapNotNull null
            val exp = (v as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            id to exp
        }?.toMap().orEmpty()
        catalog = map
        map
    }

    suspend fun lang(lang: String): LangData? {
        val code = fileLang(lang) ?: return null
        mutex.withLock { langs[code]?.let { return it } }
        val text = fileText("$code.json") ?: return null
        val data = withContext(Dispatchers.Default) { runCatching { parse(code, text) }.getOrNull() } ?: return null
        mutex.withLock { langs[code] = data }
        return data
    }

    /** Prezzi per lingua di una carta (null se i file non sono raggiungibili o la lingua non è coperta). */
    suspend fun forCard(card: CardDetail, lang: String): CardLangPrice? {
        val data = lang(lang) ?: return null
        val en = if (fileLang(lang) == "en") data else lang("en")
        val exp = catalog().let { cat -> card.cardmarketIds.firstNotNullOfOrNull { cat[it] } }
        val setCoef = exp?.let { data.coefByExp[it] }
        return CardLangPrice(
            lang = lang,
            estimates = estimatesFor(data, card),
            enEstimates = en?.let { estimatesFor(it, card) }.orEmpty(),
            coef = setCoef ?: data.coefAll,
            coefFromSet = setCoef != null,
            updated = data.updated,
            reliable = fileLang(lang) == "en" || data.coefAll != null,
        )
    }

    private fun estimatesFor(data: LangData, card: CardDetail): Map<Variant, CtEstimate> {
        val entry = card.cardmarketIds.firstNotNullOfOrNull { data.prices[it] } ?: return emptyMap()
        return buildMap {
            entry.forEach { (v, value) ->
                val est = CtEstimate(nmAmount = value.first, currency = "EUR", offers = value.second)
                when (v) {
                    'n' -> { put(Variant.NORMAL, est); put(Variant.HOLO, est) }
                    'r' -> put(Variant.REVERSE, est)
                    'f' -> put(Variant.FIRST_EDITION, est)
                }
            }
        }
    }

    companion object {
        private const val BASE = "https://raw.githubusercontent.com/cirolomonaco90-cyber/cartella-tcg/prezzi/v1/"
        private const val MAX_AGE_MS = 20L * 60 * 60 * 1000

        /** Lingue coperte dal job (occidentali per ora). */
        val SUPPORTED = setOf("en", "it", "fr", "de", "es", "pt")

        fun fileLang(lang: String): String? {
            val code = if (lang.startsWith("pt")) "pt" else lang
            return code.takeIf { it in SUPPORTED }
        }

        fun parse(code: String, text: String): LangData {
            val root = Json.parseToJsonElement(text) as JsonObject
            fun pair(el: Any?): LearnedCoef? {
                val arr = el as? JsonArray ?: return null
                val r = (arr.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return null
                val n = (arr.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: return null
                return LearnedCoef(r, n)
            }
            val coef = root["coef"] as? JsonObject
            val byExp = coef?.entries?.mapNotNull { (k, v) ->
                val id = k.toIntOrNull() ?: return@mapNotNull null
                pair(v)?.let { id to it }
            }?.toMap().orEmpty()
            val prices = (root["p"] as? JsonObject)?.entries?.mapNotNull { (k, v) ->
                val id = k.toIntOrNull() ?: return@mapNotNull null
                val o = v as? JsonObject ?: return@mapNotNull null
                val variants = o.entries.mapNotNull { (variant, arr) ->
                    val a = arr as? JsonArray ?: return@mapNotNull null
                    val price = (a.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                    val count = (a.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                    variant.first() to (price to count)
                }.toMap()
                id to variants
            }?.toMap().orEmpty()
            return LangData(
                lang = code,
                updated = (root["updated"] as? JsonPrimitive)?.contentOrNull,
                coefAll = pair(coef?.get("all")),
                coefByExp = byExp,
                prices = prices,
            )
        }
    }
}
