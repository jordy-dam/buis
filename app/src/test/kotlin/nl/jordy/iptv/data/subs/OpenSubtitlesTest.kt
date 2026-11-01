package nl.jordy.iptv.data.subs

import kotlinx.serialization.json.Json
import nl.jordy.iptv.player.TrackLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSubtitlesTest {
    private val api = OpenSubtitles.API

    @Test fun `a film with a TMDB id is searched by that id alone, parameters sorted`() {
        // A title next to the id narrows OpenSubtitles' match to files whose name happens to agree.
        val url = OpenSubtitles.searchUrl(api, SubtitleQuery.Film("The Batman", 2022, 414906), listOf("nl", "en", "nl"))
        assertEquals("https://api.opensubtitles.com/api/v1/subtitles?languages=en%2Cnl&tmdb_id=414906&type=movie", url.toString())
    }

    @Test fun `a film without an id goes by its lower-case title and year`() {
        // Any other spelling gets a redirect first; sorted, lower case is what the API asks for.
        val url = OpenSubtitles.searchUrl(api, SubtitleQuery.Film("Zwartboek", 2006, null), listOf("nl"))
        assertEquals(listOf("languages", "query", "type", "year"), url.queryParameterNames.toList())
        assertEquals("zwartboek", url.queryParameter("query"))
        assertEquals("2006", url.queryParameter("year"))
    }

    @Test fun `a film whose TMDB id finds nothing is asked again by title, then without the year`() {
        // Panels send wrong or zero TMDB ids; stopping at the id left the list empty on the tv.
        val film = SubtitleQuery.Film("Zwartboek", 2006, 123)
        assertEquals(
            listOf(film, film.copy(tmdbId = null), film.copy(tmdbId = null, year = null)),
            OpenSubtitles.attempts(film),
        )
        assertEquals(listOf(SubtitleQuery.Film("X", null, null)), OpenSubtitles.attempts(SubtitleQuery.Film("X", null, 0)))
    }

    @Test fun `an episode goes by series, season and number`() {
        val url = OpenSubtitles.searchUrl(api, SubtitleQuery.Episode("Penoza", 2, 5), listOf("nl"))
        assertEquals(listOf("episode_number", "languages", "query", "season_number", "type"), url.queryParameterNames.toList())
        assertEquals("episode", url.queryParameter("type"))
    }

    @Test fun `per language the two most downloaded files, in the order asked`() {
        val json = Json.parseToJsonElement(
            """
            {"data":[
              {"attributes":{"language":"en","download_count":900,"files":[{"file_id":1}]}},
              {"attributes":{"language":"nl","download_count":10,"files":[{"file_id":2}]}},
              {"attributes":{"language":"nl","download_count":"300","release":"Film.WEB-DL","files":[{"file_id":3}]}},
              {"attributes":{"language":"nl","download_count":50,"files":[{"file_id":4}]}},
              {"attributes":{"language":"de","download_count":999,"files":[{"file_id":5}]}},
              {"attributes":{"language":"nl","download_count":999,"files":[]}}
            ]}
            """,
        )
        // Dutch first because the profile asked for it; German wasn't asked; a hit without a file can't be fetched.
        val best = OpenSubtitlesParser.best(json, listOf("nl", "en"))
        assertEquals(listOf(3, 4, 1), best.map { it.fileId })
        assertEquals("Film.WEB-DL", best.first().release)
    }

    @Test fun `an answer without data finds nothing instead of failing`() {
        assertTrue(OpenSubtitlesParser.best(Json.parseToJsonElement("""{"message":"x"}"""), listOf("nl")).isEmpty())
    }

    @Test fun `two Dutch files differ by the video they were timed to, a number only when that is the same`() {
        // Whether subtitles run in sync depends on the release, so that's what tells them apart.
        assertEquals("Nederlands · WEB", TrackLabels.online("nl", TrackLabels.source("Film.2022.1080p.WEB-DL.x264-GRP"), 0))
        assertEquals("Nederlands · BluRay", TrackLabels.online("nl", TrackLabels.source("film 2022 BluRay 720p"), 0))
        assertEquals("Nederlands · BluRay 2", TrackLabels.online("nl", "BluRay", 1))
        assertEquals("Engels", TrackLabels.online("en", TrackLabels.source("Film (2022)"), 0))
        // "ts" inside a word is no telesync.
        assertEquals(null, TrackLabels.source("Pets.2016.HDR"))
        assertEquals("TV", TrackLabels.source("show.s01e02.hdtv.x264"))
    }

    @Test fun `a fetched file keeps its own name and its mark next to the stream's Dutch track`() {
        val label = TrackLabels.ONLINE + "Nederlands · WEB"
        assertEquals("Nederlands · WEB", TrackLabels.subtitle("nl", label, false, 3))
        assertEquals("Nederlands", TrackLabels.subtitle("nl", "Dutch", false, 0))
    }

    @Test fun `the password and API key never show in logs`() {
        val text = OpenSubtitlesLogin("sleutel123", "jordy", "geheim").toString()
        assertTrue("geheim" !in text && "sleutel123" !in text)
    }
}
