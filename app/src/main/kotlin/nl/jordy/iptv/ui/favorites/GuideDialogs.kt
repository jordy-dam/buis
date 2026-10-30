package nl.jordy.iptv.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jordy.iptv.data.repo.Guide
import nl.jordy.iptv.data.repo.GuideRepository
import nl.jordy.iptv.ui.components.ListItemSurface
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.components.TvTextField
import nl.jordy.iptv.ui.components.ignoreHeldKey
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary

@Composable
private fun DialogColumn(content: @Composable () -> Unit) = Column(
    Modifier.ignoreHeldKey().background(SurfaceColor, RoundedCornerShape(16.dp)).padding(32.dp).width(560.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
) { content() }

/** A new guide, or a new name for one. Left empty, it's called "Mijn gids". */
@Composable
fun GuideNameDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        DialogColumn {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            TvTextField(
                "Naam",
                name,
                { name = it },
                // Down from the wide field would land on whichever button is nearest its middle: Annuleren.
                Modifier.focusRequester(field).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) { save.requestFocus(); true } else false
                },
                imeAction = ImeAction.Done,
                onImeAction = { onSave(name) },
            )
            Text("Leeg laten heet hij \"${GuideRepository.DEFAULT_NAME}\".", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryButton("Opslaan", { onSave(name) }, Modifier.focusRequester(save))
                SecondaryButton("Annuleren", onDismiss)
            }
        }
    }
    LaunchedEffect(Unit) { field.requestFocus() }
}

/** Long-press on a guide in Favorieten: rename or delete it. Deleting asks again, focus on Annuleren. */
@Composable
fun GuideMenuDialog(guide: Guide, onRename: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val first = remember(confirm) { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        DialogColumn {
            if (!confirm) {
                Text(guide.name, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PrimaryButton("Naam wijzigen", onRename, Modifier.focusRequester(first))
                    SecondaryButton("Verwijderen", { confirm = true })
                    SecondaryButton("Annuleren", onDismiss)
                }
            } else {
                Text("${guide.name} verwijderen?", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "De gids en zijn ${guide.channels.size} zenders verdwijnen. De zenders zelf blijven in Live TV.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SecondaryButton("Annuleren", onDismiss, Modifier.focusRequester(first))
                    PrimaryButton("Verwijderen", onDelete)
                }
            }
        }
    }
    LaunchedEffect(confirm) { first.requestFocus() }
}

/**
 * Long-press on a channel in a guide. Omhoog and Omlaag keep the dialog open, so a channel moves
 * several places with repeated OKs; the line above says where it is now.
 */
@Composable
fun GuideChannelDialog(
    name: String,
    place: Int,
    count: Int,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val up = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        DialogColumn {
            Text(name, style = MaterialTheme.typography.headlineMedium)
            Text("Plek $place van $count", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryButton("Omhoog", { onMove(-1) }, Modifier.focusRequester(up), enabled = place > 1)
                PrimaryButton("Omlaag", { onMove(1) }, enabled = place < count)
                SecondaryButton("Uit gids", onRemove)
                SecondaryButton("Klaar", onDismiss)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { up.requestFocus() } }
}

/** Long-press on a channel in Live TV with two guides or more: tick the guides it should be in. */
@Composable
fun GuidePickDialog(channelName: String, guides: List<Guide>, streamId: Int, onToggle: (Guide) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        DialogColumn {
            Text("$channelName in gids", style = MaterialTheme.typography.headlineMedium)
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(guides, key = { it.id }) { g ->
                    ListItemSurface(
                        text = g.name,
                        selected = false,
                        checked = g.channels.any { it.streamId == streamId },
                        onClick = { onToggle(g) },
                        modifier = if (g == guides.first()) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
            SecondaryButton("Klaar", onDismiss)
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
