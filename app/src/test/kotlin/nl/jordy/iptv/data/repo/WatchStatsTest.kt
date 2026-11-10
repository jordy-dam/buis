package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.WatchLogEntity
import nl.jordy.iptv.data.xtream.ContentType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The Kijkgeschiedenis numbers must match what the household would count themselves. */
class WatchStatsTest {

    private val zone = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 11, 10)
    private fun at(day: LocalDate, h: Int) = day.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun row(type: ContentType, title: String, day: LocalDate, h: Int, minutes: Int, detail: String? = null, category: String? = null) =
        WatchLogEntity(profileId = 1, type = type, itemId = title.hashCode(), title = title, detail = detail, containerExtension = "", categoryId = category, startedAt = at(day, h), seconds = minutes * 60)

    @Test
    fun `sport is found by channel name or by the category it was played from`() {
        val cats = mapOf("7" to "┃NL┃ SPORT")
        assertEquals(WatchKind.SPORT, WatchKind.of(row(ContentType.LIVE, "ZIGGO SPORT 1", today, 20, 5), null))
        assertEquals(WatchKind.SPORT, WatchKind.of(row(ContentType.LIVE, "VIAPLAY FORMULE 1", today, 20, 5), null))
        assertEquals(WatchKind.SPORT, WatchKind.of(row(ContentType.LIVE, "Kanaal 12", today, 20, 5, category = "7"), cats["7"]))
        assertEquals(WatchKind.NEWS, WatchKind.of(row(ContentType.LIVE, "NOS Journaal 24", today, 20, 5), null))
        assertEquals(WatchKind.LIVE, WatchKind.of(row(ContentType.LIVE, "NPO 1", today, 20, 5), null))
        assertEquals("a film about sport is still a film", WatchKind.FILMS, WatchKind.of(row(ContentType.MOVIE, "Sports Night", today, 20, 5), null))
    }

    @Test
    fun `this week against last week, kinds, matches counted once`() {
        val rows = listOf(
            row(ContentType.LIVE, "ESPN", today.minusDays(2), 20, 120, detail = "FC Utrecht - N.E.C."),
            row(ContentType.LIVE, "ESPN", today.minusDays(2), 22, 10, detail = "FC Utrecht - N.E.C."), // same match, back after a zap
            row(ContentType.LIVE, "ESPN 2", today.minusDays(2), 16, 45, detail = "SC Cambuur - PSV"),
            row(ContentType.LIVE, "ESPN", today.minusDays(2), 19, 8, detail = "ESPN Live"),
            row(ContentType.MOVIE, "Film", today.minusDays(1), 21, 90),
            row(ContentType.LIVE, "NPO 1", today.minusDays(9), 20, 60), // last week
        )
        val s = WatchStats.of(rows, emptyMap(), today, zone)
        assertEquals((120 + 10 + 45 + 8 + 90) * 60, s.weekSeconds)
        assertEquals(60 * 60, s.lastWeekSeconds)
        assertEquals(listOf(WatchKind.SPORT, WatchKind.FILMS), s.byKind.map { it.first })
        assertEquals(2, s.matches)
        assertEquals(20, s.usualHour)
        assertEquals("ESPN" to 138 * 60, s.favourite)
        assertEquals("ESPN" to 120 * 60, s.longest)
    }

    @Test
    fun `the streak counts back from today, or from yesterday when today is still empty`() {
        val rows = listOf(1L, 2L, 3L, 5L).map { row(ContentType.LIVE, "NPO 1", today.minusDays(it), 20, 30) }
        assertEquals(3, WatchStats.of(rows, emptyMap(), today, zone).streak)
        val withToday = rows + row(ContentType.LIVE, "NPO 1", today, 9, 5)
        assertEquals(4, WatchStats.of(withToday, emptyMap(), today, zone).streak)
        assertEquals(0, WatchStats.of(listOf(row(ContentType.LIVE, "NPO 1", today.minusDays(3), 20, 30)), emptyMap(), today, zone).streak)
    }
}
