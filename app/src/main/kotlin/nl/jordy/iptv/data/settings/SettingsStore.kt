package nl.jordy.iptv.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** A profile's view settings, as the screens and [CountryFilter] use them. */
data class UserPrefs(
    /** Group codes from [nl.jordy.iptv.data.xtream.NameParser]: "NL", "BE", "EN"… */
    val preferredCountries: Set<String> = setOf("NL"),
    val onlyPreferred: Boolean = true,
    /** Kids profile: adult categories are hidden everywhere. */
    val hideAdult: Boolean = false,
)

private val Context.settings by preferencesDataStore("settings")

/**
 * The 2a settings, which were global. Since 2b they live on the profile; this only hands the old
 * values over once, to the first profile, and then empties the file.
 */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    /** The old settings, or null when there are none (fresh install, or already handed over). */
    suspend fun takeLegacy(): UserPrefs? {
        val p = context.settings.data.first()
        if (p.asMap().isEmpty()) return null
        context.settings.edit { it.clear() }
        return UserPrefs(
            preferredCountries = p[COUNTRIES] ?: UserPrefs().preferredCountries,
            onlyPreferred = p[ONLY_PREFERRED] ?: UserPrefs().onlyPreferred,
        )
    }

    private companion object {
        val COUNTRIES = stringSetPreferencesKey("preferred_countries")
        val ONLY_PREFERRED = booleanPreferencesKey("only_preferred")
    }
}
