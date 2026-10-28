package nl.jordy.iptv.player

/**
 * When a VOD position is worth remembering and offering to resume.
 *
 * - Under 30s in: you barely started, start over (and don't clutter "resume").
 * - At 95% or more: you watched it (credits), so it's finished and the position is cleared.
 * - Unknown duration (some providers' files report none): keep any position past 30s.
 */
object ResumePolicy {
    const val MIN_POSITION_MS = 30_000L
    const val FINISHED_FRACTION = 0.95

    sealed interface Decision {
        data class Save(val positionMs: Long) : Decision
        data object Clear : Decision
    }

    fun onProgress(positionMs: Long, durationMs: Long): Decision = when {
        durationMs > 0 && positionMs >= durationMs * FINISHED_FRACTION -> Decision.Clear
        positionMs < MIN_POSITION_MS -> Decision.Clear
        else -> Decision.Save(positionMs)
    }

    /** Position to start playback from, or 0 to start at the beginning. */
    fun startPosition(savedPositionMs: Long?, durationMs: Long): Long {
        if (savedPositionMs == null) return 0
        return when (onProgress(savedPositionMs, durationMs)) {
            is Decision.Save -> savedPositionMs
            Decision.Clear -> 0
        }
    }
}
