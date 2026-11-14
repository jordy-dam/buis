package nl.jordy.iptv.data.settings

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.jordy.iptv.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

private val Context.idle by preferencesDataStore("idle")

/**
 * After how many minutes without a key BUIS shows the testbeeld, for the whole household (0 = never).
 * Debug builds add 1 minute, to see it without waiting.
 */
@Singleton
class IdleTestbeeldStore @Inject constructor(@ApplicationContext private val context: Context) {
    val minutes: Flow<Int> = context.idle.data.map { it[MINUTES] ?: DEFAULT }

    suspend fun set(minutes: Int) {
        context.idle.edit { it[MINUTES] = minutes }
    }

    companion object {
        const val DEFAULT = 10
        val CHOICES = if (BuildConfig.DEBUG) listOf(0, 1, 5, 10, 15, 30) else listOf(0, 5, 10, 15, 30)
        fun next(minutes: Int) = CHOICES[(CHOICES.indexOf(minutes) + 1) % CHOICES.size]
        fun label(minutes: Int) = if (minutes == 0) "Uit" else "Na $minutes min"
        private val MINUTES = intPreferencesKey("minutes")
    }
}

/**
 * When the remote was last touched in the app's main window. Keys typed inside an app dialog
 * don't pass here; nobody stays in one for minutes, so that's left as it is.
 */
object IdleClock {
    @Volatile var lastInput: Long = SystemClock.elapsedRealtime()
        private set

    fun touch() {
        lastInput = SystemClock.elapsedRealtime()
    }

    fun idleMs() = SystemClock.elapsedRealtime() - lastInput
}
