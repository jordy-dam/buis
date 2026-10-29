package nl.jordy.iptv.data.auth

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.xtream.XtreamCredentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a logout or a new subscription may throw away. The catalog and guide are the provider's and
 * come back with the next sync, so they go. The household's own things stay: profiles, follow terms,
 * sleep timers. Favourites, resume positions, one-off reminders, pins and film details point at the
 * provider's ids, so they stay as long as the server stays the same (a renewed or new account on the
 * same panel) and are cleared only when a different server logs in, where those ids mean other films.
 */
@Singleton
class SubscriptionData @Inject constructor(@ApplicationContext context: Context, private val db: AppDatabase) {

    // Not in the "settings" DataStore: that file is emptied once by the 2a→2b hand-over.
    private val prefs = context.getSharedPreferences("subscription", Context.MODE_PRIVATE)

    /** Uitloggen: remember which server this was, then forget its catalog. */
    suspend fun onLogout(current: XtreamCredentials?) {
        current?.let(::serverKey)?.let { prefs.edit().putString(SERVER, it).apply() }
        clear(CATALOG)
    }

    /** After a successful login: a different server than last time starts the provider's data afresh. */
    suspend fun onLogin(c: XtreamCredentials) {
        val server = serverKey(c) ?: return
        val previous = prefs.getString(SERVER, null)
        if (previous != null && previous != server) {
            clear(CATALOG + PROVIDER_IDS)
            withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("UPDATE profile SET pinnedCategories = ''") }
        }
        prefs.edit().putString(SERVER, server).apply()
    }

    /** Alles wissen: the whole household and the subscription, as on a fresh install. */
    suspend fun wipeAll() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        prefs.edit().clear().apply()
    }

    private suspend fun clear(tables: List<String>) = withContext(Dispatchers.IO) {
        db.withTransaction { tables.forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM `$it`") } }
    }

    companion object {
        private const val SERVER = "server"

        /** The provider's catalog and guide: refilled by the next sync and guide refresh. */
        val CATALOG = listOf(
            "category", "live_channel", "movie", "series", "episode", "sync_info", "search",
            "epg_programme", "epg_channel", "epg_info",
        )

        /** The household's data that refers to the provider's ids. */
        val PROVIDER_IDS = listOf("favorite", "playback_position", "reminder", "movie_info")

        /** Same panel = same host and port; the username may change with a new subscription. */
        fun serverKey(c: XtreamCredentials): String? = c.baseUrl?.let { "${it.host}:${it.port}" }
    }
}
