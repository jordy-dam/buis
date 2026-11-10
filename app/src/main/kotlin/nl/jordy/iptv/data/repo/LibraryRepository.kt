package nl.jordy.iptv.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.EpisodeEntity
import nl.jordy.iptv.data.db.FavoriteEntity
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.PlaybackPositionEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.MovieInfo
import nl.jordy.iptv.data.xtream.SeriesInfo
import nl.jordy.iptv.data.xtream.XtreamClient
import nl.jordy.iptv.data.xtream.XtreamParser
import nl.jordy.iptv.player.ResumePolicy
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Detail info, favourites and resume positions: everything the screens need beyond the synced catalog. */
@Singleton
class LibraryRepository @Inject constructor(
    private val client: XtreamClient,
    private val credentials: CredentialStore,
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
) {
    private val movieInfo = ConcurrentHashMap<Int, MovieInfo>()
    private val detached = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Null when the provider can't be reached; the detail screen then shows what the catalog has. */
    suspend fun movieInfo(streamId: Int): MovieInfo? {
        movieInfo[streamId]?.let { return it }
        val c = credentials.current() ?: return null
        return runCatching { XtreamParser.movieInfo(client.get(c, "get_vod_info", "vod_id" to "$streamId")) }
            .getOrNull()?.also {
                movieInfo[streamId] = it
                // An opened film counts for the genre filter too; no need to fetch it again later.
                db.movieInfo().upsert(it.toEntity(streamId, System.currentTimeMillis()))
            }
    }

    fun clearCache() = movieInfo.clear()

    /** Refreshes episodes from the provider into Room; returns null when offline (cached episodes stay). */
    suspend fun refreshSeries(seriesId: Int): SeriesInfo? {
        val c = credentials.current() ?: return null
        val info = runCatching { XtreamParser.seriesInfo(seriesId, client.get(c, "get_series_info", "series_id" to "$seriesId")) }
            .getOrNull() ?: return null
        db.withTransaction {
            db.episodes().deleteForSeries(seriesId)
            db.episodes().upsert(info.episodes.map {
                EpisodeEntity(it.episodeId, seriesId, it.season, it.number, it.title, it.containerExtension, it.plot, it.imageUrl, it.durationSecs)
            })
        }
        return info
    }

    fun episodes(seriesId: Int): Flow<List<EpisodeEntity>> = db.episodes().forSeries(seriesId)

    // Favourites and resume positions belong to the watching profile; the flows follow a switch.

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> perProfile(query: (Long) -> Flow<T>): Flow<T> = profiles.currentId.flatMapLatest(query)

    fun favorites(): Flow<List<FavoriteEntity>> = perProfile { db.favorites().all(it) }
    fun favoriteIds(type: ContentType): Flow<List<Int>> = perProfile { db.favorites().ids(it, type) }
    fun isFavorite(type: ContentType, id: Int): Flow<Boolean> = perProfile { db.favorites().observeIsFavorite(it, type, id) }

    /** Returns true when the item is now a favourite. */
    suspend fun toggleFavorite(type: ContentType, id: Int, name: String, imageUrl: String?): Boolean {
        val dao = db.favorites()
        val profile = profiles.currentId()
        return if (dao.isFavorite(profile, type, id)) {
            dao.delete(profile, type, id)
            false
        } else {
            dao.upsert(FavoriteEntity(profile, type, id, name, imageUrl, System.currentTimeMillis()))
            true
        }
    }

    // Resume positions (movies: MOVIE + streamId, episodes: SERIES + episodeId)

    fun position(type: ContentType, id: Int): Flow<PlaybackPositionEntity?> = perProfile { db.playback().observe(it, type, id) }
    fun positions(type: ContentType): Flow<List<PlaybackPositionEntity>> = perProfile { db.playback().observeAll(it, type) }
    fun recentPositions(limit: Int): Flow<List<PlaybackPositionEntity>> = perProfile { db.playback().recent(it, limit) }
    suspend fun savedPosition(type: ContentType, id: Int): PlaybackPositionEntity? = db.playback().get(profiles.currentId(), type, id)

    /**
     * Fire-and-forget save that outlives the player screen, for the final position on exit.
     * [profileId] is the profile that started playing, taken when playback began.
     */
    fun reportProgressDetached(profileId: Long, type: ContentType, id: Int, positionMs: Long, durationMs: Long) {
        detached.launch { reportProgress(profileId, type, id, positionMs, durationMs) }
    }

    suspend fun reportProgress(profileId: Long, type: ContentType, id: Int, positionMs: Long, durationMs: Long) {
        // 70% in counts as seen, credits or not: the film gets its check on the poster. Once; repeats are ignored.
        if (durationMs > 0 && positionMs >= durationMs * WATCHED_FRACTION) {
            if (type == ContentType.MOVIE) db.playback().markWatched(profileId, id, System.currentTimeMillis())
            else db.playback().markEpisodeWatched(profileId, id, System.currentTimeMillis())
        }
        when (val d = ResumePolicy.onProgress(positionMs, durationMs)) {
            is ResumePolicy.Decision.Save ->
                db.playback().upsert(PlaybackPositionEntity(profileId, type, id, d.positionMs, durationMs, System.currentTimeMillis()))
            ResumePolicy.Decision.Clear -> db.playback().delete(profileId, type, id)
        }
    }

    /**
     * What watched_film holds for the active profile, as one log line: the release build's database
     * can't be read over adb, its log can (`adb logcat -s BUIS-watched`).
     */
    suspend fun logWatched() {
        val id = profiles.currentId()
        val rows = db.playback().watchedNames(id)
        android.util.Log.i("BUIS-watched", "profile $id: ${rows.size} films: " + rows.joinToString(" | ") { "${it.name ?: "?"} [${it.filmKey}] ${java.time.Instant.ofEpochMilli(it.at)}" })
        val episodes = db.playback().watchedEpisodes(id).first()
        val complete = db.playback().completeSeries(id).first()
        android.util.Log.i("BUIS-watched", "profile $id: ${episodes.size} episodes, series complete: $complete")
    }

    /** The check button on a film: seen or not, for every language version ([streamIds]) at once. */
    suspend fun setFilmWatched(streamIds: List<Int>, watched: Boolean) {
        val id = profiles.currentId()
        if (watched) streamIds.firstOrNull()?.let { db.playback().markWatched(id, it, System.currentTimeMillis()) }
        else db.playback().unmarkWatched(id, streamIds)
    }

    /** Long OK on an episode tile. */
    suspend fun setEpisodeWatched(episodeId: Int, watched: Boolean) {
        val id = profiles.currentId()
        if (watched) db.playback().markEpisodeWatched(id, episodeId, System.currentTimeMillis())
        else db.playback().unmarkEpisodeWatched(id, episodeId)
    }

    /** The check button on a series: all its episodes seen, or none. */
    suspend fun setSeriesWatched(seriesId: Int, watched: Boolean) {
        val id = profiles.currentId()
        if (watched) db.playback().markSeriesWatched(id, seriesId, System.currentTimeMillis())
        else db.playback().unmarkSeriesWatched(id, seriesId)
    }

    /** The active profile's watched films, for the check on posters. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val watchedFilms: Flow<WatchedFilms> = profiles.currentId.flatMapLatest { id ->
        combine(db.playback().watched(id), db.playback().watchedEpisodes(id), db.playback().completeSeries(id)) { films, episodes, series ->
            WatchedFilms(films.map { it.streamId }.toSet(), films.map { it.filmKey }.toSet(), episodes.toSet(), series.toSet())
        }
    }

    /** "Uit Verder kijken": forget where the active profile was; the next play starts at the beginning. */
    suspend fun forgetPosition(type: ContentType, id: Int) = db.playback().delete(profiles.currentId(), type, id)

    suspend fun currentProfileId(): Long = profiles.currentId()
}

/** Share of a film played after which it counts as seen (Jordy, 2026-11-10). */
const val WATCHED_FRACTION = 0.70

/**
 * What the profile watched (70% or more): films by stream, or by film key so other versions get
 * the check too; episodes; and series with every known episode watched.
 */
data class WatchedFilms(
    val streamIds: Set<Int> = emptySet(),
    val keys: Set<String> = emptySet(),
    val episodes: Set<Int> = emptySet(),
    /** Series whose every known episode is watched. */
    val series: Set<Int> = emptySet(),
) {
    fun hasEpisode(episodeId: Int) = episodeId in episodes
    fun hasSeries(seriesId: Int) = seriesId in series
    fun has(streamId: Int, key: String? = null) = streamId in streamIds || (key != null && key in keys)
    fun has(movie: MovieEntity) = has(movie.streamId, movie.filmKey())
}

/** [nl.jordy.iptv.data.db.FILM_KEY] for a film row, as far as the row knows it (its own TMDB id, else title and year). */
fun MovieEntity.filmKey(): String = tmdbId?.toString() ?: "${name.lowercase()}|${year ?: ""}"
