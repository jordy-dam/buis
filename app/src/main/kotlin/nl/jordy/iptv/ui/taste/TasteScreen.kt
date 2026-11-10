package nl.jordy.iptv.ui.taste

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.ui.TasteRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.repo.Taste
import nl.jordy.iptv.data.repo.TasteRepository
import nl.jordy.iptv.ui.components.PrimaryButton
import nl.jordy.iptv.ui.components.SecondaryButton
import nl.jordy.iptv.ui.theme.Background
import nl.jordy.iptv.ui.theme.Live
import nl.jordy.iptv.ui.theme.SafeArea
import nl.jordy.iptv.ui.theme.SurfaceColor
import nl.jordy.iptv.ui.theme.TextSecondary
import javax.inject.Inject

data class TasteUiState(
    /** Null while loading. */
    val films: List<Taste.Film>? = null,
    val index: Int = 0,
    /** The last answer, so the poster leaves in that direction. */
    val last: Int = Taste.UNKNOWN,
    val liked: Int = 0,
) {
    val current get() = films?.getOrNull(index)
    val done get() = films != null && films.isNotEmpty() && index >= films.size
}

@HiltViewModel
class TasteViewModel @Inject constructor(savedState: SavedStateHandle, private val taste: TasteRepository) : ViewModel() {
    /** Series instead of films: opened from "Series voor jou". */
    val series = savedState.toRoute<TasteRoute>().series
    private val type = if (series) ContentType.SERIES else ContentType.MOVIE

    private val _state = MutableStateFlow(TasteUiState())
    val state = _state.asStateFlow()

    init { more() }

    /** A fresh round of ten; films already answered don't come back. */
    fun more() {
        _state.value = TasteUiState()
        viewModelScope.launch { _state.value = TasteUiState(films = taste.probes(type)) }
    }

    fun answer(vote: Int) {
        val s = _state.value
        val f = s.current ?: return
        _state.value = s.copy(index = s.index + 1, last = vote, liked = s.liked + if (vote == Taste.LIKE || vote == Taste.WANT) 1 else 0)
        viewModelScope.launch { taste.vote(f.streamId, f.title, vote, f.key, type, f.posterUrl) }
    }
}

/**
 * "Leer mijn smaak": ten known films, one at a time. Right = leuk, Left = niks, Up = wil ik zien
 * (to Favorieten), Down = ken ik niet.
 * Every answer is saved at once, so Back halfway keeps what was answered.
 */
@Composable
fun TasteScreen(onDone: () -> Unit, vm: TasteViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val films = s.films
    Row(Modifier.fillMaxSize().background(Background).padding(SafeArea), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        Column(Modifier.width(380.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Leer mijn smaak", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (vm.series) "Zeg van tien series of je ze leuk vindt. BUIS zoekt er series bij die je nog niet zag, onder Series voor jou op Home."
                else "Zeg van tien films of je ze leuk vindt. BUIS zoekt er films bij die je nog niet zag, onder Films voor jou op Home.",
                style = MaterialTheme.typography.bodyLarge, color = TextSecondary,
            )
            if (films != null && films.isNotEmpty() && !s.done) {
                Text("${s.index + 1} van ${films.size}", style = MaterialTheme.typography.titleMedium, color = Live, modifier = Modifier.padding(top = 12.dp))
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KeyHint(Icons.AutoMirrored.Filled.KeyboardArrowRight, Icons.Filled.ThumbUp, "Leuk")
                    KeyHint(Icons.AutoMirrored.Filled.KeyboardArrowLeft, Icons.Filled.ThumbDown, "Niks voor mij")
                    KeyHint(Icons.Filled.KeyboardArrowUp, Icons.Filled.BookmarkAdd, "Wil ik zien", "naar Favorieten")
                    KeyHint(Icons.Filled.KeyboardArrowDown, Icons.Filled.VisibilityOff, "Ken ik niet")
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            when {
                films == null -> Unit
                films.isEmpty() -> Text(
                    if (vm.series) "Nog geen series om te laten zien in je voorkeurslanden."
                    else "Nog geen films om te laten zien. BUIS haalt filmgegevens op de achtergrond op voor je voorkeurslanden; probeer het straks nog eens.",
                    style = MaterialTheme.typography.bodyLarge, color = TextSecondary,
                )
                s.done -> Done(s.liked, vm.series, vm::more, onDone)
                else -> Swiper(s, vm::answer)
            }
        }
    }
}

@Composable
private fun Swiper(s: TasteUiState, onAnswer: (Int) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().focusRequester(focus).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            // A held arrow repeats: one film per press, not a run through the round.
            if (e.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent e.key in answerKeys
            when (e.key) {
                Key.DirectionRight -> onAnswer(Taste.LIKE)
                Key.DirectionLeft -> onAnswer(Taste.DISLIKE)
                Key.DirectionDown -> onAnswer(Taste.UNKNOWN)
                Key.DirectionUp -> onAnswer(Taste.WANT)
                else -> return@onPreviewKeyEvent false
            }
            true
        }.focusable(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = s.index,
            transitionSpec = {
                val out = when (s.last) {
                    Taste.LIKE -> slideOutHorizontally(tween(220)) { it }
                    Taste.DISLIKE -> slideOutHorizontally(tween(220)) { -it }
                    Taste.WANT -> slideOutVertically(tween(220)) { -it / 2 }
                    else -> slideOutVertically(tween(220)) { it / 2 }
                }
                (fadeIn(tween(200, delayMillis = 80)) + scaleIn(tween(200, delayMillis = 80), initialScale = 0.92f)) togetherWith (out + fadeOut(tween(220)))
            },
            label = "taste",
        ) { i ->
            s.films?.getOrNull(i)?.let { FilmCard(it) }
        }
    }
}

@Composable
private fun FilmCard(f: Taste.Film) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.height(330.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(SurfaceColor)) {
            Text(f.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
            AsyncImage(model = f.posterUrl, contentDescription = f.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(Taste.displayTitle(f.title, f.year), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(f.year?.toString(), Taste.ratingLabel(f.rating), f.genres.take(3).joinToString(", ").ifEmpty { null }).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge, color = TextSecondary, maxLines = 1,
        )
    }
}

@Composable
private fun Done(liked: Int, series: Boolean, onMore: () -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Klaar", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (liked > 0) "Je suggesties staan op Home bij ${if (series) "Series" else "Films"} voor jou. Hoe vaker je antwoordt, hoe beter ze worden."
            else "Geen enkele leuk gevonden, dus nog niets om op te bouwen. Probeer er nog tien.",
            style = MaterialTheme.typography.bodyLarge, color = TextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
            PrimaryButton(if (series) "Nog 10 series" else "Nog 10 films", onMore, Modifier.focusRequester(focus))
            SecondaryButton("Naar Home", onDone)
        }
    }
}

@Composable
private fun KeyHint(key: ImageVector, icon: ImageVector, text: String, note: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(key, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(30.dp))
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
    }
}

private val answerKeys = setOf(Key.DirectionRight, Key.DirectionLeft, Key.DirectionDown, Key.DirectionUp)
