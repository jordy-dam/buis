package nl.jordy.iptv.data.profile

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Bedtijd: once an evening, at a time the profile picks, BUIS asks "doorkijken of lekker naar bed?".
 * An evening runs from noon to 06:00 the next morning, so 00:00 and 00:30 belong to the evening
 * before and "zondag" means Sunday night. Days are the evenings it's on, as a bitmask with
 * Monday = bit 0 … Sunday = bit 6.
 */
object Bedtime {
    /** Minutes after midnight; -1 is "Uit". Late choices wrap past midnight. */
    val TIMES = listOf(-1, 21 * 60, 21 * 60 + 30, 22 * 60, 22 * 60 + 30, 23 * 60, 23 * 60 + 30, 0)

    /** The evening before a work or school day, every weekday evening, every evening. */
    val DAY_SETS = listOf(
        mask(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY) to "Zo–do",
        mask(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) to "Ma–vr",
        mask(*DayOfWeek.entries.toTypedArray()) to "Elke dag",
    )
    val DEFAULT_DAYS = DAY_SETS.first().first

    fun mask(vararg days: DayOfWeek): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

    fun nextTime(current: Int): Int = TIMES[(TIMES.indexOf(current).coerceAtLeast(0) + 1) % TIMES.size]
    fun nextDays(current: Int): Int = DAY_SETS[(DAY_SETS.indexOfFirst { it.first == current }.coerceAtLeast(0) + 1) % DAY_SETS.size].first
    fun daysName(mask: Int): String = DAY_SETS.firstOrNull { it.first == mask }?.second ?: "Eigen keuze"
    fun timeName(minutes: Int): String = if (minutes < 0) "Uit" else "%02d:%02d".format(minutes / 60, minutes % 60)

    /**
     * True when the card is due now: the profile has a bedtime, tonight is one of its evenings, the
     * time has come and it's not morning yet, and it wasn't shown this evening ([shownFor]).
     */
    fun due(now: LocalDateTime, minutes: Int, days: Int, shownFor: LocalDate?): Boolean {
        if (minutes < 0) return false
        // Daytime is nobody's bedtime.
        if (now.hour in 6 until 12) return false
        val evening = eveningOf(now)
        if (shownFor == evening) return false
        if (days and (1 shl (evening.dayOfWeek.value - 1)) == 0) return false
        val at = LocalTime.of(minutes / 60, minutes % 60)
        val start = if (at >= LocalTime.NOON) evening.atTime(at) else evening.plusDays(1).atTime(at)
        return !now.isBefore(start)
    }

    /** The evening [now] belongs to: from noon on it's today's, before noon still yesterday's. */
    fun eveningOf(now: LocalDateTime): LocalDate = if (now.hour >= 12) now.toLocalDate() else now.toLocalDate().minusDays(1)
}
