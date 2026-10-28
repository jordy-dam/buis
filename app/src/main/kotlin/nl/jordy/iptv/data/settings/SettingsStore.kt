package nl.jordy.iptv.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class UserPrefs(
    /** Group codes from [nl.jordy.iptv.data.xtream.NameParser]: "NL", "BE", "EN"… */
    val preferredCountries: Set<String> = setOf("NL"),
    val onlyPreferred: Boolean = true,
)

private val Context.settings by preferencesDataStore("settings")

/** User choices that aren't secret (credentials live in [nl.jordy.iptv.data.auth.CredentialStore]). */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    val prefs: Flow<UserPrefs> = context.settings.data.map { p ->
        UserPrefs(
            preferredCountries = p[COUNTRIES] ?: UserPrefs().preferredCountries,
            onlyPreferred = p[ONLY_PREFERRED] ?: UserPrefs().onlyPreferred,
        )
    }

    suspend fun setPreferredCountries(codes: Set<String>) {
        context.settings.edit { it[COUNTRIES] = codes }
    }

    suspend fun setOnlyPreferred(only: Boolean) {
        context.settings.edit { it[ONLY_PREFERRED] = only }
    }

    suspend fun clear() {
        context.settings.edit { it.clear() }
    }

    private companion object {
        val COUNTRIES = stringSetPreferencesKey("preferred_countries")
        val ONLY_PREFERRED = booleanPreferencesKey("only_preferred")
    }
}
