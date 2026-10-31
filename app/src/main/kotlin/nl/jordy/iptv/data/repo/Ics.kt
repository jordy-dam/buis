package nl.jordy.iptv.data.repo

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One appointment from a followed calendar: a session, a match. */
data class IcsEvent(val start: Long, val stop: Long, val title: String)

/**
 * Just enough of iCalendar (RFC 5545) for the sports calendars people subscribe to: each VEVENT's
 * SUMMARY, DTSTART and DTEND. All-day appointments are skipped, since a reminder needs a time.
 */
object Ics {
    /** Without a DTEND a session counts as two hours, about a race. */
    const val DEFAULT_LENGTH_SEC = 2 * 3600L

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** The calendar's own name (X-WR-CALNAME), if it has one. */
    fun name(text: String): String? = lines(text).firstOrNull { it.startsWith("X-WR-CALNAME") }?.substringAfter(':')?.let(::unescape)?.trim()?.ifEmpty { null }

    fun looksLikeCalendar(text: String) = text.trimStart('\uFEFF', ' ', '\r', '\n').startsWith("BEGIN:VCALENDAR")

    /** Appointments that aren't over at [nowSec], in time order. Times without a zone are [zone]'s. */
    fun parse(text: String, nowSec: Long, zone: ZoneId = ZoneId.systemDefault()): List<IcsEvent> {
        val out = mutableListOf<IcsEvent>()
        var inEvent = false
        var title: String? = null
        var start: Long? = null
        var stop: Long? = null
        var allDay = false
        // A VALARM inside an event has a SUMMARY of its own ("starting in 15 minutes"): not the title.
        var nested = 0
        for (line in lines(text)) {
            when {
                line == "BEGIN:VEVENT" -> { inEvent = true; nested = 0; title = null; start = null; stop = null; allDay = false }
                inEvent && line.startsWith("BEGIN:") -> nested++
                inEvent && line.startsWith("END:") && line != "END:VEVENT" -> nested--
                line == "END:VEVENT" -> {
                    val s = start
                    if (inEvent && s != null && !allDay && title != null) {
                        val e = stop?.takeIf { it > s } ?: (s + DEFAULT_LENGTH_SEC)
                        if (e > nowSec) out += IcsEvent(s, e, title)
                    }
                    inEvent = false
                }
                !inEvent || nested > 0 -> Unit
                line.startsWith("SUMMARY") -> title = unescape(line.substringAfter(':')).trim().ifEmpty { null }
                line.startsWith("DTSTART") -> time(line, zone).let { start = it.first; allDay = it.second }
                line.startsWith("DTEND") -> stop = time(line, zone).first
            }
        }
        return out.sortedBy { it.start }
    }

    /** Lines with folding undone: a line starting with a space or tab continues the one before. */
    private fun lines(text: String): List<String> {
        val out = mutableListOf<StringBuilder>()
        for (raw in text.split('\n')) {
            val line = raw.removeSuffix("\r")
            if ((line.startsWith(' ') || line.startsWith('\t')) && out.isNotEmpty()) out.last().append(line, 1, line.length)
            else out += StringBuilder(line)
        }
        return out.map { it.toString() }
    }

    /** "DTSTART:20261031T210000Z", "DTSTART;TZID=Europe/Amsterdam:…", "DTSTART;VALUE=DATE:20261031" (all day). */
    private fun time(line: String, zone: ZoneId): Pair<Long?, Boolean> {
        val params = line.substringBefore(':')
        val value = line.substringAfter(':').trim()
        return runCatching {
            if ("VALUE=DATE" in params && "VALUE=DATE-TIME" !in params || value.length == 8) {
                LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(zone).toEpochSecond() to true
            } else {
                val local = LocalDateTime.parse(value.removeSuffix("Z"), stamp)
                val at = when {
                    value.endsWith("Z") -> ZoneOffset.UTC
                    "TZID=" in params -> runCatching { ZoneId.of(params.substringAfter("TZID=").substringBefore(';').trim('"')) }.getOrDefault(zone)
                    else -> zone
                }
                local.atZone(at).toEpochSecond() to false
            }
        }.getOrDefault(null to false)
    }

    private fun unescape(s: String) = s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    /** What people paste: webcal:// is https underneath, and a bare host gets https. */
    fun normalizeUrl(input: String): String {
        val t = input.trim()
        return when {
            t.startsWith("webcal://", ignoreCase = true) -> "https://" + t.substring(9)
            t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
            else -> "https://$t"
        }
    }
}
