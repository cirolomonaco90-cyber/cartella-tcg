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
    /** Offerta più bassa così com'è, senza normalizzazione. */
    val cheapest: Double,
)

/** Coefficiente di lingua imparato dalla collezione: prezzo lingua / prezzo Cardmarket. */
data class LearnedCoef(val ratio: Double, val samples: Int)

/** Risultato finale, con tutti i pezzi per spiegare il calcolo all'utente. */
data class PriceBreakdown(
    /** Prezzo di riferimento Near Mint per quella lingua. */
    val nmAmount: Double,
    val currency: String,
    /** Quanto pesa CardTrader (0 = solo Cardmarket corretto, 1 = solo CardTrader). */
    val ctWeight: Double,
    val cmAdjusted: Double?,
    val langCoef: Double,
    val langCoefLearned: Boolean,
)

/**
 * L'algoritmo dei prezzi per lingua. Funzioni pure, senza Android: testate in PriceEngineTest.
 *
 * 1. Cardmarket (via TCGdex) dà un prezzo affidabile ma uguale per tutte le lingue.
 * 2. CardTrader dà le offerte in vendita filtrate per lingua: ogni offerta viene riportata
 *    a Near Mint dividendo per il coefficiente della sua condizione; si prende la mediana
 *    delle 5 più economiche (le offerte care spesso restano invendute, le più basse
 *    rappresentano il prezzo a cui la carta si vende davvero), scartando un'eventuale
 *    offerta anomala molto più bassa delle altre.
 * 3. I due prezzi si fondono: più offerte ci sono, più conta CardTrader (da 6 offerte in su
 *    conta solo lui). Con poche offerte si usa Cardmarket × coefficiente della lingua.
 * 4. Il coefficiente della lingua si impara dalla collezione stessa: per le carte con
 *    abbastanza offerte si calcola il rapporto CardTrader / Cardmarket e se ne prende la
 *    mediana per lingua. Con meno di [MIN_LEARN] carte si usa quello impostato a mano.
 */
object PriceEngine {
    const val FULL_WEIGHT_OFFERS = 6
    const val MIN_OFFERS_FOR_LEARNING = 3
    const val MIN_LEARN = 5
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

    fun estimate(offers: List<CtOffer>, variant: Variant): CtEstimate? {
        val usable = offers.filter {
            !it.graded && it.bundle <= 1 && it.quantity > 0 && it.price > 0 && matchesVariant(it, variant)
        }
        if (usable.isEmpty()) return null
        val currency = usable.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }!!.key
        val same = usable.filter { it.currency == currency }
        val nm = same.map { it.price / ctCondition(it.condition).defaultFactor }.sorted().toMutableList()
        // Un'offerta isolata a meno della metà della successiva è probabilmente un errore o un'esca.
        if (nm.size >= 4 && nm[0] < 0.5 * nm[1]) nm.removeAt(0)
        val cheapest = nm.take(CHEAPEST_K)
        return CtEstimate(
            nmAmount = median(cheapest),
            currency = currency,
            offers = same.size,
            cheapest = same.minOf { it.price },
        )
    }

    fun ctWeight(offers: Int): Double = (offers.toDouble() / FULL_WEIGHT_OFFERS).coerceIn(0.0, 1.0)

    /**
     * Prezzo Near Mint per una lingua.
     * [cm] = prezzo Cardmarket (tutte le lingue), [ct] = stima CardTrader per la lingua.
     */
    fun combine(
        cm: Double?,
        cmCurrency: String?,
        ct: Double?,
        ctCurrency: String?,
        ctOffers: Int,
        langCoef: Double,
        langCoefLearned: Boolean,
    ): PriceBreakdown? {
        val cmAdj = cm?.let { it * langCoef }
        val hasCt = ct != null && ct > 0 && ctCurrency != null && ctOffers > 0
        return when {
            hasCt && cmAdj != null && cmCurrency == ctCurrency -> {
                val w = ctWeight(ctOffers)
                PriceBreakdown(w * ct!! + (1 - w) * cmAdj, ctCurrency!!, w, cmAdj, langCoef, langCoefLearned)
            }
            // Valute diverse: non si mescolano. CardTrader vince se ha abbastanza offerte.
            hasCt && (cmAdj == null || ctOffers >= MIN_OFFERS_FOR_LEARNING) ->
                PriceBreakdown(ct!!, ctCurrency!!, 1.0, cmAdj, langCoef, langCoefLearned)
            cmAdj != null && cmCurrency != null ->
                PriceBreakdown(cmAdj, cmCurrency, 0.0, cmAdj, langCoef, langCoefLearned)
            else -> null
        }
    }

    data class LearningSample(val lang: String, val key: String, val cm: Double, val ct: Double)

    /** Mediana dei rapporti CardTrader/Cardmarket per lingua (una carta conta una volta). */
    fun learn(samples: List<LearningSample>): Map<String, LearnedCoef> =
        samples.distinctBy { it.lang to it.key }
            .filter { it.cm > 0 && it.ct > 0 }
            .groupBy { it.lang }
            .mapValues { (_, list) ->
                LearnedCoef(median(list.map { (it.ct / it.cm).coerceIn(0.1, 5.0) }), list.size)
            }

    fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    fun approxEquals(a: Double, b: Double, eps: Double = 1e-6) = abs(a - b) < eps
}
