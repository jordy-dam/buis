package nl.jordy.iptv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import nl.jordy.iptv.data.repo.SearchRepository
import nl.jordy.iptv.data.repo.SearchResults
import nl.jordy.iptv.data.settings.SettingsStore
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.components.ChannelTile
import nl.jordy.iptv.ui.components.PosterTile
import nl.jordy.iptv.ui.components.TvTextField
import nl.jordy.iptv.ui.components.lastInRow
import nl.jordy.iptv.ui.theme.TextSecondary
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(repo: SearchRepository, settings: SettingsStore) : ViewModel() {
    val query = MutableStateFlow("")

    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val results = combine(query.debounce(300), settings.prefs) { q, p -> q to p.preferredCountries }
        .mapLatest { (q, preferred) -> if (q.isBlank()) null else repo.search(q, preferred) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    onPlay: (PlayerRoute) -> Unit,
    onOpenMovie: (Int) -> Unit,
    onOpenSeries: (Int) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Zoeken", style = MaterialTheme.typography.headlineMedium) }
        item {
            TvTextField(
                "Zender, film of serie",
                query,
                { vm.query.value = it },
                Modifier.width(560.dp),
                imeAction = ImeAction.Search,
            )
        }
        val r: SearchResults? = results
        when {
            query.isBlank() -> item {
                Text("Druk OK op het zoekveld en typ een paar letters. Voorkeurslanden staan bovenaan.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
            r == null -> Unit
            r.isEmpty -> item { Text("Niets gevonden voor \"$query\".", style = MaterialTheme.typography.bodyLarge, color = TextSecondary) }
            else -> {
                if (r.channels.isNotEmpty()) {
                    item { Text("Zenders", style = MaterialTheme.typography.titleLarge) }
                    items(r.channels.take(MAX_CHANNELS), key = { "live/${it.streamId}" }) { c ->
                        ChannelTile(
                            name = c.name,
                            logoUrl = c.logoUrl,
                            nowPlaying = null,
                            badges = c.badges.split(",").filter { it.isNotEmpty() },
                            onClick = { onPlay(PlayerRoute(PlayerRoute.Kind.LIVE, c.streamId, c.name, categoryId = c.categoryId)) },
                            onLongClick = {},
                            modifier = Modifier.width(720.dp),
                        )
                    }
                }
                if (r.movies.isNotEmpty()) item(key = "movies") {
                    PosterRow("Films", r.movies.map { Triple(it.streamId, it.name, it.posterUrl) }, onOpenMovie)
                }
                if (r.series.isNotEmpty()) item(key = "series") {
                    PosterRow("Series", r.series.map { Triple(it.seriesId, it.name, it.coverUrl) }, onOpenSeries)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PosterRow(title: String, items: List<Triple<Int, String, String?>>, onOpen: (Int) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            itemsIndexed(items, key = { _, it -> it.first }) { i, (id, name, image) ->
                PosterTile(name, image, onClick = { onOpen(id) }, onLongClick = {}, modifier = Modifier.lastInRow(i == items.lastIndex))
            }
        }
    }
}

private const val MAX_CHANNELS = 8
