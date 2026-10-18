package it.cartella.tcg.data

import java.net.URLEncoder
import kotlin.math.roundToInt

/** Condizioni standard del mercato collezionistico. [defaultFactor] = % del prezzo di riferimento. */
enum class Condition(
    val code: String,
    val labelIt: String,
    val defaultFactor: Double,
    val hintIt: String,
) {
    MINT("M", "Mint", 1.05, "Perfetta, come appena uscita dalla bustina"),
    NEAR_MINT("NM", "Near Mint", 1.00, "Quasi perfetta, segni appena visibili"),
    EXCELLENT("EX", "Excellent", 0.85, "Lievi segni su bordi o angoli"),
    GOOD("GD", "Good", 0.70, "Segni evidenti ma carta integra"),
    LIGHT_PLAYED("LP", "Light Played", 0.55, "Usura visibile su bordi e superficie"),
    PLAYED("PL", "Played", 0.40, "Molto usata, con pieghe o graffi"),
    POOR("PO", "Poor", 0.25, "Danneggiata: piegata, strappata o sporca"),
}

enum class Variant(val labelIt: String) {
    NORMAL("Normale"),
    REVERSE("Reverse holo"),
    HOLO("Holo"),
    FIRST_EDITION("1ª edizione"),
}

data class BaseValue(
    val amount: Double,
    val currency: String,
    val source: String,
    /** true se il prezzo non è specifico per la variante scelta. */
    val approximate: Boolean = false,
)

object Valuation {

    private val CM_STANDARD = listOf("trend", "avg30", "avg", "low")
    private val CM_FOIL = listOf("trend-holo", "avg30-holo", "avg-holo", "low-holo")

    /**
     * Prezzo di riferimento (condizione Near Mint) della carta per la variante scelta.
     *
     * Ordine di ricerca:
     * 1. prezzo della variante specifica (variants_detailed di TCGdex), se c'è;
     * 2. prezzo generale della carta: Cardmarket (EUR, mercato europeo), poi TCGplayer (USD);
     * 3. per reverse e 1ª edizione senza dati propri: prezzo generale, segnalato come approssimato.
     *
     * Su Cardmarket la reverse holo è la versione "foil" dello stesso prodotto (campi *-holo),
     * mentre per le carte holo il prezzo standard è già quello della holo.
     */
    fun baseValue(card: CardDetail?, variant: Variant): BaseValue? {
        if (card == null) return null
        val specific = when (variant) {
            Variant.FIRST_EDITION -> card.variantPricing.firstOrNull { it.firstEdition }
            Variant.REVERSE -> card.variantPricing.firstOrNull { it.type == "reverse" && !it.firstEdition }
            Variant.HOLO -> card.variantPricing.firstOrNull { it.type == "holo" && !it.firstEdition }
            Variant.NORMAL -> card.variantPricing.firstOrNull { it.type == "normal" && !it.firstEdition }
        }?.pricing

        exact(specific, variant)?.let { return it }

        // La 1ª edizione è un prodotto a sé: il prezzo generale è quello "unlimited", molto più basso.
        if (variant != Variant.FIRST_EDITION) exact(card.pricing, variant)?.let { return it }

        if (variant == Variant.REVERSE || variant == Variant.FIRST_EDITION) {
            val generic = cm(specific, CM_STANDARD) ?: cm(card.pricing, CM_STANDARD)
            generic?.let { return BaseValue(it, "EUR", "Cardmarket", approximate = true) }
        }
        return null
    }

    private fun exact(p: Pricing?, variant: Variant): BaseValue? {
        if (p == null) return null
        val cmValue = when (variant) {
            Variant.REVERSE -> cm(p, CM_FOIL)
            else -> cm(p, CM_STANDARD)
        }
        if (cmValue != null) return BaseValue(cmValue, "EUR", "Cardmarket")

        val tcgKeys = when (variant) {
            Variant.NORMAL -> listOf("normal")
            Variant.REVERSE -> listOf("reverse", "reverse-holofoil")
            Variant.HOLO -> listOf("holo", "holofoil")
            Variant.FIRST_EDITION -> listOf("1st-edition-holofoil", "1st-edition", "holo", "normal")
        }
        for (key in tcgKeys) {
            val fields = p.tcg[key] ?: continue
            val v = (fields["marketPrice"] ?: fields["midPrice"])?.takeIf { it > 0 }
            if (v != null) return BaseValue(v, "USD", "TCGplayer")
        }
        return null
    }

    private fun cm(p: Pricing?, keys: List<String>): Double? =
        p?.let { pr -> keys.firstNotNullOfOrNull { pr.cm[it]?.takeIf { v -> v > 0 } } }

    fun round2(v: Double): Double = (v * 100).roundToInt() / 100.0
}

/** Link esterni per controllare il prezzo a mano. */
object MarketLinks {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // Solo le sigle che i venditori scrivono davvero nei titoli: le altre restringerebbero troppo.
    private fun langTag(lang: String) = when (lang) {
        "it" -> "ita"
        "ja" -> "japanese"
        else -> ""
    }

    fun searchText(name: String, localId: String, setTotal: Int?, lang: String): String {
        val number = if (setTotal != null && localId.isNotBlank()) "$localId/$setTotal" else localId
        return listOf(name, number, langTag(lang)).filter { it.isNotBlank() }.joinToString(" ")
    }

    /** Inserzioni concluse e vendute su eBay.it per quella carta. */
    fun ebaySold(query: String) =
        "https://www.ebay.it/sch/i.html?_nkw=${enc(query)}&LH_Sold=1&LH_Complete=1&_sop=13"

    fun cardmarketSearch(name: String) =
        "https://www.cardmarket.com/it/Pokemon/Products/Search?searchString=${enc(name)}"
}
