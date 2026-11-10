package nl.jordy.iptv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The thumbs belong to the end credits of a real film: not halfway, not after a trailer. */
class FilmThumbsTest {
    private val film = 120 * 60_000L

    @Test
    fun `come with the credits, at the point the resume position is cleared`() {
        assertFalse(FilmThumbs.due(positionMs = film / 2, durationMs = film, ended = false))
        assertFalse(FilmThumbs.due(positionMs = (film * 0.94).toLong(), durationMs = film, ended = false))
        assertTrue(FilmThumbs.due(positionMs = (film * 0.95).toLong(), durationMs = film, ended = false))
    }

    @Test
    fun `a film without a known duration is asked about when it ends`() {
        assertFalse(FilmThumbs.due(positionMs = 90 * 60_000L, durationMs = 0, ended = false))
        assertTrue(FilmThumbs.due(positionMs = 90 * 60_000L, durationMs = 0, ended = true))
    }

    @Test
    fun `short videos are never asked about`() {
        assertFalse(FilmThumbs.due(positionMs = 3 * 60_000L, durationMs = 3 * 60_000L, ended = true))
    }
}
