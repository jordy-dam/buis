package nl.jordy.iptv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live that slid minutes behind by itself goes back to live; the usual start margin and a segment's wobble don't. */
class LiveEdgeTest {

    @Test
    fun `HLS at its usual distance stays where it is`() {
        val e = LiveEdge()
        assertFalse(e.tick(0, playing = true, buffering = false, fromEdgeMs = 26_000))
        // Playlist refreshes move the edge by a segment.
        assertFalse(e.tick(5_000, playing = true, buffering = false, fromEdgeMs = 31_000))
        assertFalse(e.tick(10_000, playing = true, buffering = false, fromEdgeMs = 26_000 + LiveEdge.DRIFT_MS))
    }

    @Test
    fun `HLS that slid further behind than it started goes back to live`() {
        val e = LiveEdge()
        e.tick(0, playing = true, buffering = false, fromEdgeMs = 26_000)
        assertTrue(e.tick(60_000, playing = true, buffering = false, fromEdgeMs = 26_001 + LiveEdge.DRIFT_MS))
    }

    @Test
    fun `nothing happens while it isn't playing`() {
        val e = LiveEdge()
        e.tick(0, playing = true, buffering = false, fromEdgeMs = 26_000)
        assertFalse(e.tick(60_000, playing = false, buffering = true, fromEdgeMs = 59_000))
    }

    @Test
    fun `TS counts the time it stalled after it started`() {
        val e = LiveEdge()
        // Opening takes a while: not a stall.
        e.tick(0, playing = false, buffering = true, fromEdgeMs = null)
        e.tick(5_000, playing = true, buffering = false, fromEdgeMs = null)
        e.tick(10_000, playing = false, buffering = true, fromEdgeMs = null)
        assertFalse(e.tick(25_000, playing = true, buffering = false, fromEdgeMs = null))
        e.tick(30_000, playing = false, buffering = true, fromEdgeMs = null)
        assertTrue(e.tick(36_000, playing = true, buffering = false, fromEdgeMs = null))
    }

    @Test
    fun `reset starts counting again`() {
        val e = LiveEdge()
        e.tick(0, playing = true, buffering = false, fromEdgeMs = 26_000)
        e.reset()
        assertFalse(e.tick(1_000, playing = true, buffering = false, fromEdgeMs = 50_000))
    }
}
