package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.WatchLogEntity
import nl.jordy.iptv.data.xtream.ContentType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What kind of watching a history row was, for the Kijkgeschiedenis bar. Live splits by channel and category name. */
enum class WatchKind(val label: String) {
    SPORT("Sport"), NEWS("Nieuws"), KIDS("Kinderen"), LIVE("Overig live"), FILMS("Films"), SERIES("Series");

    companion object {
        private val sport = Regex(
            "\\b(SPORTS?|ESPN|DAZN|EUROSPORT|VIAPLAY|F1|FORMULE ?1|FORMULA ?1|VOETBAL|FOOTBALL|SOCCER|NBA|NFL|NHL|UFC|GOLF|TENNIS|DARTS|CYCLING)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val news = Regex("\\b(NEWS|NIEUWS|NOS|CNN|RTL ?Z|JOURNAAL|BNR|EURONEWS|ALJAZEERA|AL JAZEERA)\\b", RegexOption.IGNORE_CASE)
        private val kids = Regex("\\b(KIDS?|NICK(ELODEON)?|NICK JR|DISNEY|CARTOON|JUNIOR|BABY ?TV|ZAPP|ZAPPELIN|PEUTER|BOOMERANG|CARTOONITO|PIXAR|PBS KIDS)\\b", RegexOption.IGNORE_CASE)

        /** [categoryName] is the live category's name ("┃NL┃ SPORT"), when known. */
        fun of(r: WatchLogEntity, categoryName: String?): WatchKind = when (r.type) {
            ContentType.MOVIE -> FILMS
            ContentType.SERIES -> SERIES
            ContentType.LIVE -> {
                val text = "${r.title} ${categoryName.orEmpty()}"
                when {
                    sport.containsMatchIn(text) -> SPORT
                    news.containsMatchIn(text) -> NEWS
                    kids.containsMatchIn(text) -> KIDS
                    else -> LIVE
                }
            }
        }
    }
}

/**
 * Kijkgeschiedenis in numbers: the last seven days against the seven before, what kind of watching,
 * and a few facts worth a look. [perDay] feeds the calendar. All from the device's own watch log.
 */
data class WatchStats(
    val weekSeconds: Int,
    val lastWeekSeconds: Int,
    /** This week per kind, biggest first; kinds under a minute are left out. */
    val byKind: List<Pair<WatchKind, Int>>,
    val perDay: Map<LocalDate, Int>,
    /** Days in a row with watching, up to today (or yesterday, when today has none yet). */
    val streak: Int,
    /** The hour most watching starts in this week, weighted by time. */
    val usualHour: Int?,
    /** Sport sessions this week whose programme is a match ("FC Utrecht - N.E.C."), counted once each. */
    val matches: Int,
    /** The longest single sitting this week: what and how long. */
    val longest: Pair<String, Int>?,
    /** What got the most time this week: a channel, a film, or a series by name. */
    val favourite: Pair<String, Int>?,
) {
    companion object {
        private val match = Regex("\\S\\s+(-|–|vs\\.?)\\s+\\S", RegexOption.IGNORE_CASE)

        fun of(rows: List<WatchLogEntity>, categories: Map<String, String>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): WatchStats {
            fun day(r: WatchLogEntity) = Instant.ofEpochMilli(r.startedAt).atZone(zone).toLocalDate()
            val weekStart = today.minusDays(6)
            val week = rows.filter { !day(it).isBefore(weekStart) }
            val lastWeek = rows.filter { day(it).isBefore(weekStart) && !day(it).isBefore(weekStart.minusDays(7)) }
            val kinds = week.associateWith { WatchKind.of(it, it.categoryId?.let(categories::get)) }

            val perDay = rows.groupBy(::day).mapValues { (_, r) -> r.sumOf { it.seconds } }
            var streak = 0
            var d = if ((perDay[today] ?: 0) > 0) today else today.minusDays(1)
            while ((perDay[d] ?: 0) > 0) { streak++; d = d.minusDays(1) }

            val usual = week.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).hour }
                .mapValues { (_, r) -> r.sumOf { it.seconds } }
                .maxByOrNull { it.value }?.key

            val matches = week.filter { kinds[it] == WatchKind.SPORT }
                .mapNotNull { it.detail?.trim() }
                .filter { match.containsMatchIn(it) }
                .distinct().size

            val favourite = week.groupBy { if (it.type == ContentType.SERIES) it.title.substringBefore(" – ") else it.title }
                .map { (name, r) -> name to r.sumOf { it.seconds } }
                .maxByOrNull { it.second }

            return WatchStats(
                weekSeconds = week.sumOf { it.seconds },
                lastWeekSeconds = lastWeek.sumOf { it.seconds },
                byKind = week.groupBy { kinds.getValue(it) }
                    .map { (k, r) -> k to r.sumOf { it.seconds } }
                    .filter { it.second >= 60 }
                    .sortedByDescending { it.second },
                perDay = perDay,
                streak = streak,
                usualHour = usual,
                matches = matches,
                longest = week.maxByOrNull { it.seconds }?.let { (if (it.type == ContentType.SERIES) it.title.substringBefore(" – ") else it.title) to it.seconds },
                favourite = favourite,
            )
        }
    }
}
