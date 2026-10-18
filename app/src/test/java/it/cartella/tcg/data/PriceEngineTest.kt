package it.cartella.tcg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceEngineTest {

    private fun offer(
        price: Double,
        condition: String = "Near Mint",
        reverse: Boolean? = null,
        firstEdition: Boolean? = null,
        graded: Boolean = false,
        currency: String = "EUR",
        bundle: Int = 1,
    ) = CtOffer(price, currency, condition, reverse, firstEdition, graded, bundle, 1)

    @Test
    fun `mediana delle 5 offerte piu economiche`() {
        val offers = listOf(10.0, 12.0, 11.0, 30.0, 13.0, 14.0, 50.0).map { offer(it) }
        val est = PriceEngine.estimate(offers, Variant.NORMAL)!!
        // più economiche: 10, 11, 12, 13, 14 -> mediana 12
        assertEquals(12.0, est.nmAmount, 1e-9)
        assertEquals(7, est.offers)
        assertEquals(10.0, est.cheapest, 1e-9)
    }

    @Test
    fun `le condizioni vengono riportate a near mint`() {
        // 7 € in Played (40%) = 17,5 € Near Mint
        val est = PriceEngine.estimate(listOf(offer(7.0, "Played")), Variant.NORMAL)!!
        assertEquals(17.5, est.nmAmount, 1e-9)
    }

    @Test
    fun `scarta graded, lotti e un prezzo anomalo`() {
        val offers = listOf(
            offer(1.0), // anomalo: meno della metà del successivo
            offer(10.0), offer(11.0), offer(12.0),
            offer(2.0, graded = true),
            offer(3.0, bundle = 4),
        )
        val est = PriceEngine.estimate(offers, Variant.NORMAL)!!
        assertEquals(11.0, est.nmAmount, 1e-9) // 10, 11, 12
    }

    @Test
    fun `reverse e prima edizione richiedono il dato esplicito`() {
        val offers = listOf(offer(5.0), offer(20.0, reverse = true), offer(200.0, firstEdition = true))
        assertEquals(20.0, PriceEngine.estimate(offers, Variant.REVERSE)!!.nmAmount, 1e-9)
        assertEquals(200.0, PriceEngine.estimate(offers, Variant.FIRST_EDITION)!!.nmAmount, 1e-9)
        assertEquals(5.0, PriceEngine.estimate(offers, Variant.NORMAL)!!.nmAmount, 1e-9)
        assertNull(PriceEngine.estimate(listOf(offer(5.0)), Variant.REVERSE))
    }

    @Test
    fun `fusione pesata per numero di offerte`() {
        // 3 offerte -> peso CardTrader 50%
        val b = PriceEngine.combine(10.0, "EUR", 6.0, "EUR", 3, langCoef = 1.0, langCoefLearned = false)!!
        assertEquals(0.5, b.ctWeight, 1e-9)
        assertEquals(8.0, b.nmAmount, 1e-9)
        // 6+ offerte -> solo CardTrader
        val full = PriceEngine.combine(10.0, "EUR", 6.0, "EUR", 9, 1.0, false)!!
        assertEquals(6.0, full.nmAmount, 1e-9)
    }

    @Test
    fun `senza cardtrader usa cardmarket per il coefficiente lingua`() {
        val b = PriceEngine.combine(10.0, "EUR", null, null, 0, langCoef = 0.7, langCoefLearned = true)!!
        assertEquals(7.0, b.nmAmount, 1e-9)
        assertEquals(0.0, b.ctWeight, 1e-9)
        assertTrue(b.langCoefLearned)
    }

    @Test
    fun `valute diverse non si mescolano`() {
        val few = PriceEngine.combine(10.0, "USD", 6.0, "EUR", 1, 1.0, false)!!
        assertEquals("USD", few.currency) // poche offerte: resta Cardmarket/TCGplayer
        val many = PriceEngine.combine(10.0, "USD", 6.0, "EUR", 4, 1.0, false)!!
        assertEquals("EUR", many.currency)
        assertEquals(6.0, many.nmAmount, 1e-9)
    }

    @Test
    fun `nessun dato restituisce null`() {
        assertNull(PriceEngine.combine(null, null, null, null, 0, 1.0, false))
    }

    @Test
    fun `coefficiente imparato e la mediana dei rapporti`() {
        val samples = listOf(
            PriceEngine.LearningSample("it", "a", 10.0, 7.0),
            PriceEngine.LearningSample("it", "b", 20.0, 12.0),
            PriceEngine.LearningSample("it", "c", 5.0, 4.0),
            PriceEngine.LearningSample("it", "c", 5.0, 4.0), // duplicato: conta una volta
            PriceEngine.LearningSample("en", "a", 10.0, 10.0),
        )
        val learned = PriceEngine.learn(samples)
        assertNotNull(learned["it"])
        assertEquals(3, learned["it"]!!.samples)
        assertEquals(0.7, learned["it"]!!.ratio, 1e-9) // 0.6, 0.7, 0.8
        assertEquals(1.0, learned["en"]!!.ratio, 1e-9)
    }

    @Test
    fun `fattori usano il coefficiente imparato solo con abbastanza carte`() {
        val f = ValueFactors(
            language = mapOf("it" to 0.9),
            learned = mapOf("it" to LearnedCoef(0.6, PriceEngine.MIN_LEARN - 1)),
        )
        assertEquals(0.9, f.ofLang("it"), 1e-9)
        val g = f.copy(learned = mapOf("it" to LearnedCoef(0.6, PriceEngine.MIN_LEARN)))
        assertEquals(0.6, g.ofLang("it"), 1e-9)
    }
}
