package nl.jordy.iptv.data.repo

import android.util.LruCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.xtream.EpgEntry
import nl.jordy.iptv.data.xtream.XtreamClient
import nl.jordy.iptv.data.xtream.XtreamParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What's on a channel. The full guide in Room ([nl.jordy.iptv.data.epg.EpgGuide]) comes first;
 * channels it doesn't cover fall back to the provider's short EPG, fetched only for channels on
 * screen and cached for a few minutes.
 */
@Singleton
class EpgRepository @Inject constructor(
    private val client: XtreamClient,
    private val credentials: CredentialStore,
    private val db: AppDatabase,
) {
    private class Cached(val entries: List<EpgEntry>, val fetchedAt: Long)

    private val cache = LruCache<Int, Cached>(500)
    // Scrolling a channel list must not fire 20 parallel requests at a fragile panel.
    private val limiter = Semaphore(4)

    suspend fun nowAndNext(streamId: Int, nowSec: Long = System.currentTimeMillis() / 1000): Pair<EpgEntry?, EpgEntry?> {
        val guide = db.epg().upcoming(streamId, nowSec).map { EpgEntry(it.title, it.description, it.start, it.stop) }
        val entries = guide.ifEmpty { entries(streamId) }
        val nowIndex = entries.indexOfFirst { nowSec in it.start until it.stop }
        if (nowIndex < 0) return null to entries.firstOrNull { it.start > nowSec }
        return entries[nowIndex] to entries.getOrNull(nowIndex + 1)
    }

    fun clearCache() = cache.evictAll()

    /**
     * A channel's programmes from now on, for its guide panel: the stored guide, else the provider's
     * short EPG (up to [limit] entries) for channels the guide doesn't cover.
     */
    suspend fun schedule(streamId: Int, nowSec: Long = System.currentTimeMillis() / 1000, limit: Int = 40): List<EpgEntry> {
        val guide = db.epg().upcoming(streamId, nowSec, limit = 200).map { EpgEntry(it.title, it.description, it.start, it.stop) }
        if (guide.isNotEmpty()) return guide
        val c = credentials.current() ?: return emptyList()
        return limiter.withPermit {
            runCatching { XtreamParser.shortEpg(client.get(c, "get_short_epg", "stream_id" to "$streamId", "limit" to "$limit")) }
                .getOrDefault(emptyList())
        }.filter { it.stop > nowSec }
    }

    /**
     * Programmes per stream overlapping [from, to), for the guide grid: the stored guide, and the
     * provider's short EPG (from now on) for the streams it doesn't cover. Streams with neither are absent.
     */
    suspend fun window(streamIds: List<Int>, from: Long, to: Long): Map<Int, List<EpgEntry>> {
        val stored = db.epg().between(streamIds, from, to).groupBy({ it.streamId }) { EpgEntry(it.title, it.description, it.start, it.stop) }
        val missing = streamIds.filter { it !in stored }
        if (missing.isEmpty()) return stored
        val fetched = coroutineScope {
            missing.map { id -> async { id to schedule(id).filter { it.stop > from && it.start < to } } }.awaitAll()
        }
        return stored + fetched.filter { it.second.isNotEmpty() }
    }

    private suspend fun entries(streamId: Int): List<EpgEntry> {
        cache.get(streamId)?.takeIf { System.currentTimeMillis() - it.fetchedAt < TTL_MS }?.let { return it.entries }
        val c = credentials.current() ?: return emptyList()
        val entries = limiter.withPermit {
            runCatching { XtreamParser.shortEpg(client.get(c, "get_short_epg", "stream_id" to "$streamId", "limit" to "4")) }
                .getOrDefault(emptyList())
        }
        cache.put(streamId, Cached(entries, System.currentTimeMillis()))
        return entries
    }

    private companion object {
        const val TTL_MS = 5 * 60 * 1000L
    }
}
