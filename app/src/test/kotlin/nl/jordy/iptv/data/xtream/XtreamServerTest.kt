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
    fun `timeshift starts in the panel's time zone, not the tv's`() {
        val c = XtreamCredentials("http://h", "u", "p")
        // F1 race 15:00 in Amsterdam on 1 November (winter time, UTC+1).
        val start = java.time.ZonedDateTime.of(2026, 11, 1, 15, 0, 0, 0, java.time.ZoneId.of("Europe/Amsterdam")).toEpochSecond()
        assertEquals("http://h/timeshift/u/p/120/2026-11-01:15-00/7.ts", StreamUrls.timeshift(c.baseUrl!!, c, 7, start, 120, java.time.ZoneId.of("Europe/Amsterdam")))
        // A panel in London asks for the same moment as 14:00.
        assertEquals("http://h/timeshift/u/p/120/2026-11-01:14-00/7.ts", StreamUrls.timeshift(c.baseUrl!!, c, 7, start, 120, java.time.ZoneId.of("Europe/London")))
    }

    @Test
    fun `credentials never appear in toString`() {
        val s = XtreamCredentials("http://h", "secretuser", "secretpass").toString()
        assert(!s.contains("secretuser") && !s.contains("secretpass")) { s }
    }
}
