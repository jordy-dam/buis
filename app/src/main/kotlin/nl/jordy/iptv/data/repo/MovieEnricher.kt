package nl.jordy.iptv.data.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.MovieInfoEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.xtream.MovieInfo
import nl.jordy.iptv.data.xtream.XtreamClient
import nl.jordy.iptv.data.xtream.XtreamCredentials
import nl.jordy.iptv.data.xtream.XtreamParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches `get_vod_info` for every film in the household's countries, slowly, so the Films grid
 * can filter by genre (and phase 3 has cast and genre per title). The provider has no genre in
 * its film list, only per film: about 20k requests on the real provider, so [INTERVAL_MS] apart
 * (about 1.5 h the first time, then only new films), paused while something plays.
 */
@Singleton
class MovieEnricher @Inject constructor(
    private val client: XtreamClient,
    private val credentials: CredentialStore,
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Number of players open; the enricher waits while it's above zero. */
    val playing = MutableStateFlow(0)

    /** Starts (or resumes) in the background unless already running. */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runCatching { run() } }
    }

    private suspend fun run(): Int = credentials.current()?.let { run(it) } ?: 0

    /** Works through the pending films; returns how many it fetched. Public for tests. */
    suspend fun run(c: XtreamCredentials, intervalMs: Long = INTERVAL_MS, now: () -> Long = System::currentTimeMillis): Int {
        var fetched = 0
        while (true) {
            val countries = countries()
            val batch = db.movieInfo().pending(countries, retryBefore = now() - RETRY_AFTER_MS, limit = BATCH)
            if (batch.isEmpty()) return fetched
            for (id in batch) {
                playing.first { it == 0 }
                val info = runCatching { XtreamParser.movieInfo(client.get(c, "get_vod_info", "vod_id" to "$id")) }.getOrNull()
                db.movieInfo().upsert(info?.toEntity(id, now()) ?: MovieInfoEntity(id, null, null, null, null, null, now(), failed = true))
                fetched++
                delay(intervalMs)
            }
        }
    }

    /** "1.234 van 19.826 films", for Instellingen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val progress: Flow<Pair<Int, Int>> = profiles.profiles.map { all -> all.flatMap { it.preferredCountries.split(',') }.filter { it.isNotBlank() }.distinct() }
        .flatMapLatest { c -> if (c.isEmpty()) flowOf(0 to 0) else combine(db.movieInfo().done(c), db.movieInfo().total(c)) { d, t -> d to t } }

    private suspend fun countries() = profiles.all().flatMap { it.preferredCountries.split(',') }.filter { it.isNotBlank() }.distinct()

    companion object {
        const val INTERVAL_MS = 250L
        private const val BATCH = 50
        private const val RETRY_AFTER_MS = 24 * 60 * 60 * 1000L
    }
}

fun MovieInfo.toEntity(streamId: Int, at: Long) = MovieInfoEntity(streamId, genre, cast, director, plot, rating, at)
