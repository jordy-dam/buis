package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.FollowMatchRow

/** A programme a profile follows, on the channel to watch it on. */
data class FollowMatch(
    val term: String,
    val title: String,
    val start: Long,
    val stop: Long,
    val streamId: Int,
    val channelName: String,
    val categoryId: String,
    /** The channel keeps an archive, so "vanaf begin" works once it has started. */
    val hasArchive: Boolean,
) {
    /** One programme on one channel, for remembering which reminders were shown. */
    val key get() = "$streamId:$start"
}

/**
 * Which programmes a reminder is about, and when it shows. Five minutes before the start a banner
 * says it's coming; if that one is left alone, a second one comes at the start. A programme already
 * running when the app opens gets the second one, so a late start can still go "vanaf begin".
 */
object FollowMatches {
    const val SOON_SEC = 5 * 60L

    enum class Moment { SOON, STARTED }

    data class Due(val match: FollowMatch, val moment: Moment)

    /**
     * One entry per programme: the same title at the same time on several channels (HD, FHD, a
     * Ziggo copy) becomes the channel with an archive, else the first in the provider's order.
     */
    fun fold(rows: List<FollowMatchRow>): List<FollowMatch> =
        rows.groupBy { it.title.trim().lowercase() to it.start }.values
            .map { group -> group.firstOrNull { it.archiveDays > 0 || it.catchup } ?: group.first() }
            .map { FollowMatch(it.term, it.title, it.start, it.stop, it.streamId, it.channelName, it.categoryId, it.archiveDays > 0 || it.catchup) }
            .sortedBy { it.start }

    /** The banner to show now, if any; [shown] holds "<key>:soon" and "<key>:now" for what was dealt with. */
    fun due(matches: List<FollowMatch>, nowSec: Long, shown: Set<String>): Due? {
        for (m in matches) {
            val untilStart = m.start - nowSec
            val sinceStart = nowSec - m.start
            if (untilStart in 1..SOON_SEC && "${m.key}:soon" !in shown && "${m.key}:now" !in shown) return Due(m, Moment.SOON)
            if (sinceStart in 0 until SOON_SEC && nowSec < m.stop && "${m.key}:now" !in shown) return Due(m, Moment.STARTED)
        }
        return null
    }
}
