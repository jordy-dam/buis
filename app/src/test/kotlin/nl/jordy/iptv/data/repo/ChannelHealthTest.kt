package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.ChannelCheckEntity
import nl.jordy.iptv.data.db.LiveChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zenderstatus may only point at a channel that really hiccups, and should suggest the version of
 * it that ran well, not one that hiccups too.
 */
class ChannelHealthTest {

    private fun ch(id: Int, name: String, badges: String = "") = LiveChannelEntity("1", id, name, null, null, 0, 1, badges)
    private fun check(id: Int, stalls: Int = 0, failed: Boolean = false) = ChannelCheckEntity(streamId = id, at = 0, playedSeconds = 600, stalls = stalls, failed = failed)

    @Test
    fun `a stall counts once per stretch and only after the stream played`() {
        val d = StallDetector()
        // Buffering while starting up is not a stall.
        for (t in 0..5_000L step 500) d.tick(t, playing = false, buffering = true)
        assertEquals(0, d.stalls)
        d.tick(5_500, playing = true, buffering = false)
        for (t in 6_000..12_000L step 500) d.tick(t, playing = false, buffering = true)
        assertEquals("one long stretch is one stall", 1, d.stalls)
        d.tick(12_500, playing = true, buffering = false)
        for (t in 13_000..14_000L step 500) d.tick(t, playing = false, buffering = true)
        assertEquals("a short dip isn't", 1, d.stalls)
    }

    @Test
    fun `half or more troubled out of three sessions is troubled, less is not`() {
        val stats = ChannelAdvice.stats(listOf(check(1, stalls = 2), check(1, failed = true), check(1), check(2, stalls = 1), check(2), check(2), check(2)))
        assertTrue(ChannelAdvice.troubled(stats.getValue(1)))
        assertEquals(ChannelAdvice.Stats(4, 1), stats.getValue(2))
        assertEquals(false, ChannelAdvice.troubled(stats.getValue(2)))
    }

    @Test
    fun `a steady sibling is proposed over an untried one, a troubled one never`() {
        val hd = ch(1, "NPO 1", "HD")
        val fhd = ch(2, "NPO 1", "FHD")
        val sd = ch(3, "NPO 1", "SD")
        val raw = ch(4, "NPO 1")
        val checks = List(3) { check(1, stalls = 1) } + List(5) { check(2) } + List(3) { check(4, failed = true) }
        val all = listOf(hd, fhd, sd, raw).associateBy { it.streamId }
        val p = ChannelAdvice.proposals(ChannelAdvice.stats(checks), all) { all.values.toList() }
        val forHd = p.first { it.channel.streamId == 1 }
        assertEquals(fhd, forHd.sibling)
        assertEquals(ChannelAdvice.Stats(5, 0), forHd.siblingStats)
        // The failing raw version gets the steady FHD too, never another troubled one.
        assertEquals(fhd, p.first { it.channel.streamId == 4 }.sibling)
    }

    @Test
    fun `without a steady sibling an untried one is a suggestion, without any there is none`() {
        val hd = ch(1, "RTL 4", "HD")
        val sd = ch(3, "RTL 4", "SD")
        val checks = List(3) { check(1, failed = true) }
        val p = ChannelAdvice.proposals(ChannelAdvice.stats(checks), mapOf(1 to hd, 3 to sd)) { listOf(hd, sd) }.single()
        assertEquals(sd, p.sibling)
        assertNull(p.siblingStats)
        val alone = ChannelAdvice.proposals(ChannelAdvice.stats(checks), mapOf(1 to hd)) { listOf(hd) }.single()
        assertNull(alone.sibling)
        assertEquals("RTL 4 · HD", ChannelAdvice.label(hd))
    }
}
