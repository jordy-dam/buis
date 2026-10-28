package nl.jordy.iptv.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.xtream.LoginResult
import nl.jordy.iptv.data.xtream.XtreamClient
import nl.jordy.iptv.data.xtream.XtreamCredentials
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.TvTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import nl.jordy.iptv.R
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.TextSecondary
import javax.inject.Inject

data class LoginState(val busy: Boolean = false, val error: String? = null, val done: Boolean = false)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val client: XtreamClient,
    private val store: CredentialStore,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    fun login(server: String, username: String, password: String) {
        if (_state.value.busy) return
        if (server.isBlank() || username.isBlank() || password.isBlank()) {
            _state.value = LoginState(error = "Vul server, gebruikersnaam en wachtwoord in.")
            return
        }
        _state.value = LoginState(busy = true)
        viewModelScope.launch {
            val creds = XtreamCredentials(server.trim(), username.trim(), password)
            val error = when (val r = client.login(creds)) {
                is LoginResult.Success -> null
                LoginResult.InvalidUrl -> "Dit is geen geldig serveradres. Voorbeeld: http://server.nl:8080"
                LoginResult.WrongCredentials -> "Gebruikersnaam of wachtwoord klopt niet."
                is LoginResult.Inactive -> if (r.status == "Expired") "Dit abonnement is verlopen." else "Dit account is niet actief (${r.status})."
                is LoginResult.Unreachable -> "Server niet bereikbaar. Controleer het adres en je internetverbinding."
                LoginResult.NotXtream -> "De server antwoordt, maar niet als een Xtream-server. Controleer het adres en de poort."
            }
            if (error == null) {
                store.save(creds)
                _state.value = LoginState(done = true)
            } else {
                _state.value = LoginState(error = error)
            }
        }
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, vm: LoginViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }

    LaunchedEffect(state.done) { if (state.done) onLoggedIn() }
    LaunchedEffect(Unit) { first.requestFocus() }

    Box(Modifier.fillMaxSize().padding(SafeArea), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_buis), contentDescription = "BUIS", modifier = Modifier.size(88.dp))
                Text("  Beste Unieke IPTV Stream", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
            Text("Log in met je IPTV-abonnement (Xtream Codes).", style = MaterialTheme.typography.titleLarge)
            TvTextField("Serveradres", server, { server = it }, Modifier.focusRequester(first), keyboardType = KeyboardType.Uri)
            TvTextField("Gebruikersnaam", username, { username = it })
            TvTextField(
                "Wachtwoord", password, { password = it },
                password = true, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                onImeAction = { vm.login(server, username, password) },
            )
            PrimaryButton(if (state.busy) "Bezig met controleren…" else "Inloggen", { vm.login(server, username, password) }, enabled = !state.busy)
            state.error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
        }
    }
}
