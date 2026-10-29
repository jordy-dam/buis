package nl.jordy.iptv.ui.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import nl.jordy.iptv.ui.theme.Background
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary
import java.util.Locale

/** Hero layout shared by film and series detail: backdrop fading into the background, poster, facts, plot. */
@Composable
fun DetailScaffold(
    title: String,
    posterUrl: String?,
    backdropUrl: String?,
    facts: List<String>,
    plot: String?,
    actions: @Composable () -> Unit,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    // A TV screen is only 540dp tall: with content below the hero, the hero has to shrink.
    val compact = below != null
    Box(Modifier.fillMaxSize().background(Background)) {
        (backdropUrl ?: posterUrl)?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.45f)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Background, 0.55f to Background.copy(alpha = 0.75f), 1f to Color.Transparent),
            ),
        )
        Column(Modifier.fillMaxSize().padding(SafeArea)) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.width(if (compact) 120.dp else 220.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(SurfaceColor), contentAlignment = Alignment.Center) {
                    Text(title, color = TextSecondary, modifier = Modifier.padding(12.dp))
                    posterUrl?.let { AsyncImage(model = it, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.width(40.dp))
                Column(Modifier.width(820.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)) {
                    Text(title, style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (facts.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        facts.forEachIndexed { i, f ->
                            if (i > 0) Text("   ·   ", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                            if (f.startsWith(RATING_MARK)) {
                                androidx.tv.material3.Icon(Icons.Filled.Star, contentDescription = "Beoordeling", tint = TextSecondary, modifier = Modifier.padding(end = 4.dp).size(18.dp))
                            }
                            Text(f.removePrefix(RATING_MARK), style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        }
                    }
                    plot?.let { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = if (compact) 2 else 5, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.width(1.dp))
                    actions()
                }
            }
            below?.let { Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = it) }
        }
    }
}

/** Marks the rating among the facts; the hero draws it as a star icon. */
internal const val RATING_MARK = "\u0001"

fun facts(year: Int?, rating: Double?, genre: String?, durationSecs: Int?): List<String> = listOfNotNull(
    year?.toString(),
    rating?.let { RATING_MARK + String.format(Locale.forLanguageTag("nl-NL"), "%.1f", it) },
    genre,
    durationSecs?.takeIf { it > 0 }?.let { s -> if (s >= 3600) "${s / 3600} u ${s % 3600 / 60} min" else "${s / 60} min" },
)
