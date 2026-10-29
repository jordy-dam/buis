package nl.jordy.iptv.player

/**
 * Pauzemoment: after [minutes] of watching films or episodes (0 = off), a break of [lengthSecs].
 * Counts playing time only, so paused or buffering time doesn't count, and a single short episode
 * never reaches it; with autoplay the time of a whole run of episodes adds up. A pause of a few
 * minutes you took yourself counts as the break. Time comes in from outside, which keeps it testable.
 */
class BreakTimer(private val minutes: Int, lengthMinutes: Int = 1) {

    /** How long the break lasts, from the profile's "Pauzeduur". */
    val lengthSecs = lengthMinutes.coerceIn(1, 15) * 60

    enum class Placement {
        /** Not due, or off. */
        NONE,
        /** Pause now: a film, or a long way to the end. */
        NOW,
        /** Wait for the end of this episode: the next-episode card becomes the break. */
        AT_END,
        /** Nearly over and nothing follows: no break this time. */
        SKIP,
    }

    private var watchedMs = 0L
    private var lastPlayingMs: Long? = null
    private var pausedSinceMs: Long? = null

    /** Every player tick: [playing] is true while a film or episode actually plays. */
    fun tick(nowMs: Long, playing: Boolean) {
        if (playing) {
            pausedSinceMs?.let { if (nowMs - it >= SELF_BREAK_MS) watchedMs = 0 }
            pausedSinceMs = null
            lastPlayingMs?.let { watchedMs += nowMs - it }
            lastPlayingMs = nowMs
        } else {
            lastPlayingMs = null
            if (pausedSinceMs == null) pausedSinceMs = nowMs
        }
    }

    val due: Boolean get() = minutes > 0 && watchedMs >= minutes * 60_000L

    /**
     * Where the break goes once it's due. [remainingMs] is null when the stream gives no duration;
     * [hasNext] is true for an episode that autoplay will follow with the next one.
     */
    fun placement(remainingMs: Long?, hasNext: Boolean): Placement = when {
        !due -> Placement.NONE
        hasNext && (remainingMs == null || remainingMs <= NEAR_END_MS) -> Placement.AT_END
        remainingMs != null && remainingMs <= NEAR_END_MS -> Placement.SKIP
        else -> Placement.NOW
    }

    /** The break happened (or was skipped): start counting again. */
    fun taken() {
        watchedMs = 0
    }

    companion object {
        /** A pause this long that you took yourself is the break. */
        const val SELF_BREAK_MS = 3 * 60_000L
        /** This close to the end, a film isn't interrupted and an episode waits for its end. */
        const val NEAR_END_MS = 15 * 60_000L

        /** What Instellingen offers, in minutes; 0 is "Uit". */
        val CHOICES = listOf(0, 45, 60, 90)
        val DEBUG_CHOICES = listOf(0, 1, 45, 60, 90)
        /** "Pauzeduur" in minutes: short steps first, where the choice matters most. */
        val LENGTHS = listOf(1, 2, 3, 5, 10, 15)

        fun nextChoice(current: Int, choices: List<Int> = CHOICES): Int =
            choices[(choices.indexOf(current).coerceAtLeast(0) + 1) % choices.size]
    }
}
