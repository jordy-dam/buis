package nl.jordy.iptv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The intro end must come from a deliberate skip at the start, settle on where people actually
 * kept watching, and never from ordinary seeking later in an episode.
 */
class IntroSkipTest {

    private fun IntroLearner.play(from: Long, to: Long): Long? {
        var learned: Long? = null
        var t = from
        while (t <= to) { tick(t, playing = true)?.let { learned = it }; t += 500 }
        return learned
    }

    @Test
    fun `skipping forward at the start and watching on learns the landing spot`() {
        val l = IntroLearner()
        l.seeked(fromMs = 20_000, toMs = 50_000)
        l.seeked(fromMs = 50_000, toMs = 80_000)
        assertNull("not settled yet", l.play(0, 29_000))
        assertEquals(80_000L, l.play(29_500, 31_000))
        assertNull("learned once", l.play(31_500, 60_000))
    }

    @Test
    fun `seeking back after an overshoot refines the spot`() {
        val l = IntroLearner()
        l.seeked(20_000, 110_000)
        l.seeked(110_000, 100_000)
        assertEquals(100_000L, l.play(0, 31_000))
    }

    @Test
    fun `a seek later in the episode, or one that isn't followed by watching, teaches nothing`() {
        val late = IntroLearner()
        late.seeked(fromMs = 25 * 60_000L, toMs = 26 * 60_000L)
        assertNull(late.play(0, 60_000))

        val restless = IntroLearner()
        restless.seeked(20_000, 50_000)
        restless.play(0, 20_000)
        restless.seeked(70_000, 100_000) // still seeking: the clock starts again
        assertNull(restless.play(20_500, 40_000))
    }

    @Test
    fun `the jump is offered before the learned end, in the first minutes only`() {
        assertTrue(IntroLearner.offer(90_000, 10_000))
        assertFalse("almost there", IntroLearner.offer(90_000, 86_000))
        assertFalse("nothing learned", IntroLearner.offer(null, 10_000))
    }
}
