package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.WatchLogEntity
import nl.jordy.iptv.data.xtream.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** History and the weekoverzicht must count real watching, and add up the way people would. */
class WatchLogTest {

    private val zone = ZoneId.of("Europe/Amsterdam")
    private val tuesday = LocalDate.of(2026, 10, 27)
    private fun at(day: LocalDate, h: Int, m: Int) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun row(type: ContentType, id: Int, title: String, start: Long, minutes: Int) =
        WatchLogEntity(profileId = 1, type = type, itemId = id, title = title, detail = null, containerExtension = "", categoryId = null, startedAt = start, seconds = minutes * 60)

    @Test
    fun `the counter only counts playing time and starts again after take`() {
        val c = WatchCounter()
        for (t in 0..60_000L step 500) c.tick(t, playing = true)
        for (t in 60_500..120_000L step 500) c.tick(t, playing = false)
        assertEquals(60_000L, c.take())
        c.tick(200_000, playing = true)
        c.tick(201_000, playing = true)
        assertEquals("nothing carried over from before take", 1_000L, c.take())
    }

    @Test
    fun `totals per kind, top channels and distinct films and episodes`() {
        val rows = listOf(
            row(ContentType.LIVE, 1, "NPO 1", at(tuesday, 20, 0), 30),
            row(ContentType.LIVE, 2, "ESPN", at(tuesday, 20, 30), 90),
            row(ContentType.LIVE, 1, "NPO 1", at(tuesday, 22, 0), 20),
            row(ContentType.MOVIE, 10, "Film", at(tuesday.plusDays(1), 20, 0), 60),
            row(ContentType.MOVIE, 10, "Film", at(tuesday.plusDays(1), 21, 30), 40), // resumed: one film
            row(ContentType.SERIES, 100, "Serie – A1", at(tuesday.plusDays(2), 20, 0), 40),
        )
        val s = WeekSummary.of(rows, zone)
        assertEquals(280 * 60, s.totalSeconds)
        assertEquals(140 * 60, s.liveSeconds)
        assertEquals(listOf("ESPN" to 90 * 60, "NPO 1" to 50 * 60), s.topChannels)
        assertEquals(1, s.films)
        assertEquals(1, s.episodes)
        assertEquals(tuesday to 140 * 60, s.busiestDay)
    }

    @Test
    fun `a binge is one series with short gaps, a long break or another series ends it`() {
        val rows = listOf(
            row(ContentType.SERIES, 1, "Serie A – A1", at(tuesday, 20, 0), 40),
            row(ContentType.SERIES, 2, "Serie A – A2", at(tuesday, 20, 45), 40), // 5 min gap
            row(ContentType.SERIES, 3, "Serie A – A3", at(tuesday, 22, 30), 40), // 65 min gap: new run
            row(ContentType.SERIES, 4, "Serie B – A1", at(tuesday, 23, 15), 50),
        )
        assertEquals("Serie A" to 80 * 60, WeekSummary.of(rows, zone).longestBinge)
        assertNull(WeekSummary.of(emptyList(), zone).longestBinge)
    }

    @Test
    fun `durations read like people say them`() {
        assertEquals("3 u 10 m", WeekSummary.duration(190 * 60))
        assertEquals("45 m", WeekSummary.duration(45 * 60 + 30))
        assertEquals("dinsdag", WeekSummary.dayName(tuesday))
    }
}
