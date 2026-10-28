package nl.jordy.iptv.data.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamServerTest {

    @Test
    fun `server urls as people paste them all resolve to the server root`() {
        val expected = "http://tv.example.com:8080/"
        listOf(
            "tv.example.com:8080",
            "http://tv.example.com:8080",
            "  http://tv.example.com:8080/  ",
            "http://tv.example.com:8080/player_api.php?username=a&password=b",
            "HTTP://tv.example.com:8080/get.php",
        ).forEach { assertEquals(it, expected, normalizeServerUrl(it).toString()) }
    }

    @Test
    fun `a sub path is kept, https is kept`() {
        assertEquals("https://x.example/iptv", normalizeServerUrl("https://x.example/iptv/player_api.php").toString())
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("http://"))
    }

    @Test
    fun `stream urls follow the xtream layout and escape credentials`() {
        val c = XtreamCredentials("http://h:80", "user", "p@ss/word")
        val base = c.baseUrl!!
        assertEquals("http://h/live/user/p@ss%2Fword/12.m3u8", StreamUrls.liveHls(base, c, 12))
        assertEquals("http://h/live/user/p@ss%2Fword/12.ts", StreamUrls.liveTs(base, c, 12))
        assertEquals("http://h/movie/user/p@ss%2Fword/5.mkv", StreamUrls.movie(base, c, 5, "mkv"))
        assertEquals("http://h/series/user/p@ss%2Fword/9.mp4", StreamUrls.episode(base, c, 9, ""))
    }

    @Test
    fun `credentials never appear in toString`() {
        val s = XtreamCredentials("http://h", "secretuser", "secretpass").toString()
        assert(!s.contains("secretuser") && !s.contains("secretpass")) { s }
    }
}
