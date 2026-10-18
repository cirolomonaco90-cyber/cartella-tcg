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
)

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

data class CardDetail(
    val id: String,
    val localId: String,
    val name: String,
    val imageBase: String?,
    val setName: String,
    val rarity: String?,
    val illustrator: String?,
    val hasNormal: Boolean,
    val hasReverse: Boolean,
    val hasHolo: Boolean,
    val hasFirstEdition: Boolean,
    val pricing: Pricing?,
)
