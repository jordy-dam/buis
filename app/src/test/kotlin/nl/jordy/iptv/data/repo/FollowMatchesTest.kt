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
        FollowMatchRow("Formule 1", start, start + 7200, title, stream, name, "sport", archive, false, null)

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
    fun `a block of back-to-back programmes is one item, from the first start to the last stop`() {
        fun z(t: String, from: Long, to: Long) = FollowMatchRow("Zappelin", from, to, t, 3, "NPO 3", "nl", 0, false, null)
        val m = FollowMatches.fold(
            listOf(
                z("Zappelin: Nijntje", now, now + 600),
                z("Zappelin: Dikkie Dik", now + 600, now + 1200),
                z("Zappelin: Bing", now + 1200, now + 1800),
                z("Zappelin: Nijntje", now + 36_000, now + 36_600), // the evening block
            ),
        )
        assertEquals(2, m.size)
        assertEquals(now to now + 1800, m[0].start to m[0].stop)
        assertEquals(3, m[0].parts)
        assertEquals(now + 36_000, m[1].start)
    }

    @Test
    fun `Home shows the first item per term only`() {
        val m = FollowMatches.fold(
            listOf(
                row(1, "Formule 1: Kwalificatie", start = now + 100),
                row(1, "Formule 1: Race", start = now + 90_000),
                FollowMatchRow("FC Utrecht", now + 5000, now + 11000, "KNVB Beker", 12, "ESPN 1", "sport", 0, false, null),
            ),
        )
        assertEquals(listOf("Formule 1: Kwalificatie", "KNVB Beker"), FollowMatches.nextPerTerm(m).map { it.title })
    }

    @Test
    fun `every one-off reminder shows on Home, and a followed programme that is also reminded shows once`() {
        val followed = FollowMatches.fold(listOf(row(1, "Formule 1: Race", start = now + 100)))
        val race = followed.single().copy(oneOff = true)
        val other = race.copy(title = "Darts", streamId = 9, start = now + 50)
        val merged = FollowMatches.merge(followed, listOf(race, other))
        assertEquals(listOf("Darts", "Formule 1: Race"), merged.map { it.title })
        assertEquals(2, FollowMatches.nextPerTerm(merged).size)
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
