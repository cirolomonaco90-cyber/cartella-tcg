package it.cartella.tcg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardMatchingTest {

    private val exps = listOf(
        CtExpansion(1, 5, "aq", "Aquapolis"),
        CtExpansion(2, 5, "bs", "Base Set"),
        CtExpansion(3, 5, "sv2", "Paldea Evolved"),
        CtExpansion(4, 5, "pr", "Pokémon Rumble"),
        CtExpansion(5, 5, "ss", "Scarlet & Violet"),
    )

    @Test
    fun `nomi identici o con differenze minime`() {
        assertEquals(1, CardMatching.matchExpansion(exps, "Aquapolis")?.id)
        assertEquals(3, CardMatching.matchExpansion(exps, "Paldea Evolved")?.id)
        assertEquals(2, CardMatching.matchExpansion(exps, "Base")?.id)
        assertEquals(4, CardMatching.matchExpansion(exps, "Pokemon Rumble")?.id)
        assertEquals(5, CardMatching.matchExpansion(exps, "Scarlet and Violet")?.id)
    }

    @Test
    fun `set inesistente`() {
        assertNull(CardMatching.matchExpansion(exps, "Expedition Base Set Deluxe Edition Super Long"))
        assertNull(CardMatching.matchExpansion(exps, ""))
    }

    @Test
    fun `numeri delle carte`() {
        assertEquals("7", CardMatching.normNumber("007"))
        assertEquals("149", CardMatching.normNumber("149/147"))
        assertEquals("h14", CardMatching.normNumber("H14"))
        assertEquals("0", CardMatching.normNumber("000"))
        assertEquals("", CardMatching.normNumber(null))
    }
}
