package nl.jordy.iptv.player

/**
 * The thumbs at the end of a film, for "Voor jou". They come with the credits (the same 95% at
 * which the resume position is cleared, see [ResumePolicy]) or when the film ends, once per
 * playback, and go by themselves after [SECONDS]: no answer is fine. Short videos (trailers,
 * under 20 minutes) aren't asked about.
 */
object FilmThumbs {
    const val SECONDS = 10
    private const val MIN_DURATION_MS = 20 * 60_000L

    fun due(positionMs: Long, durationMs: Long, ended: Boolean): Boolean = when {
        durationMs in 1 until MIN_DURATION_MS -> false
        ended -> true
        durationMs <= 0 -> false
        else -> positionMs >= durationMs * ResumePolicy.FINISHED_FRACTION
    }
}
