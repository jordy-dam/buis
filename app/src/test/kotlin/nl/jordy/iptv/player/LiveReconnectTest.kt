package nl.jordy.iptv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A dropped live stream gets a few tries back, then ends with a message instead of looping forever. */
class LiveReconnectTest {

    @Test
    fun `tries with growing waits, then gives up`() {
        val r = LiveReconnect()
        assertEquals(LiveReconnect.DELAYS_MS, List(LiveReconnect.DELAYS_MS.size) { r.next() })
        assertNull(r.next())
    }

    @Test
    fun `a minute of steady playing earns the tries back`() {
        val r = LiveReconnect()
        repeat(LiveReconnect.DELAYS_MS.size) { r.next() }
        r.tick(0, playing = true)
        r.tick(LiveReconnect.STEADY_MS, playing = true)
        assertEquals(LiveReconnect.DELAYS_MS.first(), r.next())
    }

    @Test
    fun `a stream that drops every few seconds still ends`() {
        val r = LiveReconnect()
        var now = 0L
        repeat(LiveReconnect.DELAYS_MS.size) {
            r.tick(now, playing = true)
            r.tick(now + 20_000, playing = true)
            r.tick(now + 20_500, playing = false)
            r.next()
            now += 30_000
        }
        assertNull(r.next())
    }
}
