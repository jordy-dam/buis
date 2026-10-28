package nl.jordy.iptv.player

import nl.jordy.iptv.player.ResumePolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePolicyTest {

    private val twoHours = 2 * 60 * 60 * 1000L

    @Test
    fun `a few seconds in is not worth resuming`() {
        assertEquals(Decision.Clear, ResumePolicy.onProgress(29_999, twoHours))
        assertEquals(Decision.Save(30_000), ResumePolicy.onProgress(30_000, twoHours))
    }

    @Test
    fun `reaching the credits counts as watched`() {
        val credits = (twoHours * 0.95).toLong()
        assertEquals(Decision.Save(credits - 1), ResumePolicy.onProgress(credits - 1, twoHours))
        assertEquals(Decision.Clear, ResumePolicy.onProgress(credits, twoHours))
    }

    @Test
    fun `unknown duration keeps any meaningful position`() {
        assertEquals(Decision.Save(3_600_000), ResumePolicy.onProgress(3_600_000, 0))
        assertEquals(Decision.Save(3_600_000), ResumePolicy.onProgress(3_600_000, -1))
    }

    @Test
    fun `start position resumes only what was saved for a reason`() {
        assertEquals(0L, ResumePolicy.startPosition(null, twoHours))
        assertEquals(0L, ResumePolicy.startPosition(10_000, twoHours))
        assertEquals(600_000L, ResumePolicy.startPosition(600_000, twoHours))
        assertEquals(0L, ResumePolicy.startPosition(twoHours, twoHours))
    }
}
