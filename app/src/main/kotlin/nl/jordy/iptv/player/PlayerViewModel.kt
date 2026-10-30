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
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import nl.jordy.iptv.data.settings.UserPrefs
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
import nl.jordy.iptv.data.db.EpisodeEntity
import nl.jordy.iptv.data.db.SeriesIntroEntity
import nl.jordy.iptv.data.db.LiveChannelEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.repo.EpgRepository
import nl.jordy.iptv.data.repo.MovieEnricher
import nl.jordy.iptv.data.repo.LibraryRepository
import nl.jordy.iptv.data.repo.ChannelHealthRepository
import nl.jordy.iptv.data.repo.StallDetector
import nl.jordy.iptv.data.repo.WatchCounter
import nl.jordy.iptv.data.repo.WatchItem
import nl.jordy.iptv.data.repo.WatchLog
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.EpgEntry
import nl.jordy.iptv.data.xtream.StreamUrls
import nl.jordy.iptv.data.xtream.XtreamClient
import nl.jordy.iptv.data.xtream.XtreamCredentials
import nl.jordy.iptv.data.xtream.XtreamParser
import java.time.ZoneId
import nl.jordy.iptv.ui.PlayerRoute
import javax.inject.Inject

data class PlayerUiState(
    val title: String = "",
    val channelNumber: Int? = null,
    /** Live: the channel's logo, the bar's button to the channel list. */
    val logoUrl: String? = null,
    val now: EpgEntry? = null,
    val next: EpgEntry? = null,
    /** Live: the channel Left goes back to. */
    val previous: String? = null,
    val error: String? = null,
    val buffering: Boolean = true,
    /** Episodes: the card for the next one, while it shows. */
    val nextUp: NextUpCard? = null,
    /** Sleep timer: seconds until playback stops, during the last minute. */
    val sleepSecondsLeft: Int? = null,
    /** Pauzemoment: seconds until playback goes on, while the break card shows. */
    val breakSecondsLeft: Int? = null,
    /** The sleep timer ran out: the screen closes the player. */
    val finished: Boolean = false,
    /** Live: the running programme can be watched from its start (the channel keeps an archive). */
    val canRestart: Boolean = false,
    /** Live: watching this programme from its start instead of live. */
    val catchup: EpgEntry? = null,
    /** Audio tracks the stream offers; [audioSelected] indexes into it. */
    val audio: List<TrackOption> = emptyList(),
    val audioSelected: Int? = null,
    /** Subtitle tracks the stream offers; [subtitleSelected] null = off. */
    val subtitles: List<TrackOption> = emptyList(),
    val subtitleSelected: Int? = null,
    val showClock: Boolean = true,
    /** Live: watching a little behind live after holding Left; OK goes back to live. */
    val replaying: Boolean = false,
    /** Live: why holding Left did nothing, for the info bar. */
    val replayHint: String? = null,
    /** Episodes: where "Intro overslaan" jumps to, while the card shows. */
    val skipIntroTo: Long? = null,
)

/** One audio or subtitle track: its name and where it sits in the player's track groups. */
data class TrackOption(val label: String, val group: Int, val track: Int)

/** Instant replay: how far one hold of Left goes back in live. */
const val REPLAY_MS = 10_000L

/** [secondsLeft] counts down to the next episode; null means it waits for OK ("Kijk je nog?"). */
data class NextUpCard(val code: String, val title: String, val secondsLeft: Int?)

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
    private val profiles: ProfileRepository,
    private val xtream: XtreamClient,
    private val watchLog: WatchLog,
    private val channelHealth: ChannelHealthRepository,
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
                // Keep the last minute: instant replay in live seeks back into it instead of refetching.
                .setBackBuffer(60_000, true)
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
    /** Live category id → country, for [TrackLabels.liveSubtitleLang]. */
    private var categoryCountries: Map<String, String?> = emptyMap()
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
            override fun onTracksChanged(tracks: Tracks) = readTracks(tracks)

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
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                tick()
            }
        }
    }

    private suspend fun start() {
        val c = credentialStore.current() ?: return showError("Niet ingelogd.")
        val base = c.baseUrl ?: return showError("Ongeldige server.")
        credentials = c
        val prefs = profiles.currentPrefs()
        sleepTimer = SleepTimer(prefs.sleepMinutes, SystemClock.elapsedRealtime())
        if (!isLive) breakTimer = BreakTimer(prefs.breakMinutes, prefs.breakLength)
        profilePrefs = prefs
        applyProfileTracks()
        _state.update { it.copy(showClock = prefs.showClock) }
        when (route.kind) {
            PlayerRoute.Kind.LIVE -> {
                liveSources = LiveSources(base, c)
                swaps = runCatching { channelHealth.swapMap() }.getOrDefault(emptyMap())
                categoryCountries = db.catalog().liveCategoryCountries().associate { it.id to it.country }
                channels = loadZapList()
                channelIndex = channels.indexOfFirst { it.streamId == route.id }
                val channel = channels.getOrNull(channelIndex)
                playLive(route.id, channel?.name ?: route.title)
            }
            PlayerRoute.Kind.MOVIE -> {
                watching(WatchItem(ContentType.MOVIE, route.id, route.title, containerExtension = route.containerExtension))
                playVod(StreamUrls.movie(base, c, route.id, route.containerExtension), ContentType.MOVIE)
            }
            PlayerRoute.Kind.EPISODE -> {
                loadSeries()
                val title = episodes.firstOrNull { it.episodeId == route.id }?.let { "$seriesName – ${it.title}" } ?: route.title
                watching(WatchItem(ContentType.SERIES, route.id, title, containerExtension = route.containerExtension))
                playVod(StreamUrls.episode(base, c, route.id, route.containerExtension), ContentType.SERIES)
            }
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

    /** The channels Up/Down zap through, for the player's channel list. */
    val zapChannels: List<LiveChannelEntity> get() = channels
    val currentChannelIndex: Int get() = channelIndex

    /** A channel picked in the player's channel list. */
    fun tune(index: Int) {
        val ch = channels.getOrNull(index) ?: return
        if (index == channelIndex) return
        channelIndex = index
        playLive(ch.streamId, ch.name)
    }

    suspend fun nowAndNext(streamId: Int) = epg.nowAndNext(streamId)

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
        // Zenderstatus: a channel the household replaced plays its steadier version; guide and catch-up stay the channel's own.
        val played = swaps[streamId] ?: streamId
        val source = liveSources?.first(played) ?: return
        liveStreamId = streamId
        playedStreamId = played
        healthStart(played)
        history.watched(ChannelHistory.Entry(streamId, name, zapCategory))
        watching(WatchItem(ContentType.LIVE, streamId, name, categoryId = zapCategory))
        _state.update {
            PlayerUiState(
                title = name, channelNumber = channelIndex.takeIf { it >= 0 }?.plus(1),
                logoUrl = channels.firstOrNull { c -> c.streamId == streamId }?.logoUrl,
                previous = history.previous?.name, showClock = it.showClock,
            )
        }
        zapStartedAt = SystemClock.elapsedRealtime()
        // Subtitles per channel: a track picked on the previous channel was for that one only.
        channels.firstOrNull { it.streamId == streamId }?.let { ch ->
            applyTracks(TrackLabels.liveSubtitleLang(categoryCountries[ch.categoryId], profilePrefs.subtitleLang))
        }
        setSource(source)
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            // Let the stream start first; the EPG request must not compete with it during a zap.
            delay(400)
            val (now, next) = epg.nowAndNext(streamId)
            val channel = channels.firstOrNull { it.streamId == streamId } ?: db.catalog().liveByIds(listOf(streamId)).firstOrNull()
            _state.update { it.copy(now = now, next = next, canRestart = Catchup.canRestart(channel, now, System.currentTimeMillis() / 1000)) }
            if (watchItem?.itemId == streamId) watchItem = watchItem?.copy(detail = now?.title)
            // "Vanaf begin" from a reminder: once, for the channel the player opened on.
            if (startFromBeginning && streamId == route.id) {
                startFromBeginning = false
                watchFromStart()
            }
        }
    }

    private var startFromBeginning = isLive && route.fromStart

    // Vanaf begin

    /** The live channel playing (or last played before going back in time). */
    private var liveStreamId = route.id
    /** The stream that actually plays: [liveStreamId], or its replacement from Zenderstatus. */
    private var playedStreamId = route.id
    private var swaps: Map<Int, Int> = emptyMap()
    /** The panel's time zone, asked once: timeshift URLs need the start time in it. */
    private var panelZone: ZoneId? = null

    /** OK on a programme with an archive: play it from its start, with normal seek controls. */
    fun watchFromStart() {
        val programme = _state.value.now?.takeIf { _state.value.canRestart } ?: return
        val c = credentials ?: return
        val base = c.baseUrl ?: return
        val streamId = liveStreamId
        viewModelScope.launch {
            val zone = panelZone
                ?: runCatching { XtreamParser.account(xtream.get(c)).timezone?.let(ZoneId::of) }.getOrNull()?.also { panelZone = it }
                ?: ZoneId.systemDefault()
            currentSource = null
            epgJob?.cancel()
            healthEnd()
            _state.update { it.copy(catchup = programme, buffering = true, error = null) }
            val url = StreamUrls.timeshift(base, c, streamId, programme.start, Catchup.minutes(programme), zone)
            player.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP2T).build())
            player.prepare()
            player.playWhenReady = true
        }
    }

    /** Back while watching from the start: the channel live again. */
    fun backToLive() {
        val name = _state.value.title
        playLive(liveStreamId, name)
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
        if (isLive && _state.value.catchup != null) {
            return showError("Terugkijken lukt niet voor dit programma. Druk op Terug voor live.")
        }
        if (isLive) {
            val failed = currentSource
            val next = failed?.let { liveSources?.fallback(playedStreamId, it) }
            if (next != null) return setSource(next)
            healthFailed = true
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

    /** What's playing now: the route's film or episode, then each next episode in turn. */
    private var vodId = route.id

    private suspend fun playVod(url: String, type: ContentType, fromStart: Boolean = route.fromStart) {
        profileId = library.currentProfileId()
        val saved = if (fromStart) null else library.savedPosition(type, vodId)
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
            if (playbackStarted && player.isPlaying) library.reportProgress(profile, vodType, vodId, player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }

    fun saveNow() {
        logWatched()
        val profile = profileId ?: return
        if (!isLive && playbackStarted) {
            library.reportProgressDetached(profile, vodType, vodId, player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }

    // Next episode, sleep timer and Pauzemoment

    private var episodes: List<EpisodeEntity> = emptyList()
    private var seriesName = ""
    private var nextEpisode: EpisodeEntity? = null
    /** Episodes that ended and moved on without a key press in between. */
    private var unattended = 0
    private var cardShownAt: Long? = null
    private var asking = false
    /** Back on the card: this episode just ends. */
    private var dismissedFor: Int? = null
    private var sleepTimer: SleepTimer? = null
    private var breakTimer: BreakTimer? = null
    private var breakStartedAt: Long? = null
    /** The break took the place of the next-episode card: when it ends, the next episode starts. */
    private var breakThenNext = false

    private suspend fun loadSeries() {
        val ep = db.episodes().episode(route.id) ?: return
        episodes = db.episodes().forSeries(ep.seriesId).first()
        // Next episodes get the "<series> – <episode>" title the detail screen gives the first one. The name
        // comes from the catalog: the route's title differs per entry point ("<series> · S1 A3" from Home).
        seriesName = db.catalog().series(ep.seriesId)?.name ?: route.title.substringBefore(" – ").substringBefore(" · ")
        nextEpisode = Autoplay.next(episodes, route.id)
        seriesId = ep.seriesId
        introEnd = runCatching { db.seriesIntro().end(ep.seriesId) }.getOrNull()
    }

    /** Any key on the remote: someone is watching. */
    fun userActive() {
        sleepTimer?.activity(SystemClock.elapsedRealtime())
        unattended = 0
        if (_state.value.sleepSecondsLeft != null) _state.update { it.copy(sleepSecondsLeft = null) }
    }

    fun playNextNow() {
        nextEpisode?.let(::startEpisode)
    }

    fun dismissNextUp() {
        dismissedFor = vodId
        _state.update { it.copy(nextUp = null) }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        when (val s = sleepTimer?.state(now)) {
            is SleepTimer.State.Warning -> _state.update { it.copy(sleepSecondsLeft = s.secondsLeft) }
            SleepTimer.State.Expired -> {
                sleepTimer = null
                saveNow()
                player.pause()
                _state.update { it.copy(sleepSecondsLeft = null, finished = true) }
                return
            }
            else -> Unit
        }
        breakTimer?.tick(now, player.isPlaying)
        watchCounter.tick(now, player.isPlaying)
        if (isLive && _state.value.catchup == null) {
            healthCounter.tick(now, player.isPlaying)
            stall.tick(now, player.isPlaying, player.playbackState == Player.STATE_BUFFERING)
        }
        if (breakTick(now)) return
        if (route.kind == PlayerRoute.Kind.EPISODE) {
            introTick(now)
            nextUpTick(now)
        }
    }

    /** True while the break card shows, so the next-episode card waits. */
    private fun breakTick(now: Long): Boolean {
        breakStartedAt?.let { startedAt ->
            val left = (breakTimer?.lengthSecs ?: 60) - ((now - startedAt) / 1000).toInt()
            if (left > 0) _state.update { it.copy(breakSecondsLeft = left) } else endBreak()
            return true
        }
        val timer = breakTimer ?: return false
        if (!playbackStarted) return false
        val remaining = player.duration.takeIf { it > 0 }?.let { it - player.currentPosition }
        val hasNext = route.kind == PlayerRoute.Kind.EPISODE && nextEpisode != null && dismissedFor != vodId
        when (timer.placement(remaining, hasNext)) {
            BreakTimer.Placement.NOW -> startBreak(now, thenNext = false)
            BreakTimer.Placement.SKIP -> timer.taken()
            // The next-episode card takes it, see nextUpTick.
            BreakTimer.Placement.AT_END, BreakTimer.Placement.NONE -> return false
        }
        return breakStartedAt != null
    }

    private fun startBreak(now: Long, thenNext: Boolean) {
        breakTimer?.taken()
        breakStartedAt = now
        breakThenNext = thenNext
        player.pause()
        _state.update { it.copy(nextUp = null, breakSecondsLeft = breakTimer?.lengthSecs ?: 60) }
    }

    /** OK or Back on the break card, or the minute is over. */
    fun endBreak() {
        if (breakStartedAt == null) return
        breakStartedAt = null
        _state.update { it.copy(breakSecondsLeft = null) }
        if (breakThenNext) nextEpisode?.let(::startEpisode) else player.play()
    }

    /** The app went to the background during the break: stop it there, nothing resumes by itself. */
    fun cancelBreak() {
        breakStartedAt = null
        _state.update { it.copy(breakSecondsLeft = null) }
    }

    private fun nextUpTick(now: Long) {
        val next = nextEpisode ?: return
        if (dismissedFor == vodId || !playbackStarted) return
        val shownAt = cardShownAt ?: run {
            val ended = player.playbackState == Player.STATE_ENDED
            if (!Autoplay.cardDue(player.currentPosition, player.duration.coerceAtLeast(0), ended)) return
            asking = Autoplay.asksFirst(unattended + 1)
            // "Kijk je nog?" waits for a key, so it is a break of its own.
            if (asking) breakTimer?.taken()
            cardShownAt = now
            now
        }
        if (!asking && breakTimer?.due == true) {
            unattended++
            startBreak(now, thenNext = true)
            return
        }
        if (asking) {
            _state.update { it.copy(nextUp = NextUpCard(Autoplay.code(next), next.title, null)) }
            return
        }
        val left = Autoplay.COUNTDOWN_SECS - ((now - shownAt) / 1000).toInt()
        if (left > 0) {
            _state.update { it.copy(nextUp = NextUpCard(Autoplay.code(next), next.title, left)) }
        } else {
            unattended++
            startEpisode(next)
        }
    }

    /** Same player, new media item: the next episode starts without leaving the screen. */
    private fun startEpisode(e: EpisodeEntity) {
        val c = credentials ?: return
        val base = c.baseUrl ?: return
        saveNow()
        watching(WatchItem(ContentType.SERIES, e.episodeId, "$seriesName – ${e.title}", containerExtension = e.containerExtension))
        vodId = e.episodeId
        playbackStarted = false
        introLearner = IntroLearner()
        introDone = false
        cardShownAt = null
        asking = false
        nextEpisode = Autoplay.next(episodes, e.episodeId)
        _state.update { PlayerUiState(title = "$seriesName – ${e.title}", showClock = it.showClock) }
        // A track picked in the player was for that episode; the next one starts from the profile's languages.
        applyProfileTracks()
        viewModelScope.launch { playVod(StreamUrls.episode(base, c, e.episodeId, e.containerExtension), ContentType.SERIES, fromStart = false) }
    }

    fun seekBy(ms: Long) {
        val from = player.currentPosition
        val target = (from + ms).coerceAtLeast(0)
        val to = if (player.duration > 0) target.coerceAtMost(player.duration) else target
        if (route.kind == PlayerRoute.Kind.EPISODE) introLearner.seeked(from, to)
        player.seekTo(to)
    }

    // Instant replay (live)

    /** Holding Left in live: 10 seconds back, as far as the stream lets you seek. */
    fun replay() {
        if (!player.isCurrentMediaItemSeekable) {
            _state.update { it.copy(replayHint = "Terugspoelen kan niet op deze zender") }
            return
        }
        player.seekTo((player.currentPosition - REPLAY_MS).coerceAtLeast(0))
        _state.update { it.copy(replaying = true, replayHint = null) }
    }

    fun goLive() {
        player.seekToDefaultPosition()
        _state.update { it.copy(replaying = false) }
    }

    // Intro overslaan

    private var seriesId: Int? = null
    private var introEnd: Long? = null
    private var introLearner = IntroLearner()
    /** Back on the card, or the jump was made: not offered again in this episode. */
    private var introDone = false

    private fun introTick(now: Long) {
        introLearner.tick(now, player.isPlaying)?.let { end ->
            introEnd = end
            introDone = true
            val id = seriesId ?: return@let
            viewModelScope.launch { runCatching { db.seriesIntro().upsert(SeriesIntroEntity(id, end, System.currentTimeMillis())) } }
        }
        val offer = !introDone && playbackStarted && IntroLearner.offer(introEnd, player.currentPosition)
        val to = if (offer) introEnd else null
        if (_state.value.skipIntroTo != to) _state.update { it.copy(skipIntroTo = to) }
    }

    /** OK on the card. */
    fun skipIntro() {
        val to = introEnd ?: return
        introDone = true
        player.seekTo(to)
        _state.update { it.copy(skipIntroTo = null) }
    }

    fun dismissIntro() {
        introDone = true
        _state.update { it.copy(skipIntroTo = null) }
    }

    // Kijkgeschiedenis

    private val watchCounter = WatchCounter()
    private var watchItem: WatchItem? = null
    private var watchStartedAt = System.currentTimeMillis()

    /** Something else is on screen now: what was on before goes into the history first. */
    private fun watching(item: WatchItem) {
        logWatched()
        watchItem = item
    }

    /** Writes what played since the last write (a minute or more), and keeps counting from now. */
    private fun logWatched() {
        val played = watchCounter.take()
        watchItem?.let { watchLog.record(it, watchStartedAt, played) }
        watchStartedAt = System.currentTimeMillis()
    }

    // Zenderstatus

    private var healthId: Int? = null
    private var healthCounter = WatchCounter()
    private var stall = StallDetector()
    private var healthFailed = false

    private fun healthStart(streamId: Int) {
        healthEnd()
        healthId = streamId
    }

    /** One live session ends (zap, catch-up, leaving): how it went goes into the channel's record. */
    private fun healthEnd() {
        healthId?.let { channelHealth.record(it, healthCounter.take(), stall.stalls, healthFailed) }
        healthId = null
        healthCounter = WatchCounter()
        stall = StallDetector()
        healthFailed = false
    }

    // Audio, subtitles and the clock

    private var profilePrefs = UserPrefs()

    /** The profile's languages: the player picks them when the stream has them; otherwise its default. */
    private fun applyProfileTracks() = applyTracks(profilePrefs.subtitleLang)

    /**
     * [subtitleLang] empty = off. Only a track in that language shows: a stream's "default" or
     * "forced" flag must not switch on Polish subtitles when Dutch was asked for.
     */
    private fun applyTracks(subtitleLang: String) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setPreferredAudioLanguage(profilePrefs.audioLang.ifEmpty { null })
            .setPreferredTextLanguage(subtitleLang.ifEmpty { null })
            .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
            .setSelectUndeterminedTextLanguage(false)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleLang.isEmpty())
            .build()
    }

    private fun readTracks(tracks: Tracks) {
        val audio = mutableListOf<TrackOption>()
        val subs = mutableListOf<TrackOption>()
        var audioSelected: Int? = null
        var subSelected: Int? = null
        tracks.groups.forEachIndexed { g, group ->
            for (t in 0 until group.length) {
                if (!group.isTrackSupported(t)) continue
                val f = group.getTrackFormat(t)
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        if (group.isTrackSelected(t)) audioSelected = audio.size
                        audio += TrackOption(TrackLabels.audio(f.language, f.label, f.channelCount, audio.size), g, t)
                    }
                    C.TRACK_TYPE_TEXT -> {
                        if (group.isTrackSelected(t)) subSelected = subs.size
                        val forced = f.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                        subs += TrackOption(TrackLabels.subtitle(f.language, f.label, forced, subs.size), g, t)
                    }
                }
            }
        }
        _state.update { it.copy(audio = audio, audioSelected = audioSelected, subtitles = subs, subtitleSelected = subSelected) }
    }

    private fun override(option: TrackOption): TrackSelectionOverride? =
        player.currentTracks.groups.getOrNull(option.group)?.let { TrackSelectionOverride(it.mediaTrackGroup, option.track) }

    /** An audio track picked in the player bar, for this title only. */
    fun selectAudio(index: Int) {
        val o = _state.value.audio.getOrNull(index)?.let(::override) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(o).build()
    }

    /** A subtitle track picked in the player bar, or null for off; for this title only. */
    fun selectSubtitle(index: Int?) {
        val builder = player.trackSelectionParameters.buildUpon()
        val option = index?.let { _state.value.subtitles.getOrNull(it) }
        if (option == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val o = override(option) ?: return
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(o)
        }
        player.trackSelectionParameters = builder.build()
    }

    fun toggleClock() {
        val on = !_state.value.showClock
        _state.update { it.copy(showClock = on) }
        viewModelScope.launch { profiles.setShowClock(on) }
    }

    fun togglePause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun onCleared() {
        healthEnd()
        saveNow()
        player.release()
        enricher.playing.update { it - 1 }
    }
}
