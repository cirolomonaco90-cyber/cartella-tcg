package it.cartella.tcg.data

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

    /**
     * Prezzo di riferimento della carta per la variante scelta.
     * Preferisce Cardmarket (EUR, il mercato europeo); se manca usa TCGplayer (USD).
     */
    fun baseValue(pricing: Pricing?, variant: Variant): BaseValue? {
        if (pricing == null) return null

        val cmKeys = when (variant) {
            Variant.REVERSE -> listOf("trend-holo", "avg-holo")
            else -> listOf("trend", "avg", "low")
        }
        val tcgKeys = when (variant) {
            Variant.NORMAL -> listOf("normal")
            Variant.REVERSE -> listOf("reverse-holofoil", "reverse")
            Variant.HOLO -> listOf("holofoil", "holo")
            Variant.FIRST_EDITION -> listOf("1st-edition-holofoil", "1st-edition")
        }

        cmKeys.firstNotNullOfOrNull { pricing.cm[it] }
            ?.takeIf { it > 0 }
            ?.let { return BaseValue(it, "EUR", "Cardmarket") }

        for (key in tcgKeys) {
            val fields = pricing.tcg[key] ?: continue
            val v = (fields["marketPrice"] ?: fields["midPrice"])?.takeIf { it > 0 }
            if (v != null) return BaseValue(v, "USD", "TCGplayer")
        }

        // Ultima spiaggia: prezzo generico Cardmarket, segnalato come approssimato.
        if (variant == Variant.REVERSE || variant == Variant.FIRST_EDITION) {
            listOf("trend", "avg", "low").firstNotNullOfOrNull { pricing.cm[it] }
                ?.takeIf { it > 0 }
                ?.let { return BaseValue(it, "EUR", "Cardmarket", approximate = true) }
        }
        return null
    }

    fun round2(v: Double): Double = (v * 100).roundToInt() / 100.0
}
