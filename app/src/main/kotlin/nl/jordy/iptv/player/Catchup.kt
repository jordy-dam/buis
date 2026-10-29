package nl.jordy.iptv.player

import nl.jordy.iptv.data.db.LiveChannelEntity
import nl.jordy.iptv.data.xtream.EpgEntry

/** When "vanaf begin" is offered, and how long a stretch to ask the panel for. */
object Catchup {

    /** Only on a channel with an archive, for a programme that started within the days the panel keeps. */
    fun canRestart(channel: LiveChannelEntity?, now: EpgEntry?, nowSec: Long): Boolean {
        if (channel == null || now == null || !channel.hasArchive) return false
        val keptFrom = nowSec - channel.archiveDays.coerceAtLeast(1) * 86_400L
        return now.start in keptFrom..nowSec
    }

    /** The whole programme, rounded up to whole minutes (the panel counts in minutes). */
    fun minutes(programme: EpgEntry): Int = (((programme.stop - programme.start).coerceAtLeast(60) + 59) / 60).toInt()
}
