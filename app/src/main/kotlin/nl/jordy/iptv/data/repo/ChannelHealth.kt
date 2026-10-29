package nl.jordy.iptv.data.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.ChannelCheckEntity
import nl.jordy.iptv.data.db.ChannelSwapEntity
import nl.jordy.iptv.data.db.LiveChannelEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notices a live stream that stalls: buffering for [STALL_MS] or longer after it was playing counts
 * once per stretch. Time comes in from outside, like the player's other timers.
 */
class StallDetector {
    var stalls = 0
        private set
    private var started = false
    private var bufferingSince: Long? = null
    private var counted = false

    fun tick(nowMs: Long, playing: Boolean, buffering: Boolean) {
        if (playing) started = true
        if (started && buffering) {
            val since = bufferingSince ?: nowMs.also { bufferingSince = it }
            if (!counted && nowMs - since >= STALL_MS) {
                stalls++
                counted = true
            }
        } else {
            bufferingSince = null
            counted = false
        }
    }

    companion object {
        const val STALL_MS = 3_000L
    }
}

/**
 * Zenderstatus: how each live channel behaved, and what to do about one that keeps hiccuping. A
 * session is "troubled" when it stalled or failed. A channel with at least three sessions, half of
 * them troubled, gets a proposal: another version of it (same name, other quality or category)
 * that ran steadily, or one nobody tried yet.
 */
object ChannelAdvice {
    data class Stats(val sessions: Int, val troubled: Int)

    data class Proposal(
        val channel: LiveChannelEntity,
        val stats: Stats,
        val sibling: LiveChannelEntity?,
        /** Null when the sibling was never watched: worth a try, nothing more. */
        val siblingStats: Stats?,
    )

    fun stats(checks: List<ChannelCheckEntity>): Map<Int, Stats> = checks.groupBy { it.streamId }
        .mapValues { (_, c) -> Stats(c.size, c.count { it.failed || it.stalls > 0 }) }

    fun troubled(s: Stats) = s.sessions >= 3 && s.troubled * 2 >= s.sessions
    fun steady(s: Stats) = s.sessions >= 2 && s.troubled == 0

    /**
     * [siblings] gives the other versions of a channel. A steady sibling beats an untried one;
     * among steady ones the most watched wins.
     */
    fun proposals(
        stats: Map<Int, Stats>,
        channels: Map<Int, LiveChannelEntity>,
        siblings: (LiveChannelEntity) -> List<LiveChannelEntity>,
    ): List<Proposal> = stats.filter { troubled(it.value) }.mapNotNull { (id, s) ->
        val channel = channels[id] ?: return@mapNotNull null
        val others = siblings(channel).filter { it.streamId != id }
        val best = others.filter { o -> stats[o.streamId]?.let(::steady) == true }.maxByOrNull { stats.getValue(it.streamId).sessions }
            ?: others.firstOrNull { it.streamId !in stats }
        Proposal(channel, s, best, best?.let { stats[it.streamId] })
    }.sortedByDescending { it.stats.troubled }

    /** "NPO 1 · FHD": the name with the badges that tell versions apart. */
    fun label(c: LiveChannelEntity) = (listOf(c.name) + c.badges.split(',').filter { it.isNotBlank() }).joinToString(" · ")
}

@Singleton
class ChannelHealthRepository @Inject constructor(private val db: AppDatabase) {
    private val detached = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One live session: only worth keeping when it played a while, stalled or failed. */
    fun record(streamId: Int, playedMs: Long, stalls: Int, failed: Boolean) {
        if (playedMs < 10_000 && stalls == 0 && !failed) return
        detached.launch {
            val now = System.currentTimeMillis()
            db.channelHealth().insert(ChannelCheckEntity(streamId = streamId, at = now, playedSeconds = (playedMs / 1000).toInt(), stalls = stalls, failed = failed))
            db.channelHealth().prune(now - KEEP_DAYS * 86_400_000L)
        }
    }

    suspend fun proposals(): List<ChannelAdvice.Proposal> {
        val dao = db.channelHealth()
        val stats = ChannelAdvice.stats(dao.checks())
        val troubled = stats.filter { ChannelAdvice.troubled(it.value) }.keys
        if (troubled.isEmpty()) return emptyList()
        val channels = db.catalog().liveByIds(troubled.toList()).associateBy { it.streamId }
        val siblings = channels.values.associate { c -> c.streamId to dao.liveNamed(c.name) }
        val all = (channels.values + siblings.values.flatten()).associateBy { it.streamId }
        return ChannelAdvice.proposals(stats, all) { siblings[it.streamId].orEmpty() }
    }

    val swaps: Flow<List<ChannelSwapEntity>> = db.channelHealth().observeSwaps()
    suspend fun swapMap(): Map<Int, Int> = db.channelHealth().swaps().associate { it.fromStreamId to it.toStreamId }
    suspend fun swap(from: Int, to: Int) = db.channelHealth().upsertSwap(ChannelSwapEntity(from, to, System.currentTimeMillis()))
    suspend fun undo(from: Int) = db.channelHealth().deleteSwap(from)
    suspend fun channels(ids: List<Int>) = db.catalog().liveByIds(ids)

    companion object {
        const val KEEP_DAYS = 30
    }
}
