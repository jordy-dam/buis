package nl.jordy.iptv.player

/**
 * Stops playback after [minutes] without a key press (0 = off). One minute before, it warns, so
 * someone still watching can press any key. Time comes in from outside, which keeps it testable.
 */
class SleepTimer(private val minutes: Int, startedAtMs: Long) {

    sealed interface State {
        data object Running : State
        data class Warning(val secondsLeft: Int) : State
        data object Expired : State
    }

    private var lastActivityMs = startedAtMs

    /** A key press: start counting again. */
    fun activity(nowMs: Long) {
        lastActivityMs = nowMs
    }

    fun state(nowMs: Long): State {
        if (minutes <= 0) return State.Running
        val left = minutes * 60_000L - (nowMs - lastActivityMs)
        return when {
            left <= 0 -> State.Expired
            left <= WARNING_MS -> State.Warning(((left + 999) / 1000).toInt())
            else -> State.Running
        }
    }

    companion object {
        const val WARNING_MS = 60_000L
        /** What Instellingen offers, in minutes; 0 is "Uit". */
        val CHOICES = listOf(0, 30, 60, 90, 120)

        fun nextChoice(current: Int): Int = CHOICES[(CHOICES.indexOf(current).coerceAtLeast(0) + 1) % CHOICES.size]
    }
}
