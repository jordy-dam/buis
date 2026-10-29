package nl.jordy.iptv.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.EpisodeEntity
import nl.jordy.iptv.data.db.FavoriteEntity
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
        when (val d = ResumePolicy.onProgress(positionMs, durationMs)) {
            is ResumePolicy.Decision.Save ->
                db.playback().upsert(PlaybackPositionEntity(profileId, type, id, d.positionMs, durationMs, System.currentTimeMillis()))
            ResumePolicy.Decision.Clear -> db.playback().delete(profileId, type, id)
        }
    }

    /** "Uit Verder kijken": forget where the active profile was; the next play starts at the beginning. */
    suspend fun forgetPosition(type: ContentType, id: Int) = db.playback().delete(profiles.currentId(), type, id)

    suspend fun currentProfileId(): Long = profiles.currentId()
}
