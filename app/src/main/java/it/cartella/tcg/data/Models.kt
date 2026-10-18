package it.cartella.tcg.data

data class Lang(val code: String, val label: String, val flag: String) {
    val display: String get() = "$flag $label"
}

/** Codici lingua accettati da TCGdex (v2). */
val CARD_LANGUAGES = listOf(
    Lang("it", "Italiano", "🇮🇹"),
    Lang("en", "English", "🇬🇧"),
    Lang("fr", "Français", "🇫🇷"),
    Lang("de", "Deutsch", "🇩🇪"),
    Lang("es", "Español", "🇪🇸"),
    Lang("pt-br", "Português (BR)", "🇧🇷"),
    Lang("pt-pt", "Português (PT)", "🇵🇹"),
    Lang("nl", "Nederlands", "🇳🇱"),
    Lang("pl", "Polski", "🇵🇱"),
    Lang("ru", "Русский", "🇷🇺"),
    Lang("ja", "日本語", "🇯🇵"),
    Lang("ko", "한국어", "🇰🇷"),
    Lang("zh-tw", "繁體中文", "🇹🇼"),
    Lang("zh-cn", "简体中文", "🇨🇳"),
    Lang("id", "Indonesia", "🇮🇩"),
    Lang("th", "ไทย", "🇹🇭"),
)

fun languageOf(code: String): Lang =
    CARD_LANGUAGES.firstOrNull { it.code == code } ?: Lang(code, code.uppercase(), "🏳️")

data class CardBrief(
    val id: String,
    val localId: String,
    val name: String,
    val imageBase: String?,
    /** true se la carta manca nella lingua scelta e i dati vengono dalla versione inglese. */
    val fromEnglish: Boolean = false,
)

data class SearchPage(val cards: List<CardBrief>, val endReached: Boolean)

data class SetBrief(
    val id: String,
    val name: String,
    val logoBase: String?,
    val total: Int,
)

/**
 * Prezzi come restituiti da TCGdex.
 * [cm] = campi Cardmarket (EUR), es. "trend", "avg", "low", "trend-holo".
 * [tcg] = TCGplayer (USD): variante -> campo -> valore, es. "holofoil" -> "marketPrice".
 */
data class Pricing(
    val cm: Map<String, Double>,
    val tcg: Map<String, Map<String, Double>>,
)

/**
 * Una voce di "variants_detailed": TCGdex può avere prezzi separati per variante
 * (es. la 1ª edizione è un prodotto diverso su Cardmarket).
 */
data class VariantPricing(
    /** "normal", "holo", "reverse", ... */
    val type: String,
    val firstEdition: Boolean,
    val pricing: Pricing?,
    val cardmarketId: Int? = null,
    val cardtraderId: Int? = null,
)

data class CardDetail(
    val id: String,
    val localId: String,
    val name: String,
    val imageBase: String?,
    val setName: String,
    val setTotal: Int?,
    val rarity: String?,
    val illustrator: String?,
    val hasNormal: Boolean,
    val hasReverse: Boolean,
    val hasHolo: Boolean,
    val hasFirstEdition: Boolean,
    val pricing: Pricing?,
    val variantPricing: List<VariantPricing> = emptyList(),
    /** true se l'immagine viene dalla versione inglese perché manca nella lingua scelta. */
    val imageFromEnglish: Boolean = false,
    /** true se TCGdex non ha la carta nella lingua scelta e tutti i dati sono quelli inglesi. */
    val dataFromEnglish: Boolean = false,
    val setId: String = "",
    /** Id prodotto Cardmarket della carta (radice + varianti): servono per trovarla su CardTrader. */
    val cardmarketIds: Set<Int> = emptySet(),
    /** Id "blueprint" CardTrader, quando TCGdex li conosce già. */
    val cardtraderId: Int? = null,
)
