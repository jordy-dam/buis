package nl.jordy.iptv.data.epg

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.EpgChannelEntity
import nl.jordy.iptv.data.db.LiveGuideRow
import nl.jordy.iptv.data.repo.EpgRepository
import nl.jordy.iptv.data.xtream.XtreamClient
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XmltvGuideTest {

    private val now = XmltvCounter.parseTime("20261028190000 +0100")!!
    private val from = now - 2 * 3600
    private val to = now + 36 * 3600

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="npo1.nl"><display-name>NPO 1</display-name></channel>
          <programme start="20261028183000 +0100" stop="20261028190000 +0100" channel="npo1.nl"><title>Eerder</title></programme>
          <programme start="20261028190000 +0100" stop="20261028193000 +0100" channel="npo1.nl"><title lang="nl">Journaal</title><desc>Het nieuws.</desc></programme>
          <programme start="20261028183000 +0000" stop="20261028190000 +0000" channel="npo1.nl"><title>Daarna</title></programme>
          <programme start="20261028190000 +0100" stop="20261028193000 +0100" channel="npo1.nl"><title>Dubbel</title></programme>
          <programme start="20261020120000 +0100" stop="20261020130000 +0100" channel="npo1.nl"><title>Vorige week</title></programme>
          <programme start="20261028190000 +0100" stop="20261028200000 +0100" channel="cnn.us"><title>Niet gevraagd</title></programme>
          <programme start="kapot" stop="20261028200000 +0100" channel="npo1.nl"><title>Kapot</title></programme>
          <programme start="20261028200000 +0100" stop="20261028210000 +0100" channel="npo1.nl"><title> </title></programme>
        </tv>
    """.trimIndent()

    @Test
    fun `keeps only wanted channels inside the window, skips broken and duplicate programmes`() {
        val got = XmltvGuide.read(xml.byteInputStream(), setOf("npo1.nl"), from, to)
        assertEquals(listOf("Eerder", "Journaal", "Daarna"), got.map { it.title })
        assertEquals("Het nieuws.", got[1].description)
        assertEquals("+0000 is an hour later than +0100", now + 1800, got[2].start)
    }

    @Test
    fun `a channel without an EPG id borrows its sibling's, only within the same country`() {
        val rows = listOf(
            LiveGuideRow(1, "NPO 1", "npo1.nl", "NL"),
            LiveGuideRow(2, "NPO 1", null, "NL"), // was "NPO 1 HD", cleaned at sync
            LiveGuideRow(3, "npo 1 ", null, "NL"),
            LiveGuideRow(4, "NPO 1", null, "BE"),
            LiveGuideRow(5, "Onbekend", null, "NL"),
        )
        val byStream = XmltvGuide.channels(rows).associate { it.streamId to it.channelId }
        assertEquals(mapOf(1 to "npo1.nl", 2 to "npo1.nl", 3 to "npo1.nl"), byStream)
    }

    @Test
    fun `now and next come from the stored guide, the stop time is exclusive`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.epg().insertProgrammes(XmltvGuide.read(xml.byteInputStream(), setOf("npo1.nl"), from, to))
        db.epg().insertChannels(listOf(EpgChannelEntity(101, "npo1.nl"), EpgChannelEntity(102, "npo1.nl")))
        val repo = EpgRepository(XtreamClient(OkHttpClient()), CredentialStore(context), db)

        val (onNow, next) = repo.nowAndNext(101, nowSec = now)
        assertEquals("at 19:00 the 18:30–19:00 programme is over", "Journaal", onNow?.title)
        assertEquals("Daarna", next?.title)
        assertEquals("an aliased stream sees the same guide", "Journaal", repo.nowAndNext(102, nowSec = now + 60).first?.title)

        // Not logged in, no guide: nothing, and no crash.
        assertNull(repo.nowAndNext(999, nowSec = now).first)
        db.close()
    }

    @Test
    fun `a provider-sized guide (200k programmes) is read within the time budget`() {
        val channels = 4000
        val xmlBig = buildString {
            append("<tv>")
            repeat(channels) { c ->
                repeat(50) { i ->
                    val start = 20261028000000L + i * 3000 // well-formed enough: hh mm advance in steps
                    append("<programme start=\"$start +0100\" stop=\"${start + 3000} +0100\" channel=\"c$c\"><title>Programma $i</title><desc>Omschrijving van programma $i op kanaal $c.</desc></programme>")
                }
            }
            append("</tv>")
        }
        val wanted = (0 until 600).map { "c$it" }.toSet() // about the NL share
        val started = System.nanoTime()
        val got = XmltvGuide.read(xmlBig.byteInputStream(), wanted, 0, Long.MAX_VALUE)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("kept only wanted channels", got.all { it.channelId in wanted })
        assertTrue("took ${ms}ms", ms < 5_000)
    }
}
