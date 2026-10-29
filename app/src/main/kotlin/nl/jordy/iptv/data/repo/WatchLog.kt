package nl.jordy.iptv.data.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.WatchLogEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.xtream.ContentType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** What's on screen in the player, for the watch log. */
data class WatchItem(
    val type: ContentType,
    val itemId: Int,
    val title: String,
    val detail: String? = null,
    val containerExtension: String = "",
    val categoryId: String? = null,
)

/**
 * Playing time of what's on screen now. Paused and buffering time doesn't count; time comes in
 * from outside, like the player's other timers.
 */
class WatchCounter {
    private var playedMs = 0L
    private var lastMs: Long? = null

    fun tick(nowMs: Long, playing: Boolean) {
        if (playing) {
            lastMs?.let { playedMs += nowMs - it }
            lastMs = nowMs
        } else lastMs = null
    }

    /** The time so far, counting again from zero: what's on screen changed or was saved. */
    fun take(): Long = playedMs.also { playedMs = 0; lastMs = null }
}

@Singleton
class WatchLog @Inject constructor(private val db: AppDatabase, private val profiles: ProfileRepository) {
    private val detached = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Survives the player closing: the last stretch is written while its screen goes away. */
    fun record(item: WatchItem, startedAt: Long, playedMs: Long) {
        if (playedMs < MIN_SECONDS * 1000L) return
        detached.launch {
            val profile = profiles.currentId()
            db.watchLog().insert(
                WatchLogEntity(
                    profileId = profile, type = item.type, itemId = item.itemId, title = item.title, detail = item.detail,
                    containerExtension = item.containerExtension, categoryId = item.categoryId,
                    startedAt = startedAt, seconds = (playedMs / 1000).toInt(),
                ),
            )
            db.watchLog().prune(System.currentTimeMillis() - KEEP_DAYS * 86_400_000L)
        }
    }

    /** The active profile's history of the last [days] days, newest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun recent(days: Int): Flow<List<WatchLogEntity>> = profiles.currentId.flatMapLatest { id ->
        db.watchLog().since(id, System.currentTimeMillis() - days * 86_400_000L)
    }

    companion object {
        const val MIN_SECONDS = 60
        const val KEEP_DAYS = 90
    }
}

/** The weekoverzicht: what a profile watched in a stretch of days. */
data class WeekSummary(
    val totalSeconds: Int,
    val liveSeconds: Int,
    val filmSeconds: Int,
    val seriesSeconds: Int,
    /** Channel name and seconds, most watched first, at most three. */
    val topChannels: List<Pair<String, Int>>,
    val films: Int,
    val episodes: Int,
    /** Longest run of one series with gaps under 15 minutes. */
    val longestBinge: Pair<String, Int>?,
    /** The day with the most watching, and its seconds. */
    val busiestDay: Pair<LocalDate, Int>?,
) {
    companion object {
        private const val BINGE_GAP_MS = 15 * 60_000L

        fun of(rows: List<WatchLogEntity>, zone: ZoneId = ZoneId.systemDefault()): WeekSummary {
            fun sum(type: ContentType) = rows.filter { it.type == type }.sumOf { it.seconds }
            val top = rows.filter { it.type == ContentType.LIVE }.groupBy { it.title }
                .map { (name, r) -> name to r.sumOf { it.seconds } }
                .sortedByDescending { it.second }.take(3)
            val busiest = rows.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
                .map { (day, r) -> day to r.sumOf { it.seconds } }
                .maxByOrNull { it.second }
            return WeekSummary(
                totalSeconds = rows.sumOf { it.seconds },
                liveSeconds = sum(ContentType.LIVE),
                filmSeconds = sum(ContentType.MOVIE),
                seriesSeconds = sum(ContentType.SERIES),
                topChannels = top,
                films = rows.filter { it.type == ContentType.MOVIE }.map { it.itemId }.distinct().size,
                episodes = rows.filter { it.type == ContentType.SERIES }.map { it.itemId }.distinct().size,
                longestBinge = longestBinge(rows),
                busiestDay = busiest,
            )
        }

        private fun longestBinge(rows: List<WatchLogEntity>): Pair<String, Int>? {
            var best: Pair<String, Int>? = null
            var series: String? = null
            var total = 0
            var endMs = 0L
            for (r in rows.filter { it.type == ContentType.SERIES }.sortedBy { it.startedAt }) {
                val name = r.title.substringBefore(" – ")
                if (name == series && r.startedAt - endMs <= BINGE_GAP_MS) total += r.seconds else {
                    series = name
                    total = r.seconds
                }
                endMs = r.startedAt + r.seconds * 1000L
                if (best == null || total > best.second) best = name to total
            }
            return best
        }

        /** "3 u 10 m", "45 m", "0 m". */
        fun duration(seconds: Int): String {
            val m = seconds / 60
            return if (m >= 60) "${m / 60} u ${m % 60} m" else "$m m"
        }

        fun dayName(day: LocalDate): String = when (day.dayOfWeek!!) {
            DayOfWeek.MONDAY -> "maandag"
            DayOfWeek.TUESDAY -> "dinsdag"
            DayOfWeek.WEDNESDAY -> "woensdag"
            DayOfWeek.THURSDAY -> "donderdag"
            DayOfWeek.FRIDAY -> "vrijdag"
            DayOfWeek.SATURDAY -> "zaterdag"
            DayOfWeek.SUNDAY -> "zondag"
        }
    }
}
