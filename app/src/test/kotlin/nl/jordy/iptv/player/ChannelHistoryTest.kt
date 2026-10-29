package nl.jordy.iptv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Left in the player must always bring back the channel you came from, and Left again the one you left. */
class ChannelHistoryTest {

    private val f1 = ChannelHistory.Entry(7, "Viaplay F1", "sport")
    private val espn = ChannelHistory.Entry(12, "ESPN 1", "sport")
    private val npo = ChannelHistory.Entry(1, "NPO 1", "nl")

    @Test
    fun `nothing to go back to on the first channel`() {
        val h = ChannelHistory()
        h.watched(f1)
        assertNull(h.previous)
    }

    @Test
    fun `going back twice returns to where you were`() {
        val h = ChannelHistory()
        h.watched(f1)
        h.watched(espn)
        h.watched(h.previous!!)
        assertEquals(f1, h.current)
        assertEquals(espn, h.previous)
        h.watched(h.previous!!)
        assertEquals(espn, h.current)
        assertEquals(f1, h.previous)
    }

    @Test
    fun `the previous channel keeps its own category`() {
        val h = ChannelHistory()
        h.watched(npo)
        h.watched(f1)
        assertEquals("nl", h.previous!!.categoryId)
    }

    @Test
    fun `restarting the same channel does not lose the previous one`() {
        val h = ChannelHistory()
        h.watched(f1)
        h.watched(espn)
        h.watched(espn)
        assertEquals(f1, h.previous)
    }

    @Test
    fun `zapping through channels remembers only the one just before`() {
        val h = ChannelHistory()
        h.watched(npo)
        h.watched(f1)
        h.watched(espn)
        assertEquals(f1, h.previous)
    }
}
