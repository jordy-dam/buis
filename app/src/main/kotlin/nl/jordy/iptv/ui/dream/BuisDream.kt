package nl.jordy.iptv.ui.dream

import android.service.dreams.DreamService
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.ui.theme.Background
import nl.jordy.iptv.ui.theme.IptvTheme
import nl.jordy.iptv.ui.theme.TextPrimary
import nl.jordy.iptv.ui.theme.TextSecondary
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BUIS as the tv's screensaver: posters from the active profile's Verder kijken and favourites,
 * three at a time, drifting slowly and changing every so often, with the time. Picked in the tv's
 * settings (or `adb shell settings put secure screensaver_components nl.jordy.iptv/.ui.dream.BuisDream`
 * where Google TV only offers Ambient mode). Nothing to press: any key wakes the tv as usual.
 */
@AndroidEntryPoint
class BuisDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    @Inject lateinit var posters: DreamPosters

    // A DreamService is no LifecycleOwner, but Compose needs one (and a saved-state owner) on its view.
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BuisDream)
            setViewTreeSavedStateRegistryOwner(this@BuisDream)
            setContent { IptvTheme { DreamScreen(posters::load) } }
        }
        setContentView(view)
        registry.currentState = Lifecycle.State.STARTED
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        registry.currentState = Lifecycle.State.STARTED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}

/** Poster URLs for the screensaver: what the profile is watching first, then its favourite films and series. */
@Singleton
class DreamPosters @Inject constructor(private val db: AppDatabase, private val profiles: ProfileRepository) {
    suspend fun load(): List<String> = runCatching {
        val profile = profiles.currentId()
        val catalog = db.catalog()
        val watching = db.playback().recent(profile, 20).first().mapNotNull { p ->
            when (p.type) {
                ContentType.MOVIE -> catalog.movie(p.itemId)?.posterUrl
                ContentType.SERIES -> db.episodes().episode(p.itemId)?.let { catalog.series(it.seriesId)?.coverUrl }
                ContentType.LIVE -> null
            }
        }
        val favourites = db.favorites().all(profile).first().filter { it.type != ContentType.LIVE }.mapNotNull { it.imageUrl }
        (watching + favourites).filter { it.isNotBlank() }.distinct()
    }.getOrDefault(emptyList())
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** The screensaver itself; Instellingen in debug builds shows it too, since emulators rarely start a dream. */
@Composable
internal fun DreamScreen(load: suspend () -> List<String>) {
    val posters by produceState(emptyList<String>()) { value = load() }
    // Every 12 seconds the next three posters; drift keeps the screen from burning in.
    val page by produceState(0, posters) {
        while (true) {
            delay(12_000)
            value++
        }
    }
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(10_000)
        }
    }
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = -24f, targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
    )
    Box(Modifier.fillMaxSize().background(Background)) {
        if (posters.isNotEmpty()) {
            val three = List(minOf(3, posters.size)) { i -> posters[(page * 3 + i) % posters.size] }
            Crossfade(three, animationSpec = tween(2_000), label = "posters", modifier = Modifier.align(Alignment.Center)) { shown ->
                Row(
                    Modifier.graphicsLayer { translationX = drift * density; translationY = -drift * density / 2 },
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    shown.forEach { url ->
                        AsyncImage(
                            model = url, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.height(300.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(48.dp), horizontalAlignment = Alignment.End) {
            Text(clock.format(now), style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            Text("BUIS", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        }
    }
}
