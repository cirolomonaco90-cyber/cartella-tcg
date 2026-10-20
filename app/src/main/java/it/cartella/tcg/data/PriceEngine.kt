package it.cartella.tcg.data

import kotlin.math.abs

/** Un'offerta in vendita su CardTrader (già filtrata per lingua dalla richiesta). */
data class CtOffer(
    val price: Double,
    val currency: String,
    val condition: String?,
    /** null = CardTrader non indica se è reverse. */
    val reverse: Boolean?,
    val firstEdition: Boolean?,
    val graded: Boolean,
    val bundle: Int = 1,
    val quantity: Int = 1,
)

/** Stima ricavata dalle offerte di una lingua, riportata alla condizione Near Mint. */
data class CtEstimate(
    val nmAmount: Double,
    val currency: String,
    /** Offerte usate nel calcolo (dopo i filtri). */
    val offers: Int,
    /** Offerta più bassa così com'è, senza normalizzazione (null se non nota). */
    val cheapest: Double? = null,
)

/** Coefficiente di lingua: rapporto prezzo lingua / prezzo inglese, con il numero di carte usate. */
data class LearnedCoef(val ratio: Double, val samples: Int)

/** Come si arriva al rapporto di lingua di una carta (per spiegarlo all'utente). */
data class RatioInfo(
    /** Rapporto usato: prezzo nella lingua = Cardmarket × ratio. */
    val ratio: Double,
    /** Rapporto misurato su questa carta (lingua/inglese su CardTrader), se disponibile. */
    val cardRatio: Double?,
    /** Peso del rapporto della carta rispetto a quello medio (0..1). */
    val cardWeight: Double,
    val langOffers: Int,
    val enOffers: Int,
    /** Rapporto medio del set (o della lingua) usato come base. */
    val coef: LearnedCoef?,
    val coefFromSet: Boolean,
)

/** Risultato finale, con i pezzi per spiegare il calcolo. */
data class PriceBreakdown(
    /** Prezzo di riferimento Near Mint per quella lingua. */
    val nmAmount: Double,
    val currency: String,
    /** true se basato su Cardmarket × rapporto, false se solo sulle offerte nella lingua. */
    val fromCardmarket: Boolean,
    val ratio: Double,
    val ratioMeasured: Boolean,
)

/**
 * L'algoritmo dei prezzi per lingua. Funzioni pure, senza Android: testate in PriceEngineTest.
 *
 * Cardmarket (via TCGdex) dà il prezzo più affidabile, ma uguale per tutte le lingue.
 * CardTrader dà le offerte separate per lingua, ma i suoi prezzi richiesti sono in genere
 * più alti delle vendite di Cardmarket. Per questo non si usano i prezzi CardTrader
 * direttamente: si usa il **rapporto** tra lingua e inglese sullo stesso sito, dove il
 * sovrapprezzo si annulla.
 *
 *   prezzo NM nella lingua = Cardmarket × rapporto
 *
 * 1. Stima Near Mint per lingua (job notturno): offerte riportate a NM con il coefficiente
 *    della condizione, mediana delle 5 più economiche, esclusa un'offerta anomala.
 * 2. Rapporto della carta = stima lingua / stima inglese, se entrambe hanno offerte.
 * 3. Rapporto medio del set (o della lingua), calcolato ogni notte su tutte le carte.
 * 4. Fusione: peso della carta = min(offerte lingua, offerte inglese) / 6, al massimo 100%.
 * 5. Senza prezzo Cardmarket si usa direttamente la stima CardTrader nella lingua.
 * Infine l'app applica la percentuale della condizione.
 */
object PriceEngine {
    const val FULL_WEIGHT_OFFERS = 6
    private const val CHEAPEST_K = 5

    /** Condizioni di CardTrader -> le nostre. */
    fun ctCondition(name: String?): Condition = when (name?.trim()?.lowercase()) {
        "mint" -> Condition.MINT
        "near mint" -> Condition.NEAR_MINT
        "slightly played" -> Condition.EXCELLENT
        "moderately played" -> Condition.GOOD
        "played" -> Condition.PLAYED
        "heavily played", "poor" -> Condition.POOR
        else -> Condition.NEAR_MINT
    }

    fun matchesVariant(o: CtOffer, variant: Variant): Boolean = when (variant) {
        // Per reverse e 1ª edizione serve che CardTrader lo dica esplicitamente.
        Variant.REVERSE -> o.reverse == true
        Variant.FIRST_EDITION -> o.firstEdition == true
        Variant.NORMAL, Variant.HOLO -> o.reverse != true && o.firstEdition != true
    }

    /** Stessa stima del job notturno (prezzi/src/stats.js), usata nei test. */
    fun estimate(offers: List<CtOffer>, variant: Variant): CtEstimate? {
        val usable = offers.filter {
            !it.graded && it.bundle <= 1 && it.quantity > 0 && it.price > 0 && matchesVariant(it, variant)
        }
        if (usable.isEmpty()) return null
        val currency = usable.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }!!.key
        val same = usable.filter { it.currency == currency }
        val nm = same.map { it.price / ctCondition(it.condition).defaultFactor }.sorted().toMutableList()
        if (nm.size >= 4 && nm[0] < 0.5 * nm[1]) nm.removeAt(0)
        return CtEstimate(
            nmAmount = median(nm.take(CHEAPEST_K)),
            currency = currency,
            offers = same.size,
            cheapest = same.minOf { it.price },
        )
    }

    fun weight(offers: Int): Double = (offers.toDouble() / FULL_WEIGHT_OFFERS).coerceIn(0.0, 1.0)

    /** Rapporto prezzo lingua / Cardmarket per una carta. L'inglese vale sempre 1. */
    fun languageRatio(
        lang: String,
        langEst: CtEstimate?,
        enEst: CtEstimate?,
        coef: LearnedCoef?,
        coefFromSet: Boolean,
    ): RatioInfo {
        if (lang == "en") return RatioInfo(1.0, null, 0.0, enEst?.offers ?: 0, enEst?.offers ?: 0, null, false)
        val base = coef?.ratio ?: 1.0
        val card = if (langEst != null && enEst != null && enEst.nmAmount > 0 && langEst.currency == enEst.currency) {
            (langEst.nmAmount / enEst.nmAmount).coerceIn(0.1, 5.0)
        } else {
            null
        }
        val w = if (card != null) weight(minOf(langEst!!.offers, enEst!!.offers)) else 0.0
        val ratio = if (card != null) w * card + (1 - w) * base else base
        return RatioInfo(ratio, card, w, langEst?.offers ?: 0, enEst?.offers ?: 0, coef, coefFromSet)
    }

    /**
     * Prezzo Near Mint nella lingua: Cardmarket × rapporto; senza Cardmarket, la stima
     * delle offerte nella lingua.
     */
    fun combine(
        cm: Double?,
        cmCurrency: String?,
        ct: Double?,
        ctCurrency: String?,
        ratio: Double,
        ratioMeasured: Boolean,
    ): PriceBreakdown? = when {
        cm != null && cm > 0 && cmCurrency != null ->
            PriceBreakdown(cm * ratio, cmCurrency, true, ratio, ratioMeasured)
        ct != null && ct > 0 && ctCurrency != null ->
            PriceBreakdown(ct, ctCurrency, false, ratio, ratioMeasured)
        else -> null
    }

    fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    fun approxEquals(a: Double, b: Double, eps: Double = 1e-6) = abs(a - b) < eps
}
