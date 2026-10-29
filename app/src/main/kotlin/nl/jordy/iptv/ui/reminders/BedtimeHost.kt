package nl.jordy.iptv.ui.reminders

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.profile.Bedtime
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.profile.prefs
import nl.jordy.iptv.ui.components.CornerCard
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.components.ignoreHeldKey
import nl.jordy.iptv.ui.theme.SafeArea
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Checks every 30 seconds whether the active profile's Bedtijd has come. */
@HiltViewModel
class BedtimeViewModel @Inject constructor(private val profiles: ProfileRepository) : ViewModel() {

    /** Per profile, the evening the card was shown, so it comes once an evening. */
    private val shown = mutableMapOf<Long, LocalDate>()

    /** The time to show ("23:04") while the card is up. */
    val due = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            while (isActive) {
                val p = profiles.current.value
                if (due.value == null && p != null) {
                    val now = LocalDateTime.now()
                    if (Bedtime.due(now, p.prefs.bedtime, p.prefs.bedtimeDays, shown[p.id])) {
                        shown[p.id] = Bedtime.eveningOf(now)
                        due.value = now.format(DateTimeFormatter.ofPattern("HH:mm"))
                    }
                }
                delay(30_000)
            }
        }
    }

    fun dismiss() {
        due.value = null
    }
}

/**
 * Bedtijd's question, in the corner like every card. Doorkijken has focus, so one OK carries on;
 * Naar bed closes BUIS, and the player saves the position on its way out as it always does.
 */
@Composable
fun BedtimeHost(active: Boolean, vm: BedtimeViewModel = hiltViewModel()) {
    val time by vm.due.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val t = time ?: return
    if (!active) return
    val stay = remember { FocusRequester() }
    Dialog(onDismissRequest = vm::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0.25f)
        Box(Modifier.fillMaxSize().padding(SafeArea), contentAlignment = Alignment.BottomEnd) {
            CornerCard(
                label = "Het is $t · Bedtijd",
                title = "Doorkijken of lekker naar bed?",
                modifier = Modifier.ignoreHeldKey(),
            ) {
                PrimaryButton("Doorkijken", vm::dismiss, Modifier.focusRequester(stay))
                SecondaryButton("Naar bed", {
                    vm.dismiss()
                    activity?.finish()
                })
            }
        }
    }
    LaunchedEffect(t) { runCatching { stay.requestFocus() } }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
