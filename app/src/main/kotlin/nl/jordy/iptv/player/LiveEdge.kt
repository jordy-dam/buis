package nl.jordy.iptv.player

/**
 * Notices a live stream that slid behind without anyone asking for it. Live never skips ahead on its
 * own, so every stall adds its length for good, and minutes of it go by unnoticed. The provider's HLS
 * has no timestamps, so ExoPlayer can't tell how far behind it is and doesn't catch up either.
 *
 * HLS: how far playback sits from the end of the playlist's window ([tick]'s `fromEdgeMs`), compared
 * with where it started. TS has no window: the time it spent stalled since it opened stands in for it.
 * Only ticked while nobody chose to be behind (no pause, no −10). Time comes in from outside.
 */
class LiveEdge {
    private var startFromEdge: Long? = null
    private var stalledMs = 0L
    private var bufferingSince: Long? = null
    private var started = false

    /** A new stream, or back at live: count from here. */
    fun reset() {
        startFromEdge = null
        stalledMs = 0
        bufferingSince = null
        started = false
    }

    /** True when the stream is [DRIFT_MS] further behind than it started: time to go back to live. */
    fun tick(nowMs: Long, playing: Boolean, buffering: Boolean, fromEdgeMs: Long?): Boolean {
        if (playing) started = true
        if (started && buffering) {
            if (bufferingSince == null) bufferingSince = nowMs
        } else {
            bufferingSince?.let { stalledMs += nowMs - it }
            bufferingSince = null
        }
        if (!playing) return false
        if (fromEdgeMs != null) {
            val start = startFromEdge ?: fromEdgeMs.also { startFromEdge = it }
            return fromEdgeMs - start > DRIFT_MS
        }
        return stalledMs > DRIFT_MS
    }

    companion object {
        /** More than a segment's wobble, less than anyone would accept falling behind. */
        const val DRIFT_MS = 20_000L
    }
}
