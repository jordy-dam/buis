package nl.jordy.iptv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.subs.OpenSubtitlesLogin
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.components.TvTextField
import nl.jordy.iptv.ui.components.ignoreHeldKey
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary

/**
 * The household's OpenSubtitles API key and account. Saving signs in first, so a typo shows here
 * and not halfway through a film. [onSave] returns what went wrong, or null.
 */
@Composable
fun OpenSubtitlesDialog(
    current: OpenSubtitlesLogin?,
    onSave: suspend (OpenSubtitlesLogin) -> String?,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var apiKey by remember { mutableStateOf(current?.apiKey.orEmpty()) }
    var username by remember { mutableStateOf(current?.username.orEmpty()) }
    var password by remember { mutableStateOf(current?.password.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    fun complete() = apiKey.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
    fun save() {
        if (busy || !complete()) return
        busy = true
        error = null
        scope.launch {
            error = onSave(OpenSubtitlesLogin(apiKey.trim(), username.trim(), password))
            busy = false
            if (error == null) onDismiss()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.ignoreHeldKey().background(SurfaceColor, RoundedCornerShape(16.dp)).padding(32.dp).width(560.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("OpenSubtitles", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Met een account kies je in de speler ook ondertitels van OpenSubtitles, bij films en series. Voor iedereen in huis. " +
                    "De API-sleutel maak je één keer aan op opensubtitles.com, onder je profiel bij API consumers.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            TvTextField("API-sleutel", apiKey, { apiKey = it }, Modifier.focusRequester(first))
            TvTextField("Gebruikersnaam", username, { username = it })
            TvTextField("Wachtwoord", password, { password = it }, password = true, imeAction = ImeAction.Done, onImeAction = ::save)
            Text(error ?: if (busy) "Inloggen…" else "", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryButton("Opslaan", ::save, enabled = !busy && complete())
                if (current != null) SecondaryButton("Verwijderen", onRemove)
                SecondaryButton("Annuleren", onDismiss)
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}
