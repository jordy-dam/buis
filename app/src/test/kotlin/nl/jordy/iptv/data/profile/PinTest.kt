package nl.jordy.iptv.data.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {

    @Test
    fun `the right PIN opens the profile, a wrong one doesn't`() {
        val stored = Pin.hash("4821")
        assertTrue(Pin.verify("4821", stored))
        assertFalse(Pin.verify("4812", stored))
        assertFalse(Pin.verify("", stored))
    }

    @Test
    fun `the PIN itself is never stored, and equal PINs don't look equal`() {
        val a = Pin.hash("1234")
        val b = Pin.hash("1234")
        assertFalse(a.contains("1234"))
        assertNotEquals("two profiles with the same PIN can't be spotted in the database", a, b)
    }

    @Test
    fun `a damaged hash never unlocks`() {
        assertFalse(Pin.verify("1234", "garbage"))
        assertFalse(Pin.verify("1234", ""))
    }
}
