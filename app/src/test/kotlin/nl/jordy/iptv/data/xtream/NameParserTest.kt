package nl.jordy.iptv.data.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shapes taken from a real 105k-item catalog (2026-10-28), with made-up titles. */
class NameParserTest {

    private fun p(raw: String, stripQuality: Boolean = true) = NameParser.parse(raw, stripQuality)

    @Test
    fun `unicode bar country tag is split off and becomes the country`() {
        val n = p("┃NL┃ Kill Bill: Vol. 1")
        assertEquals("Kill Bill: Vol. 1", n.title)
        assertEquals("NL", n.country)
        assertEquals(emptyList<String>(), n.badges)
    }

    @Test
    fun `ascii bars, brackets, a stray space and a missing opening bar all work`() {
        assertEquals("NL" to "NPO 1", p("|NL| NPO 1").let { it.country to it.title })
        assertEquals("NL" to "NPO 1", p("[NL] NPO 1").let { it.country to it.title })
        assertEquals("DE" to "Tatort", p("DE┃ Tatort").let { it.country to it.title })
        assertEquals("BE" to "Een", p("BE: Een").let { it.country to it.title })
        assertEquals("Multi", p("┃MULTI ┃ Conni & Co.").badges.single())
    }

    @Test
    fun `variant country codes land in one group`() {
        assertEquals("US", p("┃USA┃ CNN").country)
        assertEquals("US", p("┃US┃ The Movie").country)
        assertEquals("US", p("|USA┃ Mixed bars").country)
        assertEquals("GB", p("┃UK┃ BBC One").country)
        assertEquals("EN", p("┃EN┃ Some Series").country)
    }

    @Test
    fun `multi lingo becomes a badge, not part of the title`() {
        val n = p("┃MULTI / LINGO┃ APPLE TV+", stripQuality = false)
        assertEquals("APPLE TV+", n.title)
        assertNull(n.country)
        assertEquals(listOf("Multi"), n.badges)
        assertEquals(listOf("Multi"), p("|LINGO| Show").badges)
    }

    @Test
    fun `country with language and dutch sub or audio keep both facts`() {
        assertEquals("CA" to listOf("FR"), p("┃CA FR┃ TVA").let { it.country to it.badges })
        assertEquals("NL" to listOf("NL sub"), p("┃NL-SUB┃ Film").let { it.country to it.badges })
        assertEquals("NL" to listOf("NL audio"), p("┃NL-AUDIO┃ Film").let { it.country to it.badges })
    }

    @Test
    fun `non-country tags stay visible as a badge`() {
        val n = p("┃PPV┃ UFC 300")
        assertNull(n.country)
        assertEquals(listOf("PPV"), n.badges)
        assertEquals("UFC 300", n.title)
    }

    @Test
    fun `quality suffixes become badges, plain HD is dropped as noise`() {
        assertEquals("NPO 1" to emptyList<String>(), p("┃NL┃ NPO 1 HD").let { it.title to it.badges })
        assertEquals("NPO 1" to listOf("FHD"), p("┃NL┃ NPO 1 FHD").let { it.title to it.badges })
        assertEquals("ESPN" to listOf("4K", "HEVC"), p("┃NL┃ ESPN 4K HEVC").let { it.title to it.badges })
        assertEquals("Ziggo" to listOf("4K"), p("┃NL┃ Ziggo UHD").let { it.title to it.badges })
    }

    @Test
    fun `tags at the end are split off too, years stay in the title`() {
        val ziggo = p("┃UCL┃ ZIGGO SPORT 1 HD [NL]")
        assertEquals("ZIGGO SPORT 1" to "NL", ziggo.title to ziggo.country)
        assertEquals(listOf("UCL"), ziggo.badges)
        assertEquals("Frozen" to listOf("NL gesproken"), p("┃MULTI┃ Frozen (NL GESPROKEN)").let { it.title to it.badges.minus("Multi") })
        assertEquals("NL", p("Frozen (NL GESPROKEN)").country)
        assertEquals("Dune" to listOf("Dolby"), p("┃NL┃ Dune [DOLBY]").let { it.title to it.badges })
        assertEquals("Shogun (2024)", p("┃EN┃ Shogun (2024)").title)
        assertEquals("F1 Race" to listOf("UK audio"), p("F1 Race [UK AUDIO]").let { it.title to it.badges })
        // Unknown trailing brackets are part of the title.
        assertEquals("Mission: Impossible (Director's Cut)", p("Mission: Impossible (Director's Cut)").title)
    }

    @Test
    fun `category names keep their quality words`() {
        assertEquals("NEDERLAND 4K ULTRA", p("┃NL┃ NEDERLAND 4K ULTRA", stripQuality = false).title)
    }

    @Test
    fun `catch-up mark sets the flag and leaves the title`() {
        val n = p("┃NL┃ TV WEST ⏺ʳᵉᶜ")
        assertTrue(n.catchup)
        assertEquals("TV WEST", n.title)
        assertTrue(p("┃NL┃ NEDERLAND HD | TERUGKIJKEN ⏺", stripQuality = false).catchup)
        assertFalse(p("┃NL┃ NPO 2").catchup)
    }

    @Test
    fun `separator pseudo-channels lose their decoration`() {
        assertEquals("PPV LIVE EVENTS", p("==== PPV LIVE EVENTS ====").title)
        assertEquals("24/7", p("|| ===== 24/7 ===== ||", stripQuality = false).title)
    }

    @Test
    fun `untagged names and things that only look like tags are left alone`() {
        assertEquals("RELAX TV", p("RELAX TV").title)
        assertNull(p("RELAX TV").country)
        // A title that happens to start with a short word and a colon is not a country.
        assertEquals("WWE: Raw Highlights", p("WWE: Raw Highlights").title)
        assertEquals("Blockbuster - 2013", p("┃MULTI┃ Blockbuster - 2013").title)
        assertEquals("(500) Days of Summer" to emptyList<String>(), p("(500) Days of Summer").let { it.title to it.badges })
    }

    @Test
    fun `never returns an empty title`() {
        assertEquals("┃NL┃", p("┃NL┃").title)
    }

    @Test
    fun `country names are dutch`() {
        assertEquals("Nederland", NameParser.countryName("NL"))
        assertEquals("Verenigd Koninkrijk", NameParser.countryName("GB"))
        assertEquals("Engelstalig", NameParser.countryName("EN"))
    }

    @Test
    fun `adult categories are recognised by whole words only`() {
        listOf("ADULT ---", "   ===== XXX ===== ", "┃18+┃ Nachtprogramma", "|ADULTS| VOD", "For adults").forEach {
            assertEquals(it, true, NameParser.isAdult(it))
        }
        listOf("┃NL┃ ALGEMEEN", "ADULTSWIM", "MIDDLESEX", "┃UK┃ XXXL Sports", "Top 180+").forEach {
            assertEquals(it, false, NameParser.isAdult(it))
        }
    }
}
