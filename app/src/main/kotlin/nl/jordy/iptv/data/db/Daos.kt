package nl.jordy.iptv.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import nl.jordy.iptv.data.xtream.ContentType

@Dao
interface CatalogDao {
    @Upsert suspend fun upsertCategories(items: List<CategoryEntity>)
    @Upsert suspend fun upsertLive(items: List<LiveChannelEntity>)
    @Upsert suspend fun upsertMovies(items: List<MovieEntity>)
    @Upsert suspend fun upsertSeries(items: List<SeriesEntity>)

    @Query("DELETE FROM category WHERE type = :type AND generation < :generation")
    suspend fun deleteOldCategories(type: ContentType, generation: Long)
    @Query("DELETE FROM live_channel WHERE generation < :generation") suspend fun deleteOldLive(generation: Long)
    @Query("DELETE FROM movie WHERE generation < :generation") suspend fun deleteOldMovies(generation: Long)
    @Query("DELETE FROM series WHERE generation < :generation") suspend fun deleteOldSeries(generation: Long)

    // Only categories that actually contain something: providers ship many empty ones.
    @Query("SELECT * FROM category c WHERE c.type = 'LIVE' AND EXISTS (SELECT 1 FROM live_channel x WHERE x.categoryId = c.id) ORDER BY c.sortIndex")
    fun liveCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM category c WHERE c.type = 'MOVIE' AND EXISTS (SELECT 1 FROM movie x WHERE x.categoryId = c.id) ORDER BY c.sortIndex")
    fun movieCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM category c WHERE c.type = 'SERIES' AND EXISTS (SELECT 1 FROM series x WHERE x.categoryId = c.id) ORDER BY c.sortIndex")
    fun seriesCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM live_channel WHERE categoryId = :categoryId ORDER BY sortIndex")
    fun livePaged(categoryId: String): PagingSource<Int, LiveChannelEntity>
    /** Full ordered list of a category, for zapping up/down in the player. */
    @Query("SELECT * FROM live_channel WHERE categoryId = :categoryId ORDER BY sortIndex")
    suspend fun liveInCategory(categoryId: String): List<LiveChannelEntity>

    @Query("SELECT * FROM live_channel WHERE streamId IN (:ids) GROUP BY streamId")
    suspend fun liveByIds(ids: List<Int>): List<LiveChannelEntity>

    @Query("SELECT * FROM movie WHERE streamId IN (:ids) GROUP BY streamId")
    suspend fun moviesByIds(ids: List<Int>): List<MovieEntity>

    @Query("SELECT * FROM series WHERE seriesId IN (:ids) GROUP BY seriesId")
    suspend fun seriesByIds(ids: List<Int>): List<SeriesEntity>

    @Query("SELECT * FROM movie WHERE categoryId = :categoryId ORDER BY sortIndex")
    fun moviesPaged(categoryId: String): PagingSource<Int, MovieEntity>
    @Query("SELECT * FROM movie WHERE streamId = :streamId LIMIT 1")
    suspend fun movie(streamId: Int): MovieEntity?

    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY sortIndex")
    fun seriesPaged(categoryId: String): PagingSource<Int, SeriesEntity>
    @Query("SELECT * FROM series WHERE seriesId = :seriesId LIMIT 1")
    suspend fun series(seriesId: Int): SeriesEntity?

    @Query("SELECT * FROM category") suspend fun allCategories(): List<CategoryEntity>

    /** EPG ids of the channels in the given countries' categories (null where the provider has none). */
    @Query(
        "SELECT l.epgChannelId FROM live_channel l JOIN category c ON c.type = 'LIVE' AND c.id = l.categoryId " +
            "WHERE c.country IN (:countries) GROUP BY l.streamId",
    )
    suspend fun liveEpgIds(countries: List<String>): List<String?>

    @Query("SELECT COUNT(*) FROM live_channel") suspend fun liveCount(): Int
    @Query("SELECT COUNT(*) FROM movie") suspend fun movieCount(): Int
    @Query("SELECT COUNT(*) FROM series") suspend fun seriesCount(): Int
}

@Dao
interface EpisodeDao {
    @Upsert suspend fun upsert(items: List<EpisodeEntity>)
    @Query("DELETE FROM episode WHERE seriesId = :seriesId") suspend fun deleteForSeries(seriesId: Int)
    @Query("SELECT * FROM episode WHERE seriesId = :seriesId ORDER BY season, number")
    fun forSeries(seriesId: Int): Flow<List<EpisodeEntity>>
    @Query("SELECT * FROM episode WHERE episodeId = :episodeId") suspend fun episode(episodeId: Int): EpisodeEntity?
}

@Dao
interface FavoriteDao {
    @Upsert suspend fun upsert(item: FavoriteEntity)
    @Query("DELETE FROM favorite WHERE type = :type AND itemId = :itemId") suspend fun delete(type: ContentType, itemId: Int)
    @Query("SELECT EXISTS (SELECT 1 FROM favorite WHERE type = :type AND itemId = :itemId)")
    suspend fun isFavorite(type: ContentType, itemId: Int): Boolean
    @Query("SELECT EXISTS (SELECT 1 FROM favorite WHERE type = :type AND itemId = :itemId)")
    fun observeIsFavorite(type: ContentType, itemId: Int): Flow<Boolean>
    @Query("SELECT * FROM favorite ORDER BY addedAt DESC") fun all(): Flow<List<FavoriteEntity>>
    @Query("SELECT itemId FROM favorite WHERE type = :type") fun ids(type: ContentType): Flow<List<Int>>
}

@Dao
interface PlaybackDao {
    @Upsert suspend fun upsert(item: PlaybackPositionEntity)
    @Query("DELETE FROM playback_position WHERE type = :type AND itemId = :itemId") suspend fun delete(type: ContentType, itemId: Int)
    @Query("SELECT * FROM playback_position WHERE type = :type AND itemId = :itemId")
    suspend fun get(type: ContentType, itemId: Int): PlaybackPositionEntity?
    @Query("SELECT * FROM playback_position WHERE type = :type AND itemId = :itemId")
    fun observe(type: ContentType, itemId: Int): Flow<PlaybackPositionEntity?>
    @Query("SELECT * FROM playback_position WHERE type = :type")
    fun observeAll(type: ContentType): Flow<List<PlaybackPositionEntity>>
}

@Dao
interface SearchDao {
    @Insert suspend fun insert(items: List<SearchEntity>)
    @Query("DELETE FROM search WHERE type = :type") suspend fun deleteType(type: String)

    /**
     * [match] is an FTS4 MATCH expression, built by the search repository from the user's words.
     * Preferred countries are pulled forward before the limit, so a short query on a 100k-item
     * catalog doesn't cut off the Dutch results.
     */
    @Query("SELECT * FROM search WHERE search MATCH :match ORDER BY (country IN (:preferred)) DESC LIMIT :limit")
    suspend fun match(match: String, preferred: List<String>, limit: Int): List<SearchEntity>
}

@Dao
interface SyncInfoDao {
    @Upsert suspend fun upsert(item: SyncInfoEntity)
    @Query("SELECT * FROM sync_info WHERE id = 0") suspend fun get(): SyncInfoEntity?
}
