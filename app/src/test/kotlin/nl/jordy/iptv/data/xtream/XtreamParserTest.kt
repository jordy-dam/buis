package nl.jordy.iptv.data.xtream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the parser against the same messy fixtures the mock server serves. */
class XtreamParserTest {

    private fun fixture(name: String): JsonElement =
        Json.parseToJsonElement(javaClass.getResource("/fixtures/$name")!!.readText().replace("{base}", "http://mock:8080"))

    private fun array(name: String) = fixture(name) as JsonArray

    @Test
    fun `account with string fields is recognised as active and authenticated`() {
        val a = XtreamParser.account(fixture("account.json"))
        assertTrue(a.authenticated)
        assertEquals("Active", a.status)
        assertEquals(1798761600L, a.expiresAt)
        assertEquals(2, a.maxConnections)
    }

    @Test
    fun `failed login response is not authenticated`() {
        assertFalse(XtreamParser.account(Json.parseToJsonElement("""{"user_info":{"auth":0}}""")).authenticated)
        assertFalse(XtreamParser.account(Json.parseToJsonElement("[]")).authenticated)
    }

    @Test
    fun `categories without id are skipped, nameless ones get a readable name`() {
        val cats = array("live_categories.json").mapNotNull(XtreamParser::category)
        assertEquals(listOf("1", "2", "3", "4", "5"), cats.map { it.id })
        assertEquals("Categorie 3", cats.first { it.id == "3" }.name)
    }

    @Test
    fun `live streams survive missing, null and mistyped fields`() {
        val streams = array("live_streams.json").mapNotNull(XtreamParser::liveStream)
        // 15 entries: no stream_id, "abc" id, a number and null are dropped.
        assertEquals(11, streams.size)
        assertEquals(102, streams.first { it.name == "NPO 2 HD" }.streamId)
        assertEquals("Zender 106", streams.first { it.streamId == 106 }.name)
        assertNull("empty icon", streams.first { it.streamId == 102 }.logoUrl)
        assertNull("http:// without host", streams.first { it.streamId == 104 }.logoUrl)
        assertEquals("http://mock:8080/img/logo0.png", streams.first { it.streamId == 101 }.logoUrl)
        assertNull(streams.first { it.streamId == 108 }.categoryId)
    }

    @Test
    fun `catch-up days come from tv_archive, whatever type the panel sends`() {
        fun days(json: String) = XtreamParser.liveStream(Json.parseToJsonElement(json))!!.archiveDays
        assertEquals(3, days("""{"stream_id": 1, "tv_archive": 1, "tv_archive_duration": "3"}"""))
        assertEquals("archive on, no duration: at least a day", 1, days("""{"stream_id": 1, "tv_archive": "1"}"""))
        assertEquals("a duration without the archive switched on means nothing", 0, days("""{"stream_id": 1, "tv_archive": 0, "tv_archive_duration": 7}"""))
        assertEquals(0, days("""{"stream_id": 1}"""))
    }

    @Test
    fun `the panel's time zone is read from server_info`() {
        val account = XtreamParser.account(Json.parseToJsonElement("""{"user_info": {"auth": 1}, "server_info": {"timezone": "Europe/Amsterdam"}}"""))
        assertEquals("Europe/Amsterdam", account.timezone)
    }

    @Test
    fun `movie rating, year and container fall back sensibly`() {
        val movies = array("vod_streams.json").mapNotNull(XtreamParser::movie).associateBy { it.streamId }
        assertEquals("the fixture's films, including an NL-sub version of 5001", 8, movies.size)
        assertEquals(7.5, movies.getValue(5001).rating!!, 0.001)
        assertEquals(2019, movies.getValue(5001).year)
        assertEquals("tmdb as a string and as a number", 9001, movies.getValue(5001).tmdbId)
        assertEquals(9001, movies.getValue(5101).tmdbId)
        assertEquals("no tmdb, no id", null, movies.getValue(5002).tmdbId)
        assertEquals(6.0, movies.getValue(5002).rating!!, 0.001)
        assertEquals("mkv", movies.getValue(5002).containerExtension)
        assertNull("empty rating", movies.getValue(5003).rating)
        assertEquals("rating_5based x2 when rating is junk", 8.0, movies.getValue(5004).rating!!, 0.001)
        assertEquals("missing extension defaults to mp4", "mp4", movies.getValue(5004).containerExtension)
        assertEquals(2021, movies.getValue(5005).year)
        assertNull("non-numeric added", movies.getValue(5005).addedAt)
        assertNull("not a url", movies.getValue(5006).posterUrl)
        assertEquals("Film 5007", movies.getValue(5007).name)
    }

    @Test
    fun `series backdrop accepts array, string and empty array`() {
        val series = array("series.json").mapNotNull(XtreamParser::series).associateBy { it.seriesId }
        assertEquals(3, series.size)
        assertEquals("http://mock:8080/img/backdrop2.png", series.getValue(7001).backdropUrl)
        assertNull(series.getValue(7002).backdropUrl)
        assertNull(series.getValue(7002).rating)
        assertEquals("http://mock:8080/img/backdrop3.png", series.getValue(7003).backdropUrl)
        assertEquals(2018, series.getValue(7003).year)
    }

    @Test
    fun `vod info with an empty array instead of an object still parses`() {
        val info = XtreamParser.movieInfo(fixture("vod_info_empty.json"))
        assertNull(info.plot)
        assertNull("blank extension is treated as absent", info.containerExtension)

        val full = XtreamParser.movieInfo(fixture("vod_info.json"))
        assertEquals(180, full.durationSecs)
        assertEquals(2019, full.year)
        assertEquals("http://mock:8080/img/backdrop0.png", full.backdropUrl)
    }

    @Test
    fun `episodes as a season map are flattened, ordered, and id-less ones dropped`() {
        val info = XtreamParser.seriesInfo(7001, fixture("series_info.json"))
        assertEquals(listOf(80001, 80002, 80003), info.episodes.map { it.episodeId })
        val e3 = info.episodes.last()
        assertEquals(2, e3.season)
        assertEquals("Aflevering 1", e3.title)
        assertEquals("mp4", e3.containerExtension)
        assertNull("info: [] instead of object", info.episodes[1].plot)
    }

    @Test
    fun `episodes as a list of lists get season numbers`() {
        val info = XtreamParser.seriesInfo(1, fixture("series_info_list_episodes.json"))
        assertEquals(listOf(1, 2), info.episodes.map { it.season })
        assertEquals("mkv", info.episodes.first().containerExtension)
    }

    @Test
    fun `short epg decodes base64, keeps plain text, drops entries without times`() {
        val epg = XtreamParser.shortEpg(fixture("short_epg.json"))
        assertEquals(listOf("NU OP TV", "Daarna"), epg.map { it.title })
        assertEquals("Het programma dat nu loopt.", epg.first().description)
        assertNull("empty description", epg[1].description)
        assertEquals("not base64 at all!!", XtreamParser.decodeBase64("not base64 at all!!"))
    }
}
