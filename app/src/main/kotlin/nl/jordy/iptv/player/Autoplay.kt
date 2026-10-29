package nl.jordy.iptv.player

import nl.jordy.iptv.data.db.EpisodeEntity

/**
 * When the next episode starts by itself. A card shows in the last 20 seconds and counts down from
 * 10; after three episodes in a row without a key press it asks first, so a household that fell
 * asleep doesn't play a whole season.
 */
object Autoplay {
    const val CARD_BEFORE_END_MS = 20_000L
    const val COUNTDOWN_SECS = 10
    const val UNATTENDED_LIMIT = 3

    /** The episode after [currentId], rolling over into the next season; null after the very last one. */
    fun next(episodes: List<EpisodeEntity>, currentId: Int): EpisodeEntity? {
        val sorted = episodes.sortedWith(compareBy({ it.season }, { it.number }))
        val i = sorted.indexOfFirst { it.episodeId == currentId }
        return if (i < 0) null else sorted.getOrNull(i + 1)
    }

    /**
     * The card is due in the last 20 seconds, or at the end when the stream gives no duration.
     * Very short videos (under 40 s) only get it at the end.
     */
    fun cardDue(positionMs: Long, durationMs: Long, ended: Boolean): Boolean =
        ended || (durationMs > 2 * CARD_BEFORE_END_MS && durationMs - positionMs <= CARD_BEFORE_END_MS)

    /** [finishedUnattended] counts this episode too: the third one in a row without a key asks first. */
    fun asksFirst(finishedUnattended: Int): Boolean = finishedUnattended >= UNATTENDED_LIMIT

    /** Season and episode as the card's plate shows them: "S1 · A3". */
    fun code(e: EpisodeEntity): String = "S${e.season} · A${e.number}"
}
