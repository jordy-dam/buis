package nl.jordy.iptv.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Bedtijd must come once on the right evenings, also when the time is past midnight, and never
 * during the day or on a free evening.
 */
class BedtimeTest {

    private val schoolNights = Bedtime.DEFAULT_DAYS // Sunday to Thursday evenings
    private val wednesday = LocalDate.of(2026, 10, 28)
    private val friday = LocalDate.of(2026, 10, 30)
    private val sunday = LocalDate.of(2026, 11, 1)

    @Test
    fun `due from the bedtime on, on a school night`() {
        assertFalse(Bedtime.due(wednesday.atTime(22, 59), 23 * 60, schoolNights, null))
        assertTrue(Bedtime.due(wednesday.atTime(23, 0), 23 * 60, schoolNights, null))
        assertTrue("still due later that night", Bedtime.due(wednesday.plusDays(1).atTime(1, 30), 23 * 60, schoolNights, null))
    }

    @Test
    fun `not on a free evening, and Friday after midnight still counts as Friday`() {
        assertFalse(Bedtime.due(friday.atTime(23, 30), 23 * 60, schoolNights, null))
        assertFalse(Bedtime.due(friday.plusDays(1).atTime(0, 30), 23 * 60, schoolNights, null))
        assertTrue("Sunday night is a school night", Bedtime.due(sunday.atTime(23, 5), 23 * 60, schoolNights, null))
    }

    @Test
    fun `a midnight bedtime belongs to the evening before`() {
        assertFalse(Bedtime.due(wednesday.atTime(23, 59), 0, schoolNights, null))
        assertTrue(Bedtime.due(wednesday.plusDays(1).atTime(0, 0), 0, schoolNights, null))
        assertEquals(wednesday, Bedtime.eveningOf(wednesday.plusDays(1).atTime(0, 10)))
    }

    @Test
    fun `once an evening, off never, never in the daytime`() {
        assertFalse(Bedtime.due(wednesday.atTime(23, 30), 23 * 60, schoolNights, shownFor = wednesday))
        assertTrue("next evening again", Bedtime.due(wednesday.plusDays(1).atTime(23, 30), 23 * 60, schoolNights, shownFor = wednesday))
        assertFalse(Bedtime.due(wednesday.atTime(23, 30), -1, schoolNights, null))
        assertFalse(Bedtime.due(LocalDateTime.of(2026, 10, 29, 9, 0), 23 * 60, schoolNights, null))
    }

    @Test
    fun `Instellingen cycles time and days`() {
        assertEquals(21 * 60, Bedtime.nextTime(-1))
        assertEquals(-1, Bedtime.nextTime(0))
        assertEquals("23:00", Bedtime.timeName(23 * 60))
        assertEquals("00:00", Bedtime.timeName(0))
        assertEquals("Ma–vr", Bedtime.daysName(Bedtime.nextDays(Bedtime.DEFAULT_DAYS)))
    }
}
