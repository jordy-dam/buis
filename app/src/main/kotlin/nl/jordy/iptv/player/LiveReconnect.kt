package nl.jordy.iptv.player

/**
 * A live stream that dropped after it played: a few tries to get it back, then it gives up so the
 * player can say so instead of reconnecting forever. Only a stretch of steady playing earns the
 * tries back, so a stream that drops every minute still ends. Time comes in from outside.
 */
class LiveReconnect {
    var attempt = 0
        private set
    private var playingSince: Long? = null

    /** Playing or not, every tick. */
    fun tick(nowMs: Long, playing: Boolean) {
        if (!playing) {
            playingSince = null
            return
        }
        val since = playingSince ?: nowMs.also { playingSince = it }
        if (nowMs - since >= STEADY_MS) attempt = 0
    }

    /** The wait before the next try, or null when the tries are used up. */
    fun next(): Long? {
        if (attempt >= DELAYS_MS.size) return null
        return DELAYS_MS[attempt++]
    }

    companion object {
        val DELAYS_MS = listOf(1_000L, 3_000L, 6_000L)
        const val STEADY_MS = 60_000L
    }
}
