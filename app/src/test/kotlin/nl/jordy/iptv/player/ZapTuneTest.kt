package nl.jordy.iptv.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** A quick run of channel steps must open one stream, not one per step; a single step must not wait. */
class ZapTuneTest {

    @Test
    fun `a first press opens at once`() {
        assertEquals(0L, ZapTune.delayMs(null, 10_000))
    }

    @Test
    fun `a press on its own opens at once`() {
        assertEquals(0L, ZapTune.delayMs(10_000, 10_000 + ZapTune.ISOLATED_MS))
    }

    @Test
    fun `a press in a run waits for the next one`() {
        assertEquals(ZapTune.RUN_DELAY_MS, ZapTune.delayMs(10_000, 10_300))
        assertEquals(ZapTune.RUN_DELAY_MS, ZapTune.delayMs(10_000, 10_000 + ZapTune.ISOLATED_MS - 1))
    }

    @Test
    fun `a clock that went back never makes a step wait`() {
        assertEquals(0L, ZapTune.delayMs(10_000, 9_000))
    }
}
