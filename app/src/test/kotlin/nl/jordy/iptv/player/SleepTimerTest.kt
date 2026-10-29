package nl.jordy.iptv.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** The sleep timer must stop a forgotten tv, but never someone who is still pressing keys. */
class SleepTimerTest {

    private val min = 60_000L

    @Test
    fun `off never stops`() {
        assertEquals(SleepTimer.State.Running, SleepTimer(0, 0).state(10 * 60 * min))
    }

    @Test
    fun `warns in the last minute, then stops`() {
        val t = SleepTimer(30, 0)
        assertEquals(SleepTimer.State.Running, t.state(29 * min - 1))
        assertEquals(SleepTimer.State.Warning(60), t.state(29 * min))
        assertEquals(SleepTimer.State.Warning(1), t.state(30 * min - 500))
        assertEquals(SleepTimer.State.Expired, t.state(30 * min))
    }

    @Test
    fun `a key press starts the count again`() {
        val t = SleepTimer(30, 0)
        t.activity(29 * min + 30_000)
        assertEquals(SleepTimer.State.Running, t.state(30 * min))
        assertEquals(SleepTimer.State.Expired, t.state(59 * min + 30_000))
    }

    @Test
    fun `Instellingen cycles through the choices and back to off`() {
        assertEquals(30, SleepTimer.nextChoice(0))
        assertEquals(0, SleepTimer.nextChoice(120))
        assertEquals(30, SleepTimer.nextChoice(45)) // unknown value from an older build: restart the cycle
    }
}
