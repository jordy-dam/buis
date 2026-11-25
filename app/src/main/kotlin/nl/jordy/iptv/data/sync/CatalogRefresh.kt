package nl.jordy.iptv.data.sync

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.epg.EpgGuide
import nl.jordy.iptv.data.xtream.ContentType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the catalog a day fresh without anyone waiting for it, the way [EpgGuide] does the guide:
 * the screensaver and the idle testbeeld are the moments meant for it, and coming back to the app
 * is the safety net. The app opens on yesterday's catalog meanwhile; the sync screen is only for
 * the very first catalog after logging in (and for "Catalogus vernieuwen" in Instellingen).
 */
@Singleton
class CatalogRefresh @Inject constructor(
    private val sync: CatalogSync,
    private val credentials: CredentialStore,
    private val db: AppDatabase,
    private val guide: EpgGuide,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastAttempt = 0L

    /** Cheap when the catalog is fresh; a failed attempt waits an hour before the next. */
    fun refreshIfStale() {
        scope.launch {
            val now = System.currentTimeMillis()
            if (sync.isRunning || now - lastAttempt < RETRY_AFTER_MS) return@launch
            val info = db.syncInfo().get() ?: return@launch // no catalog yet: the sync screen does the first one
            if (now - info.lastSyncAt < RESYNC_AFTER_MS) return@launch
            val c = credentials.current() ?: return@launch
            lastAttempt = now
            val started = System.currentTimeMillis()
            runCatching { sync.run(c) }
                .onSuccess {
                    Log.i(TAG, "catalog sync ${System.currentTimeMillis() - started} ms, changed ${it.changed}, removed ${it.removed}")
                    // New or changed channels may carry new EPG ids: rebuild the guide with them.
                    if (it.touched(ContentType.LIVE)) guide.refresh()
                }
                .onFailure { Log.w(TAG, "catalog sync failed: ${it.javaClass.simpleName}") }
        }
    }

    companion object {
        /** A catalog older than this is refreshed at the next quiet moment. */
        const val RESYNC_AFTER_MS = 24 * 60 * 60 * 1000L
        private const val RETRY_AFTER_MS = 60 * 60 * 1000L
        private const val TAG = "BUIS-catalog"
    }
}
