package nl.jordy.iptv.ui.detail

import nl.jordy.iptv.data.db.EpisodeEntity
import nl.jordy.iptv.data.db.PlaybackPositionEntity
import nl.jordy.iptv.data.xtream.ContentType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The series' main button must offer what you'd watch next, not episode 1 of what you've seen. */
class NextUpTest {

    private val eps = (1..5).map { EpisodeEntity(it, 10, 1, it, "A$it", "mkv", null, null, 2700) }
    private fun pos(id: Int, at: Long) = PlaybackPositionEntity(1, ContentType.SERIES, id, 60_000, 2_700_000, at)

    @Test
    fun `halfway through one wins, else the first unseen after the last seen, else the first`() {
        assertEquals(1, nextUp(eps, emptyMap())?.number)
        assertEquals("seen 1 and 2", 3, nextUp(eps, emptyMap(), setOf(1, 2))?.number)
        assertEquals("seen out of order: after the last one in the series' order", 5, nextUp(eps, emptyMap(), setOf(1, 4))?.number)
        assertEquals("halfway through 2 beats the seen ones", 2, nextUp(eps, mapOf(2 to pos(2, 5)), setOf(1, 3))?.number)
        assertEquals("all seen: back to the start", 1, nextUp(eps, emptyMap(), (1..5).toSet())?.number)
    }
}
