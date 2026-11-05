package nl.jordy.iptv.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.repo.StallDetector
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where live HLS starts, behind the provider's newest segment. The provider sends no clock, so how far
 * behind the match itself is can't be known; this is the part BUIS steers. Its segments are 10 s, so
 * Kort leaves one segment of margin and stalls sooner when the server is busy.
 */
enum class LiveDelay(val label: String, val offsetMs: Long) {
    KORT("Kort", 10_000),
    NORMAAL("Normaal", 20_000),
    VEILIG("Veilig", 30_000);

    /**
     * What the player is asked for. Without a clock ExoPlayer starts at the beginning of the segment the
     * target falls in, so a target of exactly 10 s with a 9.6 s last segment starts 19.6 s back. Just
     * inside lands on the intended segment. It stays "about": a segment that arrives while the channel
     * opens adds 10 s (2026-11-05, Kort over 7 zaps: 9–20 s, mostly 10 or 20).
     */
    val targetMs get() = offsetMs - 1_000

    /** In Instellingen: each OK the next one, round. */
    fun next() = entries[(ordinal + 1) % entries.size]

    /** After stalling: one step more margin, Veilig stays Veilig. */
    fun safer() = entries[minOf(ordinal + 1, entries.lastIndex)]

    companion object {
        val DEFAULT = VEILIG
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The Live-vertraging the player uses while it's open: the setting to begin with, and after [STEP_STALLS]
 * stalls at one of them, one step safer (Kort → Normaal → Veilig). The setting itself stays as chosen.
 * Time comes in from outside, like the player's other timers.
 */
class LiveDelayStepper(start: LiveDelay) {
    var delay = start
        private set
    private var stall = StallDetector()

    /** Stalls counted at [delay] so far; they go into the week line with it. */
    val stalls get() = stall.stalls

    /** The delay it stepped away from, or null when it stays. */
    fun tick(nowMs: Long, playing: Boolean, buffering: Boolean): LiveDelay? {
        stall.tick(nowMs, playing, buffering)
        val safer = delay.safer()
        if (stall.stalls < STEP_STALLS || safer == delay) return null
        return delay.also { delay = safer; stall = StallDetector() }
    }

    /** What played so far was written down: count stalls from zero. */
    fun restartCount() {
        stall = StallDetector()
    }

    companion object {
        /** One stall can be bad luck; two at the same delay say it's too tight tonight. */
        const val STEP_STALLS = 2
    }
}

/**
 * Live time and stalls per [LiveDelay] over the last [DAYS] days, so the setting can say how each one
 * went in this house. Kept as lines "epochDay|MODE|playedMs|stalls", one per day and mode.
 */
object LiveDelayStats {
    const val DAYS = 7

    data class Total(val playedMs: Long, val stalls: Int)

    private data class Line(val day: Long, val mode: LiveDelay, val playedMs: Long, val stalls: Int) {
        override fun toString() = "$day|${mode.name}|$playedMs|$stalls"
    }

    private fun parse(raw: String): List<Line> = raw.lines().mapNotNull { l ->
        val p = l.split('|')
        if (p.size != 4) return@mapNotNull null
        val mode = LiveDelay.entries.firstOrNull { it.name == p[1] } ?: return@mapNotNull null
        Line(p[0].toLongOrNull() ?: return@mapNotNull null, mode, p[2].toLongOrNull() ?: 0, p[3].toIntOrNull() ?: 0)
    }

    /** [raw] with one session added to [today]'s line for [mode]; days older than [DAYS] drop off. */
    fun add(raw: String, today: Long, mode: LiveDelay, playedMs: Long, stalls: Int): String {
        val lines = parse(raw).filter { today - it.day < DAYS }.toMutableList()
        val i = lines.indexOfFirst { it.day == today && it.mode == mode }
        if (i >= 0) lines[i] = lines[i].let { it.copy(playedMs = it.playedMs + playedMs, stalls = it.stalls + stalls) }
        else lines += Line(today, mode, playedMs, stalls)
        return lines.joinToString("\n")
    }

    fun totals(raw: String, today: Long): Map<LiveDelay, Total> =
        parse(raw).filter { today - it.day < DAYS }.groupBy { it.mode }
            .mapValues { (_, l) -> Total(l.sumOf { it.playedMs }, l.sumOf { it.stalls }) }

    /** "Veilig 3 u 10 min, 0× gehaperd · Kort 40 min, 2× gehaperd", null when nothing was watched yet. */
    fun summary(totals: Map<LiveDelay, Total>): String? = LiveDelay.entries
        .mapNotNull { m -> totals[m]?.takeIf { it.playedMs >= 60_000 }?.let { "${m.label} ${duration(it.playedMs)}, ${it.stalls}× gehaperd" } }
        .takeIf { it.isNotEmpty() }?.joinToString(" · ")

    private fun duration(ms: Long): String {
        val min = ms / 60_000
        return when {
            min < 60 -> "$min min"
            min % 60 == 0L -> "${min / 60} u"
            else -> "${min / 60} u ${min % 60} min"
        }
    }
}

private val Context.live by preferencesDataStore("live")

/** For the whole tv, not per profile: how the provider's stream holds up is the same for everyone. */
@Singleton
class LiveDelayStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val detached = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val delay: Flow<LiveDelay> = context.live.data.map { LiveDelay.of(it[DELAY]) }
    val summary: Flow<String?> = context.live.data.map {
        LiveDelayStats.summary(LiveDelayStats.totals(it[STATS].orEmpty(), LocalDate.now().toEpochDay()))
    }

    suspend fun set(delay: LiveDelay) {
        context.live.edit { it[DELAY] = delay.name }
    }

    /** Survives the player closing, like the watch log: a session ends while its screen goes away. */
    fun record(delay: LiveDelay, playedMs: Long, stalls: Int) {
        if (playedMs < 10_000) return
        detached.launch {
            context.live.edit {
                it[STATS] = LiveDelayStats.add(it[STATS].orEmpty(), LocalDate.now().toEpochDay(), delay, playedMs, stalls)
            }
        }
    }

    private companion object {
        val DELAY = stringPreferencesKey("delay")
        val STATS = stringPreferencesKey("stats")
    }
}
