package nl.jordy.iptv.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XmltvCounterTest {

    private val now = XmltvCounter.parseTime("20261028190000 +0100")!!

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="npo1.nl"><display-name>NPO 1</display-name></channel>
          <channel id="rtl4.nl"><display-name>RTL 4</display-name></channel>
          <channel id="old.nl"><display-name>Old</display-name></channel>
          <programme start="20261028180000 +0100" stop="20261028193000 +0100" channel="npo1.nl"><title>Nu</title></programme>
          <programme start="20261029120000 +0100" stop="20261029130000 +0100" channel="rtl4.nl"><title>Morgen</title></programme>
          <programme start="20261020120000 +0100" stop="20261020130000 +0100" channel="old.nl"><title>Vorige week</title></programme>
          <programme start="garbage" stop="20261028193000" channel="broken.nl"><title>Kapot</title></programme>
        </tv>
    """.trimIndent()

    @Test
    fun `counts channels and programmes, and which channels have a guide for the next day`() {
        val (channels, programmes, withGuide) = XmltvCounter.count(xml.byteInputStream(), now)
        assertEquals(3, channels)
        assertEquals(4, programmes)
        assertEquals("only what's on now or within 24h counts", setOf("npo1.nl", "rtl4.nl"), withGuide)
    }

    @Test
    fun `xmltv times with and without an offset`() {
        assertEquals(XmltvCounter.parseTime("20261028180000 +0000")!! - 3600, XmltvCounter.parseTime("20261028180000 +0100"))
        assertEquals(XmltvCounter.parseTime("20261028180000 +0000"), XmltvCounter.parseTime("20261028180000"))
        assertNull(XmltvCounter.parseTime("garbage"))
    }
}
