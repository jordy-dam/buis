package nl.jordy.iptv.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.jordy.iptv.BuildConfig
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.LiveChannelEntity
import nl.jordy.iptv.data.repo.EpgRepository
import nl.jordy.iptv.data.repo.MovieEnricher
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.EpgEntry
import nl.jordy.iptv.data.xtream.StreamUrls
import nl.jordy.iptv.data.xtream.XtreamCredentials
import nl.jordy.iptv.ui.PlayerRoute
import javax.inject.Inject

data class PlayerUiState(
    val title: String = "",
    val channelNumber: Int? = null,
    val now: EpgEntry? = null,
    val next: EpgEntry? = null,
    /** Live: the channel Left goes back to. */
    val previous: String? = null,
    val error: String? = null,
    val buffering: Boolean = true,
)

/**
 * One ExoPlayer for the whole player screen. Zapping swaps the media item on the same instance:
 * no new decoder pipeline, no new surface, which is most of what keeps a zap under the 2s budget.
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedState: SavedStateHandle,
    private val credentialStore: CredentialStore,
    private val db: AppDatabase,
    private val library: LibraryRepository,
    private val epg: EpgRepository,
    private val enricher: MovieEnricher,
    private val history: ChannelHistory,
) : ViewModel() {

    val route = savedState.toRoute<PlayerRoute>()
    val isLive = route.kind == PlayerRoute.Kind.LIVE

    private val _state = MutableStateFlow(PlayerUiState(title = route.title))
    val state = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context)
            // Platform decoders first; FFmpeg steps in for AC3/EAC3/DTS the device can't decode.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true),
    )
        .setLoadControl(
            DefaultLoadControl.Builder()
                // Start rendering after 1s of buffer instead of the default 2.5s: the biggest single zap-time win.
                .setBufferDurationsMs(15_000, 50_000, 1_000, 2_500)
                .build(),
        )
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(8_000)
                    .setReadTimeoutMs(15_000)
                    .setAllowCrossProtocolRedirects(true),
            ).setLoadErrorHandlingPolicy(FailFastLoadErrorPolicy()),
        )
        .build()

    private var credentials: XtreamCredentials? = null
    private var liveSources: LiveSources? = null
    private var currentSource: LiveSources.Source? = null
    private var channels: List<LiveChannelEntity> = emptyList()
    private var channelIndex = -1
    /** The category Up/Down zap through; changes when Left goes back to a channel from another one. */
    private var zapCategory = route.categoryId
    private var epgJob: Job? = null
    private var zapStartedAt = 0L
    /** Positions are only saved once playback really started at the intended position. */
    private var playbackStarted = false

    init {
        // Film details are fetched in the background; not while someone is watching.
        enricher.playing.update { it + 1 }
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _state.update { it.copy(buffering = state == Player.STATE_BUFFERING) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) playbackStarted = true
            }

            override fun onRenderedFirstFrame() {
                if (zapStartedAt > 0 && BuildConfig.DEBUG) {
                    Log.d("Zap", "first frame after ${SystemClock.elapsedRealtime() - zapStartedAt} ms (hls=${currentSource?.isHls})")
                }
                zapStartedAt = 0
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
        viewModelScope.launch { start() }
        if (!isLive) viewModelScope.launch { saveLoop() }
    }

    private suspend fun start() {
        val c = credentialStore.current() ?: return showError("Niet ingelogd.")
        val base = c.baseUrl ?: return showError("Ongeldige server.")
        credentials = c
        when (route.kind) {
            PlayerRoute.Kind.LIVE -> {
                liveSources = LiveSources(base, c)
                channels = loadZapList()
                channelIndex = channels.indexOfFirst { it.streamId == route.id }
                val channel = channels.getOrNull(channelIndex)
                playLive(route.id, channel?.name ?: route.title)
            }
            PlayerRoute.Kind.MOVIE -> playVod(StreamUrls.movie(base, c, route.id, route.containerExtension), ContentType.MOVIE)
            PlayerRoute.Kind.EPISODE -> playVod(StreamUrls.episode(base, c, route.id, route.containerExtension), ContentType.SERIES)
        }
    }

    private suspend fun loadZapList(): List<LiveChannelEntity> {
        val dao = db.catalog()
        return when (val cat = zapCategory) {
            null -> emptyList()
            PlayerRoute.FAVORITES_CATEGORY -> {
                val favs = library.favorites().first().filter { it.type == ContentType.LIVE }
                val byId = dao.liveByIds(favs.map { it.itemId }).associateBy { it.streamId }
                favs.mapNotNull { byId[it.itemId] }
            }
            else -> dao.liveInCategory(cat)
        }
    }

    // Live

    /** Up = next channel, down = previous, wrapping around the category. */
    fun zap(delta: Int) {
        if (channels.size < 2) return
        channelIndex = (channelIndex + delta).mod(channels.size)
        val ch = channels[channelIndex]
        playLive(ch.streamId, ch.name)
    }

    /** Left: back to the channel watched before this one, with its own zap list. Left again flips back. */
    fun previousChannel() {
        val prev = history.previous ?: return
        viewModelScope.launch {
            if (prev.categoryId != zapCategory) {
                zapCategory = prev.categoryId
                channels = loadZapList()
            }
            channelIndex = channels.indexOfFirst { it.streamId == prev.streamId }
            playLive(prev.streamId, prev.name)
        }
    }

    private fun playLive(streamId: Int, name: String) {
        val source = liveSources?.first(streamId) ?: return
        history.watched(ChannelHistory.Entry(streamId, name, zapCategory))
        _state.update {
            PlayerUiState(title = name, channelNumber = channelIndex.takeIf { it >= 0 }?.plus(1), previous = history.previous?.name)
        }
        zapStartedAt = SystemClock.elapsedRealtime()
        setSource(source)
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            // Let the stream start first; the EPG request must not compete with it during a zap.
            delay(400)
            val (now, next) = epg.nowAndNext(streamId)
            _state.update { it.copy(now = now, next = next) }
        }
    }

    private fun setSource(source: LiveSources.Source) {
        currentSource = source
        val item = MediaItem.Builder()
            .setUri(source.url)
            // A MIME hint skips content sniffing, which saves a round trip on every zap.
            .setMimeType(if (source.isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP2T)
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    private fun handleError(error: PlaybackException) {
        if (isLive) {
            val streamId = channels.getOrNull(channelIndex)?.streamId ?: route.id
            val failed = currentSource
            val next = failed?.let { liveSources?.fallback(streamId, it) }
            if (next != null) return setSource(next)
        }
        showError(
            when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "De server weigert deze stream. Mogelijk zijn al je verbindingen in gebruik."
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Geen verbinding met de server."
                else -> "Kan dit niet afspelen (${error.errorCodeName})."
            },
        )
    }

    private fun showError(message: String) = _state.update { it.copy(error = message, buffering = false) }

    fun retry() {
        _state.update { it.copy(error = null) }
        if (isLive) {
            val ch = channels.getOrNull(channelIndex)
            playLive(ch?.streamId ?: route.id, ch?.name ?: route.title)
        } else {
            player.prepare()
            player.play()
        }
    }

    // VOD

    /** Whose position this is: the profile that started playing, even if saving happens later. */
    private var profileId: Long? = null

    private suspend fun playVod(url: String, type: ContentType) {
        profileId = library.currentProfileId()
        val saved = if (route.fromStart) null else library.savedPosition(type, route.id)
        val startAt = ResumePolicy.startPosition(saved?.positionMs, saved?.durationMs ?: 0)
        player.setMediaItem(MediaItem.fromUri(url), startAt)
        player.prepare()
        player.playWhenReady = true
    }

    private val vodType get() = if (route.kind == PlayerRoute.Kind.MOVIE) ContentType.MOVIE else ContentType.SERIES

    private suspend fun saveLoop() {
        while (viewModelScope.isActive) {
            delay(10_000)
            val profile = profileId ?: continue
            if (playbackStarted && player.isPlaying) library.reportProgress(profile, vodType, route.id, player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }

    fun saveNow() {
        val profile = profileId ?: return
        if (!isLive && playbackStarted) {
            library.reportProgressDetached(profile, vodType, route.id, player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }

    fun seekBy(ms: Long) {
        val target = (player.currentPosition + ms).coerceAtLeast(0)
        player.seekTo(if (player.duration > 0) target.coerceAtMost(player.duration) else target)
    }

    fun togglePause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun onCleared() {
        saveNow()
        player.release()
        enricher.playing.update { it - 1 }
    }
}
