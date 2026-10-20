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
        assertEquals(10.0, est.cheapest!!, 1e-9)
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

    private fun est(p: Double, n: Int) = CtEstimate(p, "EUR", n)

    @Test
    fun `rapporto della carta pesato con quello del set`() {
        // italiano 60, inglese 100 sulla stessa carta -> 60%; 3 offerte (minimo delle due) -> peso 50%
        val r = PriceEngine.languageRatio("it", est(60.0, 3), est(100.0, 8), LearnedCoef(0.8, 40), coefFromSet = true)
        assertEquals(0.6, r.cardRatio!!, 1e-9)
        assertEquals(0.5, r.cardWeight, 1e-9)
        assertEquals(0.7, r.ratio, 1e-9) // 0.5*0.6 + 0.5*0.8
        // con 6+ offerte in entrambe conta solo la carta
        val full = PriceEngine.languageRatio("it", est(60.0, 9), est(100.0, 7), LearnedCoef(0.8, 40), true)
        assertEquals(0.6, full.ratio, 1e-9)
    }

    @Test
    fun `senza offerte sulla carta vale il rapporto medio`() {
        val r = PriceEngine.languageRatio("it", null, est(100.0, 8), LearnedCoef(0.72, 3400), coefFromSet = false)
        assertNull(r.cardRatio)
        assertEquals(0.72, r.ratio, 1e-9)
        // nessun dato: nessuna correzione
        assertEquals(1.0, PriceEngine.languageRatio("it", null, null, null, false).ratio, 1e-9)
    }

    @Test
    fun `l inglese ha sempre rapporto 1`() {
        assertEquals(1.0, PriceEngine.languageRatio("en", est(50.0, 9), est(50.0, 9), LearnedCoef(2.0, 9), true).ratio, 1e-9)
    }

    @Test
    fun `prezzo finale cardmarket per rapporto`() {
        val b = PriceEngine.combine(10.0, "EUR", 25.0, "EUR", ratio = 0.7, ratioMeasured = true)!!
        assertEquals(7.0, b.nmAmount, 1e-9)
        assertTrue(b.fromCardmarket)
        // senza Cardmarket: la stima delle offerte nella lingua
        val ct = PriceEngine.combine(null, null, 25.0, "EUR", 0.7, true)!!
        assertEquals(25.0, ct.nmAmount, 1e-9)
        assertEquals(false, ct.fromCardmarket)
        assertNull(PriceEngine.combine(null, null, null, null, 1.0, false))
    }
}
