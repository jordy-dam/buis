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
    val logoUrl: String? = null,
    /** Back-to-back matching programmes on this channel folded into this one ("Zappelin" all morning). */
    val parts: Int = 1,
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

    /** Programmes this close together on one channel count as one stretch of watching. */
    private const val GAP_SEC = 60L

    /**
     * One entry per stretch of watching. First the same programme on several channels (HD, FHD, a
     * Ziggo copy) becomes the channel with an archive, else the first in the provider's order. Then
     * matches that follow each other on that channel (a kids block where every short programme is
     * its own guide entry) become one, from the first start to the last stop.
     */
    fun fold(rows: List<FollowMatchRow>): List<FollowMatch> {
        val programmes = rows.groupBy { it.title.trim().lowercase() to it.start }.values
            .map { group -> group.firstOrNull { it.archiveDays > 0 || it.catchup } ?: group.first() }
            .map { FollowMatch(it.term, it.title, it.start, it.stop, it.streamId, it.channelName, it.categoryId, it.archiveDays > 0 || it.catchup, it.logoUrl) }
            .sortedBy { it.start }
        val blocks = mutableListOf<FollowMatch>()
        val open = mutableMapOf<Pair<Int, String>, Int>() // (channel, term) → index in blocks
        for (p in programmes) {
            val at = open[p.streamId to p.term]
            val block = at?.let(blocks::get)
            if (block != null && p.start <= block.stop + GAP_SEC) {
                blocks[at] = block.copy(stop = maxOf(block.stop, p.stop), parts = block.parts + 1)
            } else {
                open[p.streamId to p.term] = blocks.size
                blocks += p
            }
        }
        return blocks
    }

    /** "Straks voor jou": per term only what comes first, so one busy term can't fill the row. */
    fun nextPerTerm(matches: List<FollowMatch>): List<FollowMatch> = matches.distinctBy { it.term.lowercase() }

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
