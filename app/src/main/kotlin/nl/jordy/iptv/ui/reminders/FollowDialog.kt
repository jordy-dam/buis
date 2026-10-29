package nl.jordy.iptv.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.jordy.iptv.data.repo.FollowMatch
import nl.jordy.iptv.ui.components.Focus
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.TvTextField
import nl.jordy.iptv.ui.home.whenLabel
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary

/**
 * The profile's search terms, from Instellingen and from a reminder. Adding comes first (it's what
 * you come for), below it each term with what it finds next; OK on a term removes it.
 */
@Composable
internal fun FollowDialog(
    terms: List<String>,
    next: Map<String, FollowMatch?>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(SurfaceColor, RoundedCornerShape(16.dp)).padding(24.dp).width(520.dp).heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Volgen", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Bijvoorbeeld \"Formule 1\" of \"FC Utrecht\". BUIS zoekt in titels en omschrijvingen van de gids (zo'n anderhalve dag vooruit) en meldt het als het bijna begint.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            TvTextField(
                label = "Toevoegen",
                value = draft,
                onValueChange = { draft = it },
                imeAction = ImeAction.Done,
                onImeAction = {
                    onAdd(draft)
                    draft = ""
                },
            )
            if (terms.isNotEmpty()) {
                Text("Je volgt · OK haalt een zoekterm weg", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(terms, key = { it }) { term ->
                    Surface(
                        onClick = { onRemove(term) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        colors = Focus.bar(container = Color.Transparent),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(term, style = MaterialTheme.typography.titleMedium)
                            val m = next[term]
                            Text(
                                m?.let { "${whenLabel(it.start)} · ${it.channelName} · ${it.title}" } ?: "Nog niets in de gids",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            PrimaryButton("Klaar", {
                if (draft.isNotBlank()) onAdd(draft)
                onDismiss()
            })
        }
    }
}
