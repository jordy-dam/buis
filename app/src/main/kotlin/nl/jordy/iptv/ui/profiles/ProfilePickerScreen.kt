package nl.jordy.iptv.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Lock
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

    // The easter egg listens without taking keys away; only the code's last OK is its own.
    val code = remember { TestbeeldCode() }
    var testbeeld by remember { mutableStateOf(false) }
    // Buttons click on release: the code's last OK must not also press the focused button.
    var swallowRelease by remember { mutableStateOf(false) }
    if (testbeeld) {
        TestbeeldOverlay(onDone = {
            testbeeld = false
            val i = s.profiles.indexOfFirst { it.id == s.lastUsedId }.coerceAtLeast(0)
            runCatching { focus.getOrNull(i)?.requestFocus() }
        })
        return
    }
    PickerLayout(
        profiles = s.profiles,
        managing = managing,
        focus = focus,
        onProfile = { p ->
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
        onAdd = { whenUnlocked { onEdit(0) } },
        onManage = { if (managing) managing = false else whenUnlocked { managing = true } },
        modifier = Modifier.onPreviewKeyEvent { e ->
            when (e.type) {
                KeyEventType.KeyDown -> e.nativeKeyEvent.repeatCount == 0 && code.feed(e.key.nativeKeyCode).also {
                    if (it) {
                        swallowRelease = true
                        testbeeld = true
                    }
                }
                KeyEventType.KeyUp -> swallowRelease.also { swallowRelease = false }
                else -> false
            }
        },
    )

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

/**
 * Logo, title, the profile tiles and the manage button. Kids and PIN show as a badge on the avatar
 * (no caption lines, so all tiles line up, also in "Profielen beheren"). Up to five tiles sit in one
 * row; more go into two even rows (6 → 3 + 3, 9 → 5 + 4) with smaller avatars and no logo, so eight profiles
 * and "+" still fit a 540 dp tall screen. Plain rows on purpose: a FlowRow that runs out of height
 * silently leaves out the rows that don't fit, which made the fifth profile "delete" two others.
 */
@Composable
internal fun PickerLayout(
    profiles: List<ProfileEntity>,
    managing: Boolean,
    focus: List<FocusRequester>,
    onProfile: (ProfileEntity) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAdd = profiles.size < ProfileRepository.MAX_PROFILES
    val tiles: List<ProfileEntity?> = profiles + if (canAdd) listOf(null) else emptyList()
    val twoRows = tiles.size > 5
    val perRow = if (twoRows) (tiles.size + 1) / 2 else tiles.size
    val size = if (twoRows) 80.dp else 88.dp
    Column(
        modifier.fillMaxSize().padding(SafeArea),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (twoRows) 24.dp else 32.dp, Alignment.CenterVertically),
    ) {
        if (!twoRows) Image(painterResource(R.drawable.ic_buis), contentDescription = "BUIS", modifier = Modifier.size(96.dp))
        Text(if (managing) "Profielen beheren" else "Wie kijkt er?", style = MaterialTheme.typography.displaySmall)
        Column(
            // A little air between the question and the faces.
            Modifier.padding(top = 16.dp).focusRestorer().focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            tiles.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { p ->
                        if (p == null) {
                            AvatarTile(name = "Toevoegen", avatar = AvatarAdd, symbol = "+", size = size, onClick = onAdd)
                        } else {
                            // Kids wins over a PIN: a kids profile with a PIN is still a kids profile first.
                            val (badge, what) = when {
                                p.kids -> Icons.Filled.ChildCare to "Kinderprofiel"
                                p.pinHash != null -> Icons.Filled.Lock to "Pincode"
                                else -> null to null
                            }
                            AvatarTile(
                                name = p.name,
                                avatar = p.avatar,
                                face = p.face,
                                size = size,
                                badge = badge,
                                badgeDescription = what,
                                gadget = p.gadget,
                                modifier = Modifier.focusRequester(focus[profiles.indexOf(p)]),
                                onClick = { onProfile(p) },
                            )
                        }
                    }
                }
            }
        }
        SecondaryButton(if (managing) "Klaar" else "Profielen beheren", onManage)
    }
}

private class PinRequest(val title: String, val subtitle: String?, val hashes: List<String>, val onOk: () -> Unit)

/** The "+" tile uses the grey avatar colour. */
private const val AvatarAdd = 7
