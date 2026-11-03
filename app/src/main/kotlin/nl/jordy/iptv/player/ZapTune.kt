package nl.jordy.iptv.player

/**
 * When a channel step opens its stream. One press on its own opens at once; a quick run of presses
 * only opens the channel it stops on. Opening a stream per step would hit the provider with a burst
 * of connections on a one-connection subscription, and panels answer that with 458/429 and lock the
 * account for a few minutes. The channel playing keeps playing while a step waits.
 */
object ZapTune {
    /** The wait after each step in a run: long enough to see the next press coming. */
    const val RUN_DELAY_MS = 500L

    /** A press this long after the previous one is on its own. */
    const val ISOLATED_MS = 1_500L

    /** A clock that reads earlier than the last press counts as on its own, so a step never sticks. */
    fun delayMs(lastPressMs: Long?, nowMs: Long): Long =
        if (lastPressMs == null || nowMs - lastPressMs !in 0 until ISOLATED_MS) 0L else RUN_DELAY_MS
}
