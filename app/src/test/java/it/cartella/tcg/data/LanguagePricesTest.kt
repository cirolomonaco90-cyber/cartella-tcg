package it.cartella.tcg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Il formato deve corrispondere a quello scritto da prezzi/src/build.js. */
class LanguagePricesTest {

    private val sample = """
        {"v":1,"lang":"it","updated":"2026-10-19T02:40:00.000Z",
         "coef":{"all":[0.72,3400],"100":[0.65,40]},
         "p":{"9001":{"n":[72,3],"r":[300,1]},"9002":{"n":[3.5,1]}}}
    """.trimIndent()

    @Test
    fun `legge prezzi, coefficienti e data`() {
        val d = LanguagePrices.parse("it", sample)
        assertEquals("2026-10-19T02:40:00.000Z", d.updated)
        assertEquals(0.72, d.coefAll!!.ratio, 1e-9)
        assertEquals(3400, d.coefAll!!.samples)
        assertEquals(0.65, d.coefByExp[100]!!.ratio, 1e-9)
        assertEquals(72.0 to 3, d.prices[9001]!!['n'])
        assertEquals(300.0 to 1, d.prices[9001]!!['r'])
        assertEquals(3.5 to 1, d.prices[9002]!!['n'])
        assertNull(d.prices[9001]!!['f'])
    }

    @Test
    fun `file senza coefficienti`() {
        val d = LanguagePrices.parse("en", """{"v":1,"lang":"en","coef":{},"p":{}}""")
        assertNull(d.coefAll)
        assertEquals(0, d.prices.size)
    }

    @Test
    fun `lingue coperte`() {
        assertEquals("it", LanguagePrices.fileLang("it"))
        assertEquals("pt", LanguagePrices.fileLang("pt-br"))
        assertNull(LanguagePrices.fileLang("ja"))
    }

    @Test
    fun `lingua non affidabile non applica correzioni`() {
        val est = CtEstimate(150.0, "EUR", 4)
        val en = CtEstimate(100.0, "EUR", 25)
        val lp = CardLangPrice(
            lang = "de",
            estimates = mapOf(Variant.NORMAL to est),
            enEstimates = mapOf(Variant.NORMAL to en),
            coef = null,
            coefFromSet = false,
            updated = null,
            reliable = false,
        )
        val r = lp.ratio(Variant.NORMAL)
        assertEquals(1.0, r.ratio, 1e-9)
        assertNull(r.cardRatio)
    }
}
