package nl.jordy.iptv.ui.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import nl.jordy.iptv.ui.components.AvatarFaces
import nl.jordy.iptv.ui.components.AvatarGadgets
import nl.jordy.iptv.ui.components.AvatarMarks
import nl.jordy.iptv.ui.components.avatarColor
import nl.jordy.iptv.ui.theme.SurfaceRaised
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import nl.jordy.iptv.ui.settings.Header
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
    val gadget: Int = 0,
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
                ProfileForm(true, isNew = false, p.name, p.avatar, p.face, p.gadget, p.kids, p.pinHash, canDelete = all.size > 1)
            }
        }
    }

    fun setName(name: String) = _form.update { it.copy(name = name.take(MAX_NAME)) }
    fun setAvatar(i: Int) = _form.update { it.copy(avatar = i) }
    fun setFace(i: Int) = _form.update { it.copy(face = i) }
    fun setGadget(i: Int) = _form.update { it.copy(gadget = i) }
    fun toggleKids() = _form.update { it.copy(kids = !it.kids) }
    fun setPin(pin: String?) = _form.update { it.copy(pinHash = pin?.let(Pin::hash)) }

    fun save(then: () -> Unit) = viewModelScope.launch {
        val f = _form.value
        if (f.name.isBlank()) return@launch
        val p = original
        if (p == null) profiles.create(f.name, f.avatar, f.kids, f.pinHash, f.face, f.gadget)
        else profiles.update(p.copy(name = f.name, avatar = f.avatar, face = f.face, gadget = f.gadget, kids = f.kids, pinHash = f.pinHash))
        then()
    }

    fun delete(then: () -> Unit) = viewModelScope.launch {
        if (profiles.delete(id)) then()
    }

    private companion object {
        const val MAX_NAME = 20
    }
}

/** The three parts of an avatar, each chosen in its own pop-up from the name row. */
private enum class Pick(val title: String) { FACE("Kies een gezicht"), COLOUR("Kies een kleur"), GADGET("Kies een gadget") }

/**
 * Name, look and access of one profile. The look is three round buttons next to the name (face,
 * colour, gadget), each opening a pop-up with the choices; the avatar on the left shows the
 * combination as it will look, with its marks (PIN or kids upper left, gadget lower right).
 * Nothing is saved until "Opslaan".
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProfileEditScreen(onDone: () -> Unit, vm: ProfileEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    if (!f.loaded) return
    var pinStep by remember { mutableStateOf<String?>(null) } // null: closed, "": first entry, else: first PIN to repeat
    var confirmDelete by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    // The PIN rows change when a PIN is set or removed; focus goes back to them, not to the top.
    val pinRow = remember { FocusRequester() }
    var pinChanged by remember { mutableIntStateOf(0) }
    // The open pop-up, and the button to land on again once it closes.
    var pick by remember { mutableStateOf<Pick?>(null) }
    val pickButton = remember { Pick.entries.associateWith { FocusRequester() } }
    var back by remember { mutableStateOf<Pick?>(null) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LaunchedEffect(pinChanged) { if (pinChanged > 0) runCatching { pinRow.requestFocus() } }
    LaunchedEffect(back) { back?.let { runCatching { pickButton.getValue(it).requestFocus() }; back = null } }
    val status = when {
        f.kids -> Icons.Filled.ChildCare
        f.pinHash != null -> Icons.Filled.Lock
        else -> null
    }

    Row(Modifier.fillMaxSize().padding(SafeArea), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(160.dp)) {
                AvatarCircle(f.name, f.avatar, 160.dp, face = f.face)
                AvatarMarks(160.dp, status, gadget = f.gadget)
            }
            Text(f.name.ifBlank { "Nieuw profiel" }, style = MaterialTheme.typography.headlineMedium)
        }
        Column(
            Modifier.width(640.dp).focusRestorer(first).focusGroup().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (f.isNew) "Profiel toevoegen" else "Profiel bewerken", style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvTextField("Naam", f.name, vm::setName, Modifier.weight(1f).focusRequester(first))
                LookButton("Gezicht", { pick = Pick.FACE }, Modifier.focusRequester(pickButton.getValue(Pick.FACE))) {
                    AvatarCircle(f.name, f.avatar, LOOK, face = f.face)
                }
                LookButton("Kleur", { pick = Pick.COLOUR }, Modifier.focusRequester(pickButton.getValue(Pick.COLOUR))) {
                    Box(Modifier.size(LOOK).background(avatarColor(f.avatar), CircleShape))
                }
                LookButton("Gadget", { pick = Pick.GADGET }, Modifier.focusRequester(pickButton.getValue(Pick.GADGET))) {
                    GadgetCircle(f.gadget, LOOK)
                }
            }

            Header("Toegang")
            SettingRow(
                "Pincode",
                "Vraagt om 4 cijfers bij het kiezen van dit profiel.",
                toggle = f.pinHash != null,
                // On asks for a new PIN; off removes it (only once you save).
                onClick = { if (f.pinHash == null) pinStep = "" else { vm.setPin(null); pinChanged++ } },
                modifier = Modifier.focusRequester(pinRow),
            )
            if (f.pinHash != null) SettingRow("Pincode wijzigen", onClick = { pinStep = "" })
            SettingRow(
                "Kinderprofiel",
                "Verbergt categorieën voor volwassenen. Werkt pas echt als de andere profielen een pincode hebben.",
                toggle = f.kids,
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

    pick?.let { p ->
        val close = { pick = null; back = p }
        when (p) {
            // Faces and colours show the whole avatar, so you pick the combination you'll see.
            Pick.FACE -> PickDialog(p, (0..ProfileRepository.FACES).toList(), f.face, { AvatarFaces.name(it) }, { vm.setFace(it); close() }, close) { i ->
                AvatarCircle(f.name, f.avatar, OPTION, face = i)
            }
            Pick.COLOUR -> PickDialog(p, AvatarColors.indices.toList(), f.avatar, { "" }, { vm.setAvatar(it); close() }, close, perRow = 6) { i ->
                AvatarCircle(f.name, i, OPTION, face = f.face)
            }
            Pick.GADGET -> PickDialog(p, (0..AvatarGadgets.all.size).toList(), f.gadget, { AvatarGadgets.name(it) }, { vm.setGadget(it); close() }, close, perRow = 6) { i ->
                GadgetCircle(i, OPTION)
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

/** The size of the look buttons in the name row, and of the choices in their pop-ups. */
private val LOOK = 48.dp
private val OPTION = 80.dp

/** A gadget on a plain disc, or "Geen" for none: the button and the pop-up's choices. */
@Composable
private fun GadgetCircle(gadget: Int, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(SurfaceRaised, CircleShape), contentAlignment = Alignment.Center) {
        val icon = AvatarGadgets.get(gadget)
        if (icon != null) Icon(icon, contentDescription = AvatarGadgets.name(gadget), tint = TextPrimary, modifier = Modifier.size(size * 0.5f))
        else Text("Geen", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    }
}

/** A round button with its name above it, level with the name field's label. */
@Composable
private fun LookButton(label: String, onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        Ring(onClick, modifier.size(LOOK), selected = false, content = content)
    }
}

/** A round choice: a ring when focused (and a thinner one on the current choice), slightly larger. */
@Composable
private fun Ring(onClick: () -> Unit, modifier: Modifier, selected: Boolean, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, TextSecondary), shape = CircleShape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, TextPrimary), shape = CircleShape),
        ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
    ) { content() }
}

/**
 * One of the look's pop-ups: the choices on black, so only they count, with the focused one's
 * name underneath. Focus starts on the current choice; OK takes it, Back keeps what it was.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickDialog(
    pick: Pick,
    options: List<Int>,
    current: Int,
    name: (Int) -> String,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    perRow: Int = 7,
    option: @Composable (Int) -> Unit,
) {
    val start = remember { FocusRequester() }
    var focused by remember { mutableIntStateOf(current) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Text(pick.title, style = MaterialTheme.typography.headlineMedium)
                FlowRow(
                    Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    maxItemsInEachRow = perRow,
                ) {
                    options.forEach { i ->
                        Ring(
                            { onPick(i) },
                            Modifier.size(OPTION)
                                .then(if (i == current) Modifier.focusRequester(start) else Modifier)
                                .onFocusChanged { if (it.isFocused) focused = i },
                            selected = i == current,
                        ) { option(i) }
                    }
                }
                Text(name(focused), style = MaterialTheme.typography.titleLarge, color = TextSecondary)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { start.requestFocus() } }
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
