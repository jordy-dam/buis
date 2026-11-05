package nl.jordy.iptv.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveDelayTest {
    private val min = 60_000L

    @Test fun `a fresh install starts on Veilig, the old 30 s`() {
        assertEquals(LiveDelay.VEILIG, LiveDelay.of(null))
        assertEquals(30_000L, LiveDelay.of(null).offsetMs)
    }

    @Test fun `stalling steps Kort to Normaal to Veilig and no further`() {
        assertEquals(LiveDelay.NORMAAL, LiveDelay.KORT.safer())
        assertEquals(LiveDelay.VEILIG, LiveDelay.NORMAAL.safer())
        assertEquals(LiveDelay.VEILIG, LiveDelay.VEILIG.safer())
    }

    /** Plays [ms], then buffers 4 s (longer than a stall's 3 s), [n] times. */
    private fun LiveDelayStepper.stallTimes(n: Int, from: Long = 0): Pair<List<LiveDelay>, Long> {
        val left = mutableListOf<LiveDelay>()
        var t = from
        repeat(n) {
            tick(t, playing = true, buffering = false)?.let(left::add); t += 1_000
            for (b in 0..4) { tick(t, playing = false, buffering = true)?.let(left::add); t += 1_000 }
            tick(t, playing = true, buffering = false)?.let(left::add); t += 1_000
        }
        return left to t
    }

    @Test fun `on Kort, two stalls move to Normaal, two more to Veilig, and it stays there`() {
        val s = LiveDelayStepper(LiveDelay.KORT)
        val (first, t) = s.stallTimes(1)
        assertEquals("one stall can be bad luck", LiveDelay.KORT, s.delay)
        assertEquals(emptyList<LiveDelay>(), first)
        val (second, t2) = s.stallTimes(1, t)
        assertEquals(listOf(LiveDelay.KORT), second)
        assertEquals(LiveDelay.NORMAAL, s.delay)
        assertEquals("counting starts over at the new delay", 0, s.stalls)
        val (third, t3) = s.stallTimes(2, t2)
        assertEquals(listOf(LiveDelay.NORMAAL), third)
        assertEquals(LiveDelay.VEILIG, s.delay)
        val (fourth, _) = s.stallTimes(5, t3)
        assertEquals(emptyList<LiveDelay>(), fourth)
        assertEquals(LiveDelay.VEILIG, s.delay)
        assertEquals("Veilig keeps counting for the week line", 5, s.stalls)
    }

    @Test fun `short buffering while starting or zapping is no stall`() {
        val s = LiveDelayStepper(LiveDelay.KORT)
        for (t in 0L..10_000L step 1_000) s.tick(t, playing = false, buffering = true)
        assertEquals(0, s.stalls)
    }

    @Test fun `the setting cycles round so every choice is reachable with OK`() {
        assertEquals(LiveDelay.KORT, LiveDelay.VEILIG.next())
    }

    @Test fun `sessions add up per mode and the week line says time and stalls`() {
        var raw = ""
        raw = LiveDelayStats.add(raw, 100, LiveDelay.KORT, 30 * min, 1)
        raw = LiveDelayStats.add(raw, 100, LiveDelay.KORT, 40 * min, 2)
        raw = LiveDelayStats.add(raw, 101, LiveDelay.VEILIG, 120 * min, 0)
        assertEquals(
            "Kort 1 u 10 min, 3× gehaperd · Veilig 2 u, 0× gehaperd",
            LiveDelayStats.summary(LiveDelayStats.totals(raw, 101)),
        )
    }

    @Test fun `a day older than a week no longer counts and is dropped`() {
        var raw = LiveDelayStats.add("", 100, LiveDelay.KORT, 30 * min, 5)
        assertEquals(1, LiveDelayStats.totals(raw, 106).size)
        assertEquals(0, LiveDelayStats.totals(raw, 107).size)
        raw = LiveDelayStats.add(raw, 107, LiveDelay.NORMAAL, 30 * min, 0)
        assertEquals("107|NORMAAL|${30 * min}|0", raw)
    }

    @Test fun `less than a minute watched says nothing yet`() {
        val raw = LiveDelayStats.add("", 100, LiveDelay.KORT, 50_000, 0)
        assertNull(LiveDelayStats.summary(LiveDelayStats.totals(raw, 100)))
    }
}
