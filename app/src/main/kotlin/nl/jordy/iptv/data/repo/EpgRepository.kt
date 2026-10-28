package nl.jordy.iptv.data.repo

import android.util.LruCache
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.xtream.EpgEntry
import nl.jordy.iptv.data.xtream.XtreamClient
import nl.jordy.iptv.data.xtream.XtreamParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short EPG per channel, fetched only for channels on screen and cached for a few minutes.
 * The full XMLTV guide is phase 2.
 */
@Singleton
class EpgRepository @Inject constructor(
    private val client: XtreamClient,
    private val credentials: CredentialStore,
) {
    private class Cached(val entries: List<EpgEntry>, val fetchedAt: Long)

    private val cache = LruCache<Int, Cached>(500)
    // Scrolling a channel list must not fire 20 parallel requests at a fragile panel.
    private val limiter = Semaphore(4)

    suspend fun nowAndNext(streamId: Int, nowSec: Long = System.currentTimeMillis() / 1000): Pair<EpgEntry?, EpgEntry?> {
        val entries = entries(streamId)
        val nowIndex = entries.indexOfFirst { nowSec in it.start until it.stop }
        if (nowIndex < 0) return null to entries.firstOrNull { it.start > nowSec }
        return entries[nowIndex] to entries.getOrNull(nowIndex + 1)
    }

    fun clearCache() = cache.evictAll()

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
