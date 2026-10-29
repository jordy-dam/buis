package nl.jordy.iptv.player

/**
 * Intro overslaan, learned per series: the provider has no chapter data, so BUIS watches how
 * people skip. Seeking forward in the first [WINDOW_MS] of an episode and then watching on for
 * [SETTLE_MS] without seeking again means "the intro (and recap) ends here". Seeking back right
 * after an overshoot refines the spot instead of cancelling it.
 */
class IntroLearner {
    private var landing: Long? = null
    private var settledMs = 0L
    private var lastTick: Long? = null

    /** A seek from [fromMs] to [toMs] in the episode. */
    fun seeked(fromMs: Long, toMs: Long) {
        landing = when {
            landing != null && toMs < WINDOW_MS + SETTLE_MS -> toMs
            fromMs < WINDOW_MS && toMs > fromMs -> toMs
            else -> null
        }
        settledMs = 0
        lastTick = null
    }

    /** Every player tick; returns the learned intro end once, when the viewer settled after the jump. */
    fun tick(nowMs: Long, playing: Boolean): Long? {
        val l = landing ?: return null
        if (playing) {
            lastTick?.let { settledMs += nowMs - it }
            lastTick = nowMs
        } else lastTick = null
        if (settledMs < SETTLE_MS) return null
        landing = null
        return l
    }

    companion object {
        const val WINDOW_MS = 10 * 60_000L
        const val SETTLE_MS = 30_000L

        /** Offer the jump while the episode is still before the learned end (with a little margin). */
        fun offer(introEndMs: Long?, positionMs: Long): Boolean =
            introEndMs != null && positionMs < introEndMs - 5_000 && positionMs < WINDOW_MS
    }
}
