package nl.jordy.iptv.player

import nl.jordy.iptv.data.db.LiveChannelEntity
import nl.jordy.iptv.data.xtream.EpgEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Vanaf begin" must only show where the panel can deliver it, or the button leads to an error. */
class CatchupTest {

    private val now = 1_800_000_000L
    private fun channel(days: Int, mark: Boolean = false) = LiveChannelEntity("1", 7, "Viaplay F1", null, null, 0, 0, catchup = mark, archiveDays = days)
    private val race = EpgEntry("Formule 1: Race", null, now - 30 * 60, now + 90 * 60)

    @Test
    fun `offered for a running programme on a channel with an archive`() {
        assertTrue(Catchup.canRestart(channel(3), race, now))
    }

    @Test
    fun `not on a channel without an archive`() {
        assertFalse(Catchup.canRestart(channel(0), race, now))
    }

    @Test
    fun `the catch-up mark in the name counts as an archive`() {
        assertTrue(Catchup.canRestart(channel(0, mark = true), race, now))
    }

    @Test
    fun `not without a guide entry or a channel`() {
        assertFalse(Catchup.canRestart(channel(3), null, now))
        assertFalse(Catchup.canRestart(null, race, now))
    }

    @Test
    fun `not for a programme that started before the archive reaches back`() {
        val marathon = EpgEntry("Lange uitzending", null, now - 2 * 86_400, now + 600)
        assertFalse(Catchup.canRestart(channel(1), marathon, now))
        assertTrue(Catchup.canRestart(channel(3), marathon, now))
    }

    @Test
    fun `asks for the whole programme, in whole minutes`() {
        assertEquals(120, Catchup.minutes(race))
        assertEquals(46, Catchup.minutes(EpgEntry("x", null, 0, 45 * 60 + 1)))
    }
}
