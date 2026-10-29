package nl.jordy.iptv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Track names must be readable Dutch whatever code the provider put on the stream. */
class TrackLabelsTest {

    @Test
    fun `two and three letter codes give the same Dutch name`() {
        assertEquals("Nederlands", TrackLabels.language("nl"))
        assertEquals("Nederlands", TrackLabels.language("nld"))
        assertEquals("Nederlands", TrackLabels.language("dut"))
        assertEquals("Engels", TrackLabels.language("eng"))
        assertEquals("Engels", TrackLabels.language("en-US"))
    }

    @Test
    fun `no code or undetermined has no language name`() {
        assertNull(TrackLabels.language(null))
        assertNull(TrackLabels.language(""))
        assertNull(TrackLabels.language("und"))
    }

    @Test
    fun `audio falls back to the stream's label, then to its position, and shows the channels`() {
        assertEquals("Nederlands · 5.1", TrackLabels.audio("nl", null, 6, 0))
        assertEquals("Audiodescriptie · Stereo", TrackLabels.audio("und", "Audiodescriptie", 2, 1))
        assertEquals("Spoor 3", TrackLabels.audio(null, null, 0, 2))
    }

    @Test
    fun `forced subtitles say they only translate`() {
        assertEquals("Nederlands (alleen vertaling)", TrackLabels.subtitle("nl", null, forced = true, index = 0))
        assertEquals("Ondertitels 2", TrackLabels.subtitle(null, " ", forced = false, index = 1))
    }

    @Test
    fun `profile defaults cycle and fall back for unknown values`() {
        assertEquals("nl", TrackLabels.next("", TrackLabels.AUDIO_CHOICES))
        assertEquals("", TrackLabels.next("en", TrackLabels.SUBTITLE_CHOICES))
        assertEquals("nl", TrackLabels.next("de", TrackLabels.SUBTITLE_CHOICES))
        assertEquals("Uit", TrackLabels.name("", TrackLabels.SUBTITLE_CHOICES))
    }
}
