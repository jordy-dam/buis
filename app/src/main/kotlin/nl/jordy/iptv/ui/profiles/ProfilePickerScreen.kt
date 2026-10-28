package nl.jordy.iptv.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.ProfileEntity
import nl.jordy.iptv.data.profile.Pin
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.ui.components.AvatarTile
import nl.jordy.iptv.ui.components.SecondaryButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import nl.jordy.iptv.R
import nl.jordy.iptv.ui.theme.SafeArea
import javax.inject.Inject

data class PickerState(val profiles: List<ProfileEntity>, val lastUsedId: Long?)

@HiltViewModel
class ProfilePickerViewModel @Inject constructor(private val profiles: ProfileRepository) : ViewModel() {
    val state = combine(profiles.profiles, profiles.current) { all, current -> PickerState(all, current?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pick(id: Long, then: () -> Unit) = viewModelScope.launch {
        profiles.select(id)
        then()
    }
}

/**
 * "Wie kijkt er?": one OK press for whoever watched last, since focus starts on them.
 * "Profielen beheren" turns the avatars into edit buttons. PIN profiles ask their PIN; once there
 * is a kids profile, managing asks for a grown-up's PIN (see [ProfileRepository.managementLock]).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProfilePickerScreen(
    atLaunch: Boolean,
    onPicked: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: ProfilePickerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    var managing by rememberSaveable { mutableStateOf(false) }
    // Unlocked once per visit, so editing three profiles doesn't ask the PIN three times.
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<PinRequest?>(null) }
    val activity = LocalActivity.current

    val lock = ProfileRepository.managementLock(s.profiles)
    fun whenUnlocked(action: () -> Unit) {
        if (unlocked || lock.isEmpty()) action()
        else pinFor = PinRequest("Pincode van een volwassene", "Nodig om profielen te beheren.", lock) { unlocked = true; action() }
    }

    BackHandler {
        when {
            managing -> managing = false
            atLaunch -> activity?.finish()
            else -> onPicked()
        }
    }

    val focus = remember(s.profiles.size) { List(s.profiles.size) { FocusRequester() } }
    LaunchedEffect(s.profiles.size, managing) {
        val i = s.profiles.indexOfFirst { it.id == s.lastUsedId }.coerceAtLeast(0)
        runCatching { focus.getOrNull(i)?.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().padding(SafeArea),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
    ) {
        Image(painterResource(R.drawable.ic_buis), contentDescription = "BUIS", modifier = Modifier.size(96.dp))
        Text(if (managing) "Profielen beheren" else "Wie kijkt er?", style = MaterialTheme.typography.displaySmall)
        Row(Modifier.focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            s.profiles.forEachIndexed { i, p ->
                AvatarTile(
                    name = p.name,
                    avatar = p.avatar,
                    face = p.face,
                    caption = when {
                        managing -> "Bewerken"
                        p.kids -> "Kinderprofiel"
                        p.pinHash != null -> "Pincode"
                        else -> null
                    },
                    modifier = Modifier.focusRequester(focus[i]),
                    onClick = {
                        if (managing) {
                            whenUnlocked {
                                val hash = p.pinHash
                                if (hash == null || hash in lock) onEdit(p.id)
                                else pinFor = PinRequest("Pincode van ${p.name}", null, listOf(hash)) { onEdit(p.id) }
                            }
                        } else {
                            val hash = p.pinHash
                            if (hash == null) vm.pick(p.id, onPicked)
                            else pinFor = PinRequest("Pincode van ${p.name}", null, listOf(hash)) { vm.pick(p.id, onPicked) }
                        }
                    },
                )
            }
            if (s.profiles.size < ProfileRepository.MAX_PROFILES) {
                AvatarTile(name = "Toevoegen", avatar = AvatarAdd, symbol = "+", onClick = { whenUnlocked { onEdit(0) } })
            }
        }
        SecondaryButton(
            if (managing) "Klaar" else "Profielen beheren",
            { if (managing) managing = false else whenUnlocked { managing = true } },
        )
    }

    pinFor?.let { req ->
        PinDialog(
            title = req.title,
            subtitle = req.subtitle,
            onPin = { pin ->
                if (req.hashes.any { Pin.verify(pin, it) }) {
                    pinFor = null
                    req.onOk()
                    null
                } else "Onjuiste pincode"
            },
            onDismiss = { pinFor = null },
        )
    }
}

private class PinRequest(val title: String, val subtitle: String?, val hashes: List<String>, val onOk: () -> Unit)

/** The "+" tile uses the grey avatar colour. */
private const val AvatarAdd = 7
