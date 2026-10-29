package nl.jordy.iptv.player

import nl.jordy.iptv.data.db.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The next episode must be the right one, and binge-watching must not run on while nobody watches. */
class AutoplayTest {

    private fun ep(id: Int, season: Int, number: Int) = EpisodeEntity(id, 1, season, number, "E$id", "mkv", null, null, null)

    // The provider's order is not to be trusted: ids and list order say nothing about the episode order.
    private val episodes = listOf(ep(30, 2, 1), ep(11, 1, 2), ep(10, 1, 1), ep(12, 1, 3), ep(31, 2, 2))

    @Test
    fun `next is the following episode in the same season`() {
        assertEquals(12, Autoplay.next(episodes, 11)!!.episodeId)
    }

    @Test
    fun `after the last episode of a season the next season starts`() {
        assertEquals(30, Autoplay.next(episodes, 12)!!.episodeId)
    }

    @Test
    fun `nothing after the last episode of the last season`() {
        assertNull(Autoplay.next(episodes, 31))
    }

    @Test
    fun `card shows in the last 20 seconds, not before`() {
        val duration = 45 * 60_000L
        assertFalse(Autoplay.cardDue(duration - 21_000, duration, ended = false))
        assertTrue(Autoplay.cardDue(duration - 20_000, duration, ended = false))
    }

    @Test
    fun `without a duration the card waits for the end`() {
        assertFalse(Autoplay.cardDue(10 * 60_000L, 0, ended = false))
        assertTrue(Autoplay.cardDue(10 * 60_000L, 0, ended = true))
    }

    @Test
    fun `the third episode in a row without a key asks first`() {
        assertFalse(Autoplay.asksFirst(1))
        assertFalse(Autoplay.asksFirst(2))
        assertTrue(Autoplay.asksFirst(3))
    }
}
