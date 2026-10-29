package nl.jordy.iptv.player

import nl.jordy.iptv.player.BreakTimer.Placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The break must come after an hour of actual watching, at a moment that doesn't cut into a scene
 * when it can avoid it, and never twice when people already took a break themselves.
 */
class BreakTimerTest {

    private val min = 60_000L

    /** Plays from [from] to [to] with a tick every 500 ms, like the player does. */
    private fun BreakTimer.play(from: Long, to: Long) {
        var t = from
        while (t <= to) { tick(t, playing = true); t += 500 }
    }

    private fun BreakTimer.pause(from: Long, to: Long) {
        var t = from
        while (t <= to) { tick(t, playing = false); t += 500 }
    }

    @Test
    fun `off never breaks`() {
        val t = BreakTimer(0)
        t.play(0, 300 * min)
        assertEquals(Placement.NONE, t.placement(remainingMs = 60 * min, hasNext = false))
    }

    @Test
    fun `counts playing time only, not paused time`() {
        val t = BreakTimer(60)
        t.play(0, 40 * min)
        t.pause(40 * min + 500, 42 * min) // two minutes paused: not a break, not watching either
        t.play(42 * min + 500, 61 * min)
        assertFalse("40 + 19 minutes watched", t.due)
        t.play(61 * min + 500, 63 * min)
        assertTrue(t.due)
    }

    @Test
    fun `episodes add up across autoplay`() {
        val t = BreakTimer(60)
        // Three 25-minute episodes, a few seconds of loading in between.
        t.play(0, 25 * min)
        t.pause(25 * min + 500, 25 * min + 5_000)
        t.play(25 * min + 5_500, 50 * min)
        assertFalse(t.due)
        t.pause(50 * min + 500, 50 * min + 5_000)
        t.play(50 * min + 5_500, 65 * min)
        assertTrue(t.due)
    }

    @Test
    fun `a pause of three minutes you took yourself is the break`() {
        val t = BreakTimer(60)
        t.play(0, 55 * min)
        t.pause(55 * min + 500, 58 * min + 500)
        t.play(58 * min + 1_000, 70 * min)
        assertFalse("count started again after the 3-minute pause", t.due)
    }

    @Test
    fun `a film pauses right away, unless it ends within 15 minutes`() {
        val t = BreakTimer(60)
        t.play(0, 60 * min)
        assertEquals(Placement.NOW, t.placement(remainingMs = 50 * min, hasNext = false))
        assertEquals(Placement.SKIP, t.placement(remainingMs = 10 * min, hasNext = false))
        assertEquals("no duration: can't know, so now", Placement.NOW, t.placement(remainingMs = null, hasNext = false))
    }

    @Test
    fun `an episode with a next one waits for its end when that's near`() {
        val t = BreakTimer(60)
        t.play(0, 60 * min)
        assertEquals(Placement.AT_END, t.placement(remainingMs = 12 * min, hasNext = true))
        assertEquals(Placement.AT_END, t.placement(remainingMs = null, hasNext = true))
        assertEquals("a long episode: don't wait 40 minutes", Placement.NOW, t.placement(remainingMs = 40 * min, hasNext = true))
    }

    @Test
    fun `after a break the count starts again, so a long evening gets one every hour`() {
        val t = BreakTimer(60)
        t.play(0, 60 * min)
        assertTrue(t.due)
        t.taken()
        assertFalse(t.due)
        t.play(60 * min + 500, 119 * min)
        assertFalse(t.due)
        t.play(119 * min + 500, 120 * min + 500)
        assertTrue(t.due)
    }

    @Test
    fun `Instellingen cycles through the choices and back to off`() {
        assertEquals(45, BreakTimer.nextChoice(0))
        assertEquals(0, BreakTimer.nextChoice(90))
        assertEquals(45, BreakTimer.nextChoice(1)) // the debug choice in a release build: restart the cycle
        assertEquals(1, BreakTimer.nextChoice(0, BreakTimer.DEBUG_CHOICES))
    }

    @Test
    fun `Pauzeduur is one minute by default and stays between 1 and 15`() {
        assertEquals(60, BreakTimer(60).lengthSecs)
        assertEquals(5 * 60, BreakTimer(60, 5).lengthSecs)
        assertEquals("a broken value never means no break or an endless one", 60, BreakTimer(60, 0).lengthSecs)
        assertEquals(15 * 60, BreakTimer(60, 99).lengthSecs)
        assertEquals(2, BreakTimer.nextChoice(1, BreakTimer.LENGTHS))
        assertEquals(1, BreakTimer.nextChoice(15, BreakTimer.LENGTHS))
    }
}
