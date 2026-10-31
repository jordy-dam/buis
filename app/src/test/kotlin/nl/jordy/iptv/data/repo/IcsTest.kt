package nl.jordy.iptv.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** A followed calendar must give each session the right time, or the reminder comes too late or never. */
class IcsTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val beforeMexico = 1_793_000_000L // 2026-10-26, before the Mexico weekend

    // As f1calendar.com serves it: CRLF, UTC times, a folded line.
    private val f1 = listOf(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "X-WR-CALNAME:F1 Calendar 2026",
        "BEGIN:VEVENT",
        "SUMMARY:F1: Grand Prix (Mexico City Grand Prix)",
        "DTSTART:20261101T200000Z",
        "DTEND:20261101T220000Z",
        "END:VEVENT",
        "BEGIN:VEVENT",
        "SUMMARY:F1: Qualifying (Mexico City ",
        " Grand Prix)",
        "DTSTART:20261031T210000Z",
        "DTEND:20261031T220000Z",
        "END:VEVENT",
        "BEGIN:VEVENT",
        "SUMMARY:F1: Grand Prix (Australian Grand Prix)",
        "DTSTART:20260308T040000Z",
        "DTEND:20260308T060000Z",
        "END:VEVENT",
        "END:VCALENDAR",
    ).joinToString("\r\n")

    @Test
    fun `UTC sessions keep their moment, in time order, without what's over`() {
        val e = Ics.parse(f1, beforeMexico, amsterdam)
        assertEquals(listOf("F1: Qualifying (Mexico City Grand Prix)", "F1: Grand Prix (Mexico City Grand Prix)"), e.map { it.title })
        assertEquals("22:00 in Amsterdam is 21:00 UTC", 1_793_480_400L, e.first().start)
        assertEquals(e.first().start + 3600, e.first().stop)
    }

    @Test
    fun `a time in a named zone, a floating time and a missing end`() {
        val text = listOf(
            "BEGIN:VCALENDAR",
            "BEGIN:VEVENT", "SUMMARY:Ajax - PSV", "DTSTART;TZID=Europe/Amsterdam:20261108T143000", "END:VEVENT",
            "BEGIN:VEVENT", "SUMMARY:Feyenoord - AZ", "DTSTART:20261108T163000", "DTEND:20261108T183000", "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\n")
        val e = Ics.parse(text, beforeMexico, amsterdam)
        assertEquals("14:30 CET is 13:30 UTC", 1_794_144_600L, e[0].start)
        assertEquals("no DTEND: two hours", e[0].start + Ics.DEFAULT_LENGTH_SEC, e[0].stop)
        assertEquals("floating times are the tv's zone", 1_794_151_800L, e[1].start)
    }

    @Test
    fun `an alarm's own text is not the match's title`() {
        // As FotMob serves a club's matches.
        val text = listOf(
            "BEGIN:VCALENDAR", "BEGIN:VEVENT", "DTSTART:20261101T133000Z", "DTEND:20261101T153000Z", "SUMMARY:⚽️ Telstar - FC Utrecht",
            "BEGIN:VALARM", "TRIGGER:-PT15M", "ACTION:DISPLAY", "SUMMARY:⚽️ Telstar - FC Utrecht starting in 15 minutes", "END:VALARM",
            "END:VEVENT", "END:VCALENDAR",
        ).joinToString("\r\n")
        val e = Ics.parse(text, beforeMexico, amsterdam).single()
        assertEquals("⚽️ Telstar - FC Utrecht", e.title)
        assertEquals(1_793_539_800L, e.start)
    }

    @Test
    fun `all-day appointments get no reminder`() {
        val text = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Testdag\nDTSTART;VALUE=DATE:20261105\nEND:VEVENT\nEND:VCALENDAR"
        assertTrue(Ics.parse(text, beforeMexico, amsterdam).isEmpty())
    }

    @Test
    fun `escaped text reads normally`() {
        val text = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Sprint\\, kwalificatie\\; race\nDTSTART:20261107T143000Z\nEND:VEVENT\nEND:VCALENDAR"
        assertEquals("Sprint, kwalificatie; race", Ics.parse(text, beforeMexico, amsterdam).single().title)
    }

    @Test
    fun `the calendar's name, and whether a page is a calendar at all`() {
        assertEquals("F1 Calendar 2026", Ics.name(f1))
        assertTrue(Ics.looksLikeCalendar(f1))
        assertFalse(Ics.looksLikeCalendar("<!DOCTYPE html><html>"))
    }

    @Test
    fun `webcal links and bare hosts become https`() {
        assertEquals("https://f1calendar.com/x.ics", Ics.normalizeUrl(" webcal://f1calendar.com/x.ics "))
        assertEquals("https://f1calendar.com/x.ics", Ics.normalizeUrl("f1calendar.com/x.ics"))
        assertEquals("http://lan.local/a.ics", Ics.normalizeUrl("http://lan.local/a.ics"))
    }
}
