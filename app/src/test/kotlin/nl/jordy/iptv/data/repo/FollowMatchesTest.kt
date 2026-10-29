package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.FollowMatchRow
import nl.jordy.iptv.data.repo.FollowMatches.Moment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A reminder must come in time, once, on a channel that can play it, and never nag. */
class FollowMatchesTest {

    private val now = 1_800_000_000L
    private fun row(stream: Int, title: String = "Formule 1: Race", start: Long = now + 240, archive: Int = 0, name: String = "Kanaal $stream") =
        FollowMatchRow("Formule 1", start, start + 7200, title, stream, name, "sport", archive, false)

    @Test
    fun `the same race on several channels is one reminder, on the channel with an archive`() {
        val m = FollowMatches.fold(listOf(row(1), row(2, archive = 3), row(3)))
        assertEquals(1, m.size)
        assertEquals(2, m.single().streamId)
    }

    @Test
    fun `different sessions stay apart, in time order`() {
        val m = FollowMatches.fold(listOf(row(1, "Formule 1: Race", start = now + 9000), row(1, "Formule 1: Kwalificatie", start = now + 100)))
        assertEquals(listOf("Formule 1: Kwalificatie", "Formule 1: Race"), m.map { it.title })
    }

    @Test
    fun `five minutes before, then at the start if ignored, then never again`() {
        val m = FollowMatches.fold(listOf(row(1, start = now + 240)))
        val key = m.single().key
        assertNull("too early", FollowMatches.due(m, now - 120, emptySet()))
        assertEquals(Moment.SOON, FollowMatches.due(m, now, emptySet())?.moment)
        assertNull("Later on the first banner waits for the start", FollowMatches.due(m, now + 60, setOf("$key:soon")))
        assertEquals(Moment.STARTED, FollowMatches.due(m, now + 240, setOf("$key:soon"))?.moment)
        assertNull(FollowMatches.due(m, now + 300, setOf("$key:soon", "$key:now")))
    }

    @Test
    fun `Kijken on the first banner means no second one`() {
        val m = FollowMatches.fold(listOf(row(1, start = now + 240)))
        assertNull(FollowMatches.due(m, now + 240, setOf("${m.single().key}:now")))
    }

    @Test
    fun `opening the app just after the start still offers it, not much later`() {
        val m = FollowMatches.fold(listOf(row(1, start = now - 120)))
        assertEquals(Moment.STARTED, FollowMatches.due(m, now, emptySet())?.moment)
        assertNull("ten minutes in is no longer a reminder", FollowMatches.due(m, now + 480, emptySet()))
    }
}
