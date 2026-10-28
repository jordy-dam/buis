package nl.jordy.iptv.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.db.FavoriteEntity
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.ui.PlayerRoute
import nl.jordy.iptv.ui.components.ChannelTile
import nl.jordy.iptv.ui.components.PosterTile
import nl.jordy.iptv.ui.components.lastInRow
import nl.jordy.iptv.ui.theme.TextSecondary
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(private val library: LibraryRepository) : ViewModel() {
    val favorites = library.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun remove(f: FavoriteEntity) = viewModelScope.launch { library.toggleFavorite(f.type, f.itemId, f.name, f.imageUrl) }
}

/** One global list for now (per profile in phase 2): channels, films and series as separate rows. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FavoritesScreen(
    onPlay: (PlayerRoute) -> Unit,
    onOpenMovie: (Int) -> Unit,
    onOpenSeries: (Int) -> Unit,
    vm: FavoritesViewModel = hiltViewModel(),
) {
    val all by vm.favorites.collectAsStateWithLifecycle()
    val favorites = all ?: return
    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item { Text("Favorieten", style = MaterialTheme.typography.headlineMedium) }
        if (favorites.isEmpty()) {
            item {
                Text(
                    "Nog geen favorieten. Houd OK ingedrukt (of druk op Menu) op een zender, film of serie om hem toe te voegen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            }
        }
        val channels = favorites.filter { it.type == ContentType.LIVE }
        if (channels.isNotEmpty()) {
            item { Text("Zenders", style = MaterialTheme.typography.titleLarge) }
            items(channels, key = { "live/${it.itemId}" }) { f ->
                ChannelTile(
                    name = f.name,
                    logoUrl = f.imageUrl,
                    nowPlaying = null,
                    favorite = true,
                    // Zapping from a favourite goes through the favourite channels.
                    onClick = { onPlay(PlayerRoute(PlayerRoute.Kind.LIVE, f.itemId, f.name, categoryId = PlayerRoute.FAVORITES_CATEGORY)) },
                    onLongClick = { vm.remove(f) },
                    modifier = Modifier.width(720.dp),
                )
            }
        }
        listOf(ContentType.MOVIE to "Films", ContentType.SERIES to "Series").forEach { (type, label) ->
            val items = favorites.filter { it.type == type }
            if (items.isNotEmpty()) {
                item(key = "row/$type") {
                    Text(label, style = MaterialTheme.typography.titleLarge)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.focusRestorer(),
                    ) {
                        items(items, key = { it.itemId }) { f ->
                            PosterTile(
                                title = f.name,
                                imageUrl = f.imageUrl,
                                favorite = true,
                                onClick = { if (type == ContentType.MOVIE) onOpenMovie(f.itemId) else onOpenSeries(f.itemId) },
                                onLongClick = { vm.remove(f) },
                                modifier = Modifier.lastInRow(f == items.last()),
                            )
                        }
                    }
                }
            }
        }
    }
}
