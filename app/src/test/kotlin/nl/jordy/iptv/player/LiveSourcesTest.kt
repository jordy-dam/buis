package nl.jordy.iptv.player

import nl.jordy.iptv.data.xtream.XtreamCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSourcesTest {

    private val creds = XtreamCredentials("http://h:8080", "u", "p")
    private val sources = LiveSources(creds.baseUrl!!, creds)

    @Test
    fun `hls first, ts after an hls failure, then give up`() {
        val first = sources.first(7)
        assertTrue(first.isHls)
        assertEquals("http://h:8080/live/u/p/7.m3u8", first.url)

        val second = sources.fallback(7, first)!!
        assertFalse(second.isHls)
        assertEquals("http://h:8080/live/u/p/7.ts", second.url)

        assertNull("both formats failed: show an error, don't loop", sources.fallback(7, second))
    }

    @Test
    fun `a channel whose hls failed goes straight to ts when zapped back to`() {
        sources.fallback(7, sources.first(7))
        assertFalse(sources.first(7).isHls)
        assertTrue("other channels are unaffected", sources.first(8).isHls)
    }
}
