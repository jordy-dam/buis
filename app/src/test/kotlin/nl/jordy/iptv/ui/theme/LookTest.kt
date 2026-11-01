package nl.jordy.iptv.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** A profile's stored look must always map to something wearable, and existing profiles keep the default. */
class LookTest {

    @Test
    fun `a new or migrated profile wears the standard look`() {
        assertEquals(Look.STANDARD, Look.of(0))
    }

    @Test
    fun `an index the app no longer knows falls back to the standard look`() {
        assertEquals(Look.STANDARD, Look.of(Look.entries.size))
        assertEquals(Look.STANDARD, Look.of(-1))
    }

    @Test
    fun `garage is stored as 1`() {
        // The index is persisted per profile: reordering the enum would repaint people's profiles.
        assertEquals(Look.GARAGE, Look.of(1))
    }

    @Test
    fun `garage keeps the 911 for profiles that never picked a car`() {
        // 0 is what the migration gives every profile: Garage profiles had the 911 before the choice.
        assertEquals(GarageCar.PORSCHE, GarageCar.of(0))
        assertEquals(GarageCar.PORSCHE, GarageCar.of(GarageCar.entries.size))
    }
}
