package nl.jordy.iptv.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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

    /**
     * A category's films, one tile per film: language versions of the same title and year (the
     * provider lists NL-sub, NL-audio and Multi separately) collapse into the first one listed.
     * SQLite takes the other columns from the row with MIN(sortIndex).
     */
    @Query(
        "SELECT m.*, COUNT(*) AS versions, MIN(m.sortIndex) AS firstSort FROM movie m WHERE m.categoryId = :categoryId " +
            "GROUP BY lower(m.name), m.year ORDER BY firstSort",
    )
    fun movieTilesPaged(categoryId: String): PagingSource<Int, MovieTile>

    @Query(
        "SELECT m.*, COUNT(*) AS versions, MIN(m.sortIndex) AS firstSort FROM movie m JOIN movie_info i ON i.streamId = m.streamId " +
            "WHERE m.categoryId = :categoryId AND (i.genre LIKE :pattern OR i.genre LIKE :alt) GROUP BY lower(m.name), m.year ORDER BY firstSort",
    )
    fun movieTilesPagedByGenre(categoryId: String, pattern: String, alt: String): PagingSource<Int, MovieTile>

    /** Every version of a film in the whole catalog, with the country of a category it's in. */
    @Query(
        "SELECT m.*, c.country AS country FROM movie m LEFT JOIN category c ON c.type = 'MOVIE' AND c.id = m.categoryId " +
            "WHERE lower(m.name) = lower(:name) AND m.year IS :year GROUP BY m.streamId ORDER BY m.streamId",
    )
    suspend fun movieVersions(name: String, year: Int?): List<MovieVersionRow>
    @Query("SELECT * FROM movie WHERE streamId = :streamId LIMIT 1")
    suspend fun movie(streamId: Int): MovieEntity?

    /** A category's films with a genre (from [MovieInfoEntity]), in either of two spellings. */
    @Query(
        "SELECT m.* FROM movie m JOIN movie_info i ON i.streamId = m.streamId " +
            "WHERE m.categoryId = :categoryId AND (i.genre LIKE :pattern OR i.genre LIKE :alt) ORDER BY m.sortIndex",
    )
    fun moviesPagedByGenre(categoryId: String, pattern: String, alt: String): PagingSource<Int, MovieEntity>
    @Query("SELECT i.genre FROM movie m JOIN movie_info i ON i.streamId = m.streamId WHERE m.categoryId = :categoryId")
    fun movieGenres(categoryId: String): Flow<List<String?>>

    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY sortIndex")
    fun seriesPaged(categoryId: String): PagingSource<Int, SeriesEntity>
    /** A category's series with a genre, in either of two spellings (see [nl.jordy.iptv.data.repo.Genres]). */
    @Query("SELECT * FROM series WHERE categoryId = :categoryId AND (genre LIKE :pattern OR genre LIKE :alt) ORDER BY sortIndex")
    fun seriesPagedByGenre(categoryId: String, pattern: String, alt: String): PagingSource<Int, SeriesEntity>
    @Query("SELECT genre FROM series WHERE categoryId = :categoryId")
    suspend fun seriesGenres(categoryId: String): List<String?>
    @Query("SELECT * FROM series WHERE seriesId = :seriesId LIMIT 1")
    suspend fun series(seriesId: Int): SeriesEntity?

    @Query("SELECT * FROM category") suspend fun allCategories(): List<CategoryEntity>

    /** EPG ids of the channels in the given countries' categories (null where the provider has none). */
    @Query(
        "SELECT l.epgChannelId FROM live_channel l JOIN category c ON c.type = 'LIVE' AND c.id = l.categoryId " +
            "WHERE c.country IN (:countries) GROUP BY l.streamId",
    )
    suspend fun liveEpgIds(countries: List<String>): List<String?>

    /** "Recent toegevoegd" on Home: newest films in the given countries, each film once. */
    @Query(
        "SELECT m.* FROM movie m JOIN category c ON c.type = 'MOVIE' AND c.id = m.categoryId " +
            "WHERE c.country IN (:countries) AND (:hideAdult = 0 OR c.adult = 0) AND m.addedAt IS NOT NULL " +
            "GROUP BY lower(m.name), m.year ORDER BY m.addedAt DESC LIMIT :limit",
    )
    fun recentMovies(countries: List<String>, hideAdult: Boolean, limit: Int): Flow<List<MovieEntity>>

    @Query("SELECT id FROM category WHERE type = :type AND adult = 1")
    suspend fun adultCategoryIds(type: ContentType): List<String>

    /** Live streams in the given countries with what the guide needs: cleaned name and EPG id. */
    @Query(
        "SELECT l.streamId, l.name, l.epgChannelId, c.country FROM live_channel l " +
            "JOIN category c ON c.type = 'LIVE' AND c.id = l.categoryId WHERE c.country IN (:countries) GROUP BY l.streamId",
    )
    suspend fun liveForGuide(countries: List<String>): List<LiveGuideRow>

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
interface ProfileDao {
    @Insert suspend fun insert(item: ProfileEntity): Long
    @Update suspend fun update(item: ProfileEntity)
    @Query("DELETE FROM profile WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM profile ORDER BY createdAt, id") fun observeAll(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profile ORDER BY createdAt, id") suspend fun all(): List<ProfileEntity>
    @Query("SELECT * FROM profile WHERE id = :id") suspend fun get(id: Long): ProfileEntity?
    /** Makes [id] the most recently used; strictly newest even when two picks share a millisecond. */
    @Query("UPDATE profile SET lastUsedAt = MAX(:at, (SELECT MAX(lastUsedAt) FROM profile) + 1) WHERE id = :id")
    suspend fun touch(id: Long, at: Long)
}

/** Everything here is per profile: every query takes the profile it belongs to. */
@Dao
interface FavoriteDao {
    @Upsert suspend fun upsert(item: FavoriteEntity)
    @Query("DELETE FROM favorite WHERE profileId = :profileId AND type = :type AND itemId = :itemId")
    suspend fun delete(profileId: Long, type: ContentType, itemId: Int)
    @Query("SELECT EXISTS (SELECT 1 FROM favorite WHERE profileId = :profileId AND type = :type AND itemId = :itemId)")
    suspend fun isFavorite(profileId: Long, type: ContentType, itemId: Int): Boolean
    @Query("SELECT EXISTS (SELECT 1 FROM favorite WHERE profileId = :profileId AND type = :type AND itemId = :itemId)")
    fun observeIsFavorite(profileId: Long, type: ContentType, itemId: Int): Flow<Boolean>
    @Query("SELECT * FROM favorite WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun all(profileId: Long): Flow<List<FavoriteEntity>>
    @Query("SELECT itemId FROM favorite WHERE profileId = :profileId AND type = :type")
    fun ids(profileId: Long, type: ContentType): Flow<List<Int>>
}

@Dao
interface PlaybackDao {
    @Upsert suspend fun upsert(item: PlaybackPositionEntity)
    @Query("DELETE FROM playback_position WHERE profileId = :profileId AND type = :type AND itemId = :itemId")
    suspend fun delete(profileId: Long, type: ContentType, itemId: Int)
    @Query("SELECT * FROM playback_position WHERE profileId = :profileId AND type = :type AND itemId = :itemId")
    suspend fun get(profileId: Long, type: ContentType, itemId: Int): PlaybackPositionEntity?
    @Query("SELECT * FROM playback_position WHERE profileId = :profileId AND type = :type AND itemId = :itemId")
    fun observe(profileId: Long, type: ContentType, itemId: Int): Flow<PlaybackPositionEntity?>
    @Query("SELECT * FROM playback_position WHERE profileId = :profileId AND type = :type")
    fun observeAll(profileId: Long, type: ContentType): Flow<List<PlaybackPositionEntity>>
    /** "Verder kijken": finished items are already deleted by the resume policy. */
    @Query("SELECT * FROM playback_position WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT :limit")
    fun recent(profileId: Long, limit: Int): Flow<List<PlaybackPositionEntity>>
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

data class LiveGuideRow(val streamId: Int, val name: String, val epgChannelId: String?, val country: String?)

@Dao
interface EpgDao {
    @Insert suspend fun insertProgrammes(items: List<EpgProgrammeEntity>)
    @Insert suspend fun insertChannels(items: List<EpgChannelEntity>)
    @Upsert suspend fun upsertInfo(info: EpgInfoEntity)
    @Query("DELETE FROM epg_programme") suspend fun clearProgrammes()
    @Query("DELETE FROM epg_channel") suspend fun clearChannels()
    @Query("SELECT * FROM epg_info WHERE id = 0") suspend fun info(): EpgInfoEntity?
    @Query("SELECT * FROM epg_info WHERE id = 0") fun observeInfo(): Flow<EpgInfoEntity?>

    /** The programme on now (if any) and the ones after it, for one stream. */
    @Query(
        "SELECT p.* FROM epg_programme p JOIN epg_channel c ON c.channelId = p.channelId " +
            "WHERE c.streamId = :streamId AND p.stop > :now ORDER BY p.start LIMIT :limit",
    )
    suspend fun upcoming(streamId: Int, now: Long, limit: Int = 2): List<EpgProgrammeEntity>

}

@Dao
interface MovieInfoDao {
    @Upsert suspend fun upsert(item: MovieInfoEntity)

    /**
     * Films in the given countries without details yet (or whose last attempt failed before
     * [retryBefore]), in a stable order so a restart continues where it stopped.
     */
    @Query(
        "SELECT DISTINCT m.streamId FROM movie m JOIN category c ON c.type = 'MOVIE' AND c.id = m.categoryId " +
            "LEFT JOIN movie_info i ON i.streamId = m.streamId " +
            "WHERE c.country IN (:countries) AND (i.streamId IS NULL OR (i.failed = 1 AND i.fetchedAt < :retryBefore)) " +
            "ORDER BY m.streamId LIMIT :limit",
    )
    suspend fun pending(countries: List<String>, retryBefore: Long, limit: Int): List<Int>

    @Query("SELECT COUNT(DISTINCT m.streamId) FROM movie m JOIN category c ON c.type = 'MOVIE' AND c.id = m.categoryId WHERE c.country IN (:countries)")
    fun total(countries: List<String>): Flow<Int>

    @Query(
        "SELECT COUNT(DISTINCT m.streamId) FROM movie m JOIN category c ON c.type = 'MOVIE' AND c.id = m.categoryId " +
            "JOIN movie_info i ON i.streamId = m.streamId AND i.failed = 0 WHERE c.country IN (:countries)",
    )
    fun done(countries: List<String>): Flow<Int>
}

/** A film tile: the first version of a title in a category, and how many versions it stands for. */
data class MovieTile(@Embedded val movie: MovieEntity, val versions: Int, val firstSort: Int)

data class MovieVersionRow(@Embedded val movie: MovieEntity, val country: String?)
