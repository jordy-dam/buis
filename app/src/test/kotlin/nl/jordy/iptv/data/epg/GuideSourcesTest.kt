package nl.jordy.iptv.data.epg

import nl.jordy.iptv.data.db.EpgProgrammeEntity
import nl.jordy.iptv.data.db.LiveGuideRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class GuideSourcesTest {

    private fun p(channel: String, start: Long, stop: Long, title: String) = EpgProgrammeEntity(channel, start, stop, title, null)

    @Test
    fun `the preferred source wins where guides disagree, the provider only fills gaps`() {
        // SBS6, 2026-10-30: the provider missed the late-night change, open-epg had it.
        val open = listOf(p("sbs6.nl", 2356, 2429, "True Crime Today"), p("sbs6.nl", 2429, 2525, "De Bondgenoten"))
        val provider = listOf(
            p("sbs6.nl", 2300, 2355, "Shownieuws"),
            p("sbs6.nl", 2355, 2449, "De Bondgenoten"),
            p("sbs6.nl", 2525, 2600, "Hart van Nederland"),
            p("npo1.nl", 2300, 2400, "Alleen bij de provider"),
        )
        val got = GuideSources.merge(listOf(open, provider)).sortedWith(compareBy({ it.channelId }, { it.start }))
        assertEquals(
            listOf("Alleen bij de provider", "Shownieuws", "True Crime Today", "De Bondgenoten", "Hart van Nederland"),
            got.map { it.title },
        )
        assertEquals("never two programmes at once", 2429L, got.first { it.title == "De Bondgenoten" }.start)
    }

    @Test
    fun `source channels match by id ignoring case, else by name, and names only for Dutch streams`() {
        val rows = listOf(
            LiveGuideRow(1, "SBS 6", "sbs6.nl", "NL"),
            LiveGuideRow(2, "VIAPLAY TV", null, "NL"),
            LiveGuideRow(3, "VIAPLAY TV+", null, "NL"),
            LiveGuideRow(4, "ZIGGO SPORT 6", null, null), // pinned pack without a country
            LiveGuideRow(5, "ARTE", null, "DE"),
            LiveGuideRow(6, "NPO 1 HD", "npo1.nl", "NL"),
        )
        val ids = mapOf(1 to "sbs6.nl", 6 to "npo1.nl")
        val got = GuideSources.match(
            rows, ids,
            mapOf(
                "SBS6.nl" to listOf("SBS 6"),
                "Viaplay.TV.nl" to emptyList(), // only the id says which channel
                "ViaplayTVplus.nl" to listOf("Viaplay TV+"),
                "ZiggoSport6.nl" to listOf("Ziggo Sport 6"),
                "Arte.nl" to listOf("Arte"),
                "NPO.1.nl" to listOf("NPO 1"),
                "Onbekend.nl" to listOf("Onbekend"),
            ),
        )
        assertEquals(
            mapOf(
                "SBS6.nl" to setOf("sbs6.nl"),
                "Viaplay.TV.nl" to setOf("naam:viaplaytv"),
                "ViaplayTVplus.nl" to setOf("naam:viaplaytvplus"),
                "ZiggoSport6.nl" to setOf("naam:ziggosport6"),
                "NPO.1.nl" to setOf("npo1.nl"),
            ),
            got,
        )
    }

    @Test
    fun `reads channel names and stops at the programmes`() {
        val xml = """
            <tv>
              <channel id="SBS6.nl"><display-name lang="nl">SBS 6</display-name><display-name>SBS6</display-name></channel>
              <channel id="Leeg.nl"></channel>
              <programme start="20261030000000 +0100" stop="20261030010000 +0100" channel="SBS6.nl"><title>x</title></programme>
              <channel id="Te.laat.nl"><display-name>Te laat</display-name></channel>
            </tv>
        """.trimIndent()
        assertEquals(mapOf("SBS6.nl" to listOf("SBS 6", "SBS6"), "Leeg.nl" to emptyList()), GuideSources.channelNames(xml.byteInputStream()))
    }

    @Test
    fun `refreshes after 20 hours, or once after the open guides publish at 18_30`() {
        val zone = ZoneId.of("Europe/Amsterdam")
        fun at(h: Int, m: Int, day: Int = 30) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
        assertFalse("fetched this morning, still afternoon", XmltvGuide.isStale(at(9, 0), at(17, 0), zone))
        assertTrue("fetched this morning, now evening", XmltvGuide.isStale(at(9, 0), at(19, 0), zone))
        assertFalse("already fetched this evening", XmltvGuide.isStale(at(18, 40), at(23, 0), zone))
        assertFalse("fetched last night, early morning", XmltvGuide.isStale(at(19, 0, 29), at(8, 0), zone))
        assertTrue("over 20 hours", XmltvGuide.isStale(at(15, 0, 29), at(12, 0), zone))
    }
}
