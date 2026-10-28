package nl.jordy.iptv.ui.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.ProfileEntity
import nl.jordy.iptv.data.profile.Pin
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.ui.ProfileEditRoute
import nl.jordy.iptv.ui.components.AvatarCircle
import nl.jordy.iptv.ui.components.AvatarColors
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.components.TvTextField
import nl.jordy.iptv.ui.settings.SettingRow
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextPrimary
import nl.jordy.iptv.ui.theme.TextSecondary
import javax.inject.Inject

data class ProfileForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val avatar: Int = 0,
    val face: Int = 0,
    val kids: Boolean = false,
    val pinHash: String? = null,
    val canDelete: Boolean = false,
)

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val id = savedState.toRoute<ProfileEditRoute>().id
    private var original: ProfileEntity? = null
    private val _form = MutableStateFlow(ProfileForm())
    val form = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val all = profiles.all()
            val p = profiles.get(id)
            original = p
            _form.value = if (p == null) {
                // A face and colour nobody has yet, so the new profile is easy to tell apart.
                val (face, colour) = ProfileRepository.defaultLook(all)
                ProfileForm(loaded = true, avatar = colour, face = face)
            } else {
                ProfileForm(true, isNew = false, p.name, p.avatar, p.face, p.kids, p.pinHash, canDelete = all.size > 1)
            }
        }
    }

    fun setName(name: String) = _form.update { it.copy(name = name.take(MAX_NAME)) }
    fun setAvatar(i: Int) = _form.update { it.copy(avatar = i) }
    fun setFace(i: Int) = _form.update { it.copy(face = i) }
    fun toggleKids() = _form.update { it.copy(kids = !it.kids) }
    fun setPin(pin: String?) = _form.update { it.copy(pinHash = pin?.let(Pin::hash)) }

    fun save(then: () -> Unit) = viewModelScope.launch {
        val f = _form.value
        if (f.name.isBlank()) return@launch
        val p = original
        if (p == null) profiles.create(f.name, f.avatar, f.kids, f.pinHash, f.face)
        else profiles.update(p.copy(name = f.name, avatar = f.avatar, face = f.face, kids = f.kids, pinHash = f.pinHash))
        then()
    }

    fun delete(then: () -> Unit) = viewModelScope.launch {
        if (profiles.delete(id)) then()
    }

    private companion object {
        const val MAX_NAME = 20
    }
}

/** Name, colour, PIN and kids setting of one profile. Nothing is saved until "Opslaan". */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProfileEditScreen(onDone: () -> Unit, vm: ProfileEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    if (!f.loaded) return
    var pinStep by remember { mutableStateOf<String?>(null) } // null: closed, "": first entry, else: first PIN to repeat
    var confirmDelete by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    // The PIN rows swap places when a PIN is set or removed; focus goes back to them, not to the top.
    val pinRow = remember { FocusRequester() }
    var pinChanged by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LaunchedEffect(pinChanged) { if (pinChanged > 0) runCatching { pinRow.requestFocus() } }

    Row(Modifier.fillMaxSize().padding(SafeArea), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AvatarCircle(f.name, f.avatar, 160.dp, face = f.face)
            Text(f.name.ifBlank { "Nieuw profiel" }, style = MaterialTheme.typography.headlineMedium)
        }
        Column(
            Modifier.width(640.dp).focusRestorer(first).focusGroup().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (f.isNew) "Profiel toevoegen" else "Profiel bewerken", style = MaterialTheme.typography.headlineMedium)
            TvTextField("Naam", f.name, vm::setName, Modifier.focusRequester(first))

            Text("Gezicht", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // 0 is the initial letter, 1..5 the drawn faces.
                (0..ProfileRepository.FACES).forEach { i -> FaceDot(f.name, f.avatar, i, selected = i == f.face, onClick = { vm.setFace(i) }) }
            }

            Text("Kleur", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AvatarColors.indices.forEach { i -> ColorDot(i, selected = i == f.avatar, onClick = { vm.setAvatar(i) }) }
            }

            if (f.pinHash == null) {
                SettingRow("Pincode", "Vraagt om 4 cijfers bij het kiezen van dit profiel.", value = "Uit", onClick = { pinStep = "" }, modifier = Modifier.focusRequester(pinRow))
            } else {
                SettingRow("Pincode wijzigen", value = "Aan", onClick = { pinStep = "" }, modifier = Modifier.focusRequester(pinRow))
                SettingRow("Pincode verwijderen", onClick = { vm.setPin(null); pinChanged++ })
            }
            SettingRow(
                "Kinderprofiel",
                "Verbergt categorieën voor volwassenen. Werkt pas echt als de andere profielen een pincode hebben.",
                value = if (f.kids) "Aan" else "Uit",
                onClick = vm::toggleKids,
            )

            // Coming down into the buttons always lands on Opslaan, not on whichever is nearest.
            Row(Modifier.padding(top = 16.dp).focusRestorer(save).focusGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryButton("Opslaan", { vm.save(onDone) }, Modifier.focusRequester(save), enabled = f.name.isNotBlank())
                SecondaryButton("Annuleren", onDone)
            }
            if (f.canDelete) {
                SettingRow("Profiel verwijderen", "Ook de favorieten en kijkgeschiedenis van dit profiel.", onClick = { confirmDelete = true }, destructive = true)
            }
        }
    }

    pinStep?.let { step ->
        PinDialog(
            title = if (step.isEmpty()) "Kies een pincode" else "Herhaal de pincode",
            onPin = { pin ->
                when {
                    step.isEmpty() -> { pinStep = pin; null }
                    pin == step -> { vm.setPin(pin); pinStep = null; pinChanged++; null }
                    else -> { pinStep = ""; "Pincodes verschillen, probeer opnieuw" }
                }
            },
            onDismiss = { pinStep = null; pinChanged++ },
        )
    }
    if (confirmDelete) {
        DeleteDialog(f.name, onConfirm = { vm.delete(onDone) }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun FaceDot(name: String, colour: Int, face: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(3.dp, TextPrimary), shape = CircleShape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, TextPrimary), shape = CircleShape),
        ),
        colors = ClickableSurfaceDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent),
    ) { AvatarCircle(name, colour, 52.dp, face = face) }
}

@Composable
private fun ColorDot(index: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(3.dp, TextPrimary), shape = CircleShape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, TextPrimary), shape = CircleShape),
        ),
        colors = ClickableSurfaceDefaults.colors(containerColor = AvatarColors[index], focusedContainerColor = AvatarColors[index]),
    ) {}
}

@Composable
private fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancel = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(SurfaceColor, RoundedCornerShape(16.dp)).padding(32.dp).width(520.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("$name verwijderen?", style = MaterialTheme.typography.headlineMedium)
            Text("De favorieten en kijkgeschiedenis van dit profiel worden gewist.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Default focus on cancelling: this wipes data.
                SecondaryButton("Annuleren", onDismiss, Modifier.focusRequester(cancel))
                PrimaryButton("Verwijderen", onConfirm)
            }
        }
    }
    LaunchedEffect(Unit) { cancel.requestFocus() }
}
