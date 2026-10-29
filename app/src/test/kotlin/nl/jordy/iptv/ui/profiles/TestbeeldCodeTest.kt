package nl.jordy.iptv.ui.profiles

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The easter egg must need the whole code, and survive an extra Up from someone who starts over. */
class TestbeeldCodeTest {

    private fun TestbeeldCode.type(keys: List<Int>) = keys.map { feed(it) }.last()

    @Test
    fun `the whole code opens it, part of it doesn't`() {
        assertTrue(TestbeeldCode().type(TestbeeldCode.SEQUENCE))
        assertFalse(TestbeeldCode().type(TestbeeldCode.SEQUENCE.dropLast(1) + KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun `a wrong key starts over, and Up can start a new attempt right away`() {
        val c = TestbeeldCode()
        c.type(listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(c.type(TestbeeldCode.SEQUENCE))
    }

    @Test
    fun `ordinary use of the picker never opens it`() {
        val c = TestbeeldCode()
        val browsing = listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        assertFalse(browsing.any { c.feed(it) })
    }
}
