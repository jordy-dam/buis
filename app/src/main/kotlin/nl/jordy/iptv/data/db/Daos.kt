package nl.jordy.iptv.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import nl.jordy.iptv.data.xtream.ContentType

@Dao
interface CatalogDao {
    @Upsert suspend fun upsertCategories(items: List<CategoryEntity>)
    // REPLACE, not @Upsert: on a filled table @Upsert throws and catches a constraint error per row (50 s of a
    // 109 s daily sync on the tv). Nothing references these rows by foreign key, so delete-and-insert is safe.
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLive(items: List<LiveChannelEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMovies(items: List<MovieEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSeries(items: List<SeriesEntity>)

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

    /** Each live category's country, for the player's subtitle rule. */
    @Query("SELECT id, country FROM category WHERE type = 'LIVE'")
    suspend fun liveCategoryCountries(): List<CategoryCountry>

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

    /** Zoeken op mensen: films whose director or cast (from movie_info) holds [pattern]; preferred countries first, best rated first. */
    @Query(
        "SELECT m.*, i.`cast` AS people, i.director AS director, c.adult AS adult FROM movie m JOIN movie_info i ON i.streamId = m.streamId " +
            "LEFT JOIN category c ON c.type = 'MOVIE' AND c.id = m.categoryId " +
            "WHERE i.director LIKE :pattern OR i.`cast` LIKE :pattern " +
            "ORDER BY IFNULL(c.country IN (:countries), 0) DESC, m.rating IS NULL, m.rating DESC LIMIT 2000",
    )
    suspend fun moviesByPerson(pattern: String, countries: List<String>): List<MoviePeopleRow>

    /** Zoeken op periode: films from [from] to [to]; preferred countries first, best rated first. */
    @Query(
        "SELECT m.* FROM movie m LEFT JOIN category c ON c.type = 'MOVIE' AND c.id = m.categoryId " +
            "WHERE m.year BETWEEN :from AND :to AND (:hideAdult = 0 OR IFNULL(c.adult, 0) = 0) " +
            "ORDER BY IFNULL(c.country IN (:countries), 0) DESC, m.rating IS NULL, m.rating DESC LIMIT 300",
    )
    suspend fun moviesInYears(from: Int, to: Int, countries: List<String>, hideAdult: Boolean): List<MovieEntity>

    @Query(
        "SELECT s.* FROM series s LEFT JOIN category c ON c.type = 'SERIES' AND c.id = s.categoryId " +
            "WHERE s.year BETWEEN :from AND :to AND (:hideAdult = 0 OR IFNULL(c.adult, 0) = 0) " +
            "ORDER BY IFNULL(c.country IN (:countries), 0) DESC, s.rating IS NULL, s.rating DESC LIMIT 300",
    )
    suspend fun seriesInYears(from: Int, to: Int, countries: List<String>, hideAdult: Boolean): List<SeriesEntity>

    @Query("SELECT * FROM movie WHERE categoryId = :categoryId ORDER BY sortIndex")
    fun moviesPaged(categoryId: String): PagingSource<Int, MovieEntity>

    /**
     * A category's films, one tile per film: language versions of the same title and year (the
     * provider lists NL-sub, NL-audio and Multi separately) collapse into the first one listed.
     * SQLite takes the other columns from the row with MIN(sortIndex).
     */
    @Query(
        "SELECT m.*, COUNT(*) AS versions, MIN(m.sortIndex) AS firstSort FROM movie m LEFT JOIN movie_info i ON i.streamId = m.streamId " +
            "WHERE m.categoryId = :categoryId GROUP BY $FILM_KEY ORDER BY firstSort",
    )
    fun movieTilesPaged(categoryId: String): PagingSource<Int, MovieTile>

    @Query(
        "SELECT m.*, COUNT(*) AS versions, MIN(m.sortIndex) AS firstSort FROM movie m JOIN movie_info i ON i.streamId = m.streamId " +
            "WHERE m.categoryId = :categoryId AND (i.genre LIKE :pattern OR i.genre LIKE :alt) GROUP BY $FILM_KEY ORDER BY firstSort",
    )
    fun movieTilesPagedByGenre(categoryId: String, pattern: String, alt: String): PagingSource<Int, MovieTile>

    /**
     * Films filtered at once by genre or mood ([anyGenre]: any of up to six LIKE patterns, see
     * [nl.jordy.iptv.data.repo.FilmFilters]) and by running time ([maxSecs], 0 = any length).
     * A film without details doesn't pass a filter it can't be judged on.
     */
    @Query(
        "SELECT m.*, COUNT(*) AS versions, MIN(m.sortIndex) AS firstSort FROM movie m LEFT JOIN movie_info i ON i.streamId = m.streamId " +
            "WHERE m.categoryId = :categoryId " +
            "AND (:anyGenre = 0 OR i.genre LIKE :p1 OR i.genre LIKE :p2 OR i.genre LIKE :p3 OR i.genre LIKE :p4 OR i.genre LIKE :p5 OR i.genre LIKE :p6) " +
            "AND (:maxSecs = 0 OR (i.durationSecs > 0 AND i.durationSecs <= :maxSecs)) " +
            "GROUP BY $FILM_KEY ORDER BY firstSort",
    )
    fun movieTilesFiltered(
        categoryId: String, anyGenre: Boolean,
        p1: String, p2: String, p3: String, p4: String, p5: String, p6: String,
        maxSecs: Int,
    ): PagingSource<Int, MovieTile>

    /**
     * Films › Net toegevoegd: every film in the given countries, newest first, each film once, with the same
     * genre/mood and length filters as a category. (Was "Recent toegevoegd" on Home.)
     */
    @Query(
        "SELECT m.*, COUNT(*) AS versions, MIN(m.sortIndex) AS firstSort FROM movie m JOIN category c ON c.type = 'MOVIE' AND c.id = m.categoryId " +
            "LEFT JOIN movie_info i ON i.streamId = m.streamId " +
            "WHERE c.country IN (:countries) AND (:hideAdult = 0 OR c.adult = 0) AND m.addedAt IS NOT NULL " +
            "AND (:anyGenre = 0 OR i.genre LIKE :p1 OR i.genre LIKE :p2 OR i.genre LIKE :p3 OR i.genre LIKE :p4 OR i.genre LIKE :p5 OR i.genre LIKE :p6) " +
            "AND (:maxSecs = 0 OR (i.durationSecs > 0 AND i.durationSecs <= :maxSecs)) " +
            "GROUP BY $FILM_KEY ORDER BY MAX(m.addedAt) DESC",
    )
    fun newMovieTiles(
        countries: List<String>, hideAdult: Boolean, anyGenre: Boolean,
        p1: String, p2: String, p3: String, p4: String, p5: String, p6: String,
        maxSecs: Int,
    ): PagingSource<Int, MovieTile>

    /** Genres of the newest films, for the chips over Films › Net toegevoegd. */
    @Query(
        "SELECT i.genre FROM movie m JOIN category c ON c.type = 'MOVIE' AND c.id = m.categoryId JOIN movie_info i ON i.streamId = m.streamId " +
            "WHERE c.country IN (:countries) AND (:hideAdult = 0 OR c.adult = 0) AND m.addedAt IS NOT NULL ORDER BY m.addedAt DESC LIMIT 300",
    )
    fun newMovieGenres(countries: List<String>, hideAdult: Boolean): Flow<List<String?>>

    /**
     * Every version of a film in the whole catalog: the same TMDB id (from the list or the film's
     * details), or, without one, the same title and year.
     */
    @Query(
        "SELECT m.*, c.country AS country FROM movie m LEFT JOIN category c ON c.type = 'MOVIE' AND c.id = m.categoryId " +
            "LEFT JOIN movie_info i ON i.streamId = m.streamId " +
            "WHERE (:tmdbId IS NOT NULL AND (m.tmdbId = :tmdbId OR i.tmdbId = :tmdbId)) OR (lower(m.name) = lower(:name) AND m.year IS :year) " +
            "GROUP BY m.streamId ORDER BY m.streamId",
    )
    suspend fun movieVersions(name: String, year: Int?, tmdbId: Int?): List<MovieVersionRow>
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
            "GROUP BY COALESCE(CAST(m.tmdbId AS TEXT), lower(m.name) || '|' || IFNULL(m.year, '')) ORDER BY m.addedAt DESC LIMIT :limit",
    )
    fun recentMovies(countries: List<String>, hideAdult: Boolean, limit: Int): Flow<List<MovieEntity>>

    @Query("SELECT id FROM category WHERE type = :type AND adult = 1")
    suspend fun adultCategoryIds(type: ContentType): List<String>

    /**
     * Live streams the guide covers, with what it needs (cleaned name, EPG id): those in the given
     * countries, plus pinned categories and favourite channels, whatever their country ("Ziggo Kabel",
     * "Canal+" and sports packs often carry no country tag).
     */
    @Query(
        "SELECT l.streamId, l.name, l.epgChannelId, c.country FROM live_channel l " +
            "JOIN category c ON c.type = 'LIVE' AND c.id = l.categoryId " +
            "WHERE c.country IN (:countries) OR l.categoryId IN (:categoryIds) OR l.streamId IN (:streamIds) GROUP BY l.streamId",
    )
    suspend fun liveForGuide(countries: List<String>, categoryIds: List<String> = emptyList(), streamIds: List<Int> = emptyList()): List<LiveGuideRow>

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

/** A profile's guides and their channels, see [GuideEntity]. */
@Dao
interface GuideDao {
    @Insert suspend fun insert(guide: GuideEntity): Long
    @Query("UPDATE guide SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
    @Query("DELETE FROM guide WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("SELECT * FROM guide WHERE profileId = :profileId ORDER BY position, id")
    fun guides(profileId: Long): Flow<List<GuideEntity>>
    @Query("SELECT * FROM guide WHERE profileId = :profileId ORDER BY position, id")
    suspend fun guidesNow(profileId: Long): List<GuideEntity>

    @Upsert suspend fun upsertChannels(channels: List<GuideChannelEntity>)
    @Query("DELETE FROM guide_channel WHERE guideId = :guideId AND streamId = :streamId")
    suspend fun removeChannel(guideId: Long, streamId: Int)
    @Query("SELECT * FROM guide_channel WHERE guideId = :guideId ORDER BY position, name")
    suspend fun channels(guideId: Long): List<GuideChannelEntity>
    /** Every channel in the profile's guides, each guide in its own order. */
    @Query(
        "SELECT c.* FROM guide_channel c JOIN guide g ON g.id = c.guideId WHERE g.profileId = :profileId " +
            "ORDER BY g.position, g.id, c.position, c.name",
    )
    fun channelsOf(profileId: Long): Flow<List<GuideChannelEntity>>
    /** Channels in any profile's guide: the programme guide covers them for the whole household. */
    @Query("SELECT DISTINCT streamId FROM guide_channel")
    suspend fun allChannelIds(): List<Int>
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
    /** A film watched to the end; its [FILM_KEY] lets other versions of it match. */
    @Query(
        "INSERT OR IGNORE INTO watched_film (profileId, streamId, filmKey, at) " +
            "SELECT :profileId, m.streamId, $FILM_KEY, :at FROM movie m LEFT JOIN movie_info i ON i.streamId = m.streamId WHERE m.streamId = :streamId LIMIT 1",
    )
    suspend fun markWatched(profileId: Long, streamId: Int, at: Long)
    @Query("SELECT * FROM watched_film WHERE profileId = :profileId")
    fun watched(profileId: Long): Flow<List<WatchedFilmEntity>>
    /** An episode watched (70%); its series comes from the episode list, which the player had to load to play it. */
    @Query("INSERT OR IGNORE INTO watched_episode (profileId, episodeId, seriesId, at) SELECT :profileId, e.episodeId, e.seriesId, :at FROM episode e WHERE e.episodeId = :episodeId")
    suspend fun markEpisodeWatched(profileId: Long, episodeId: Int, at: Long)
    @Query("SELECT episodeId FROM watched_episode WHERE profileId = :profileId")
    fun watchedEpisodes(profileId: Long): Flow<List<Int>>
    /**
     * Series whose every known episode is watched, and their other language versions (same title
     * and year, [SERIES_KEY]): the poster's check.
     */
    @Query(
        "SELECT DISTINCT s.seriesId FROM series s WHERE $SERIES_KEY IN (SELECT $SERIES_KEY FROM series s WHERE s.seriesId IN (" +
            "SELECT e.seriesId FROM episode e LEFT JOIN watched_episode w ON w.episodeId = e.episodeId AND w.profileId = :profileId " +
            "GROUP BY e.seriesId HAVING COUNT(w.episodeId) = COUNT(*)))",
    )
    fun completeSeries(profileId: Long): Flow<List<Int>>

    /** For the log line at start: what's marked, with the film's name, newest first. */
    @Query("SELECT w.filmKey AS filmKey, w.at AS at, (SELECT name FROM movie WHERE streamId = w.streamId LIMIT 1) AS name FROM watched_film w WHERE w.profileId = :profileId ORDER BY w.at DESC")
    suspend fun watchedNames(profileId: Long): List<WatchedName>

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

data class StreamProgramme(val streamId: Int, val start: Long, val stop: Long, val title: String, val description: String?)

data class LiveGuideRow(val streamId: Int, val name: String, val epgChannelId: String?, val country: String?)

data class CategoryCountry(val id: String, val country: String?)

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

    /** Programmes of several streams that overlap [from, to), for the guide grid. */
    @Query(
        "SELECT c.streamId AS streamId, p.start AS start, p.stop AS stop, p.title AS title, p.description AS description " +
            "FROM epg_programme p JOIN epg_channel c ON c.channelId = p.channelId " +
            "WHERE c.streamId IN (:streamIds) AND p.stop > :from AND p.start < :to ORDER BY p.start",
    )
    suspend fun between(streamIds: List<Int>, from: Long, to: Long): List<StreamProgramme>

}

@Dao
interface MovieInfoDao {
    @Upsert suspend fun upsert(item: MovieInfoEntity)
    @Upsert suspend fun upsertAll(items: List<MovieInfoEntity>)
    @Query("SELECT * FROM movie_info WHERE streamId = :streamId") suspend fun get(streamId: Int): MovieInfoEntity?

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

/** One series across its language versions and categories: title and year (table `s`); "series:" keeps it apart from film keys. */
const val SERIES_KEY = "('series:' || lower(s.name) || '|' || IFNULL(s.year, ''))"

/** One film across its language versions: the TMDB id when known, else title and year (tables `m` and `i`). */
const val FILM_KEY = "COALESCE(CAST(m.tmdbId AS TEXT), CAST(i.tmdbId AS TEXT), lower(m.name) || '|' || IFNULL(m.year, ''))"

data class WatchedName(val filmKey: String, val at: Long, val name: String?)

/** A film with the people from its details, for Zoeken op regisseur of acteur. */
data class MoviePeopleRow(@Embedded val movie: MovieEntity, val people: String?, val director: String?, val adult: Boolean?)

/** A film tile: the first version of a title in a category, and how many versions it stands for. */
data class MovieTile(@Embedded val movie: MovieEntity, val versions: Int, val firstSort: Int)

data class MovieVersionRow(@Embedded val movie: MovieEntity, val country: String?)

/** A guide programme that matches a followed term, with the channel it's on. */
data class FollowMatchRow(
    val term: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val streamId: Int,
    val channelName: String,
    val categoryId: String,
    val archiveDays: Int,
    val catchup: Boolean,
    val logoUrl: String?,
)

/** Per profile, like favourites. */
@Dao
interface FollowDao {
    @Upsert suspend fun upsert(item: FollowEntity)
    @Query("DELETE FROM follow WHERE profileId = :profileId AND term = :term") suspend fun delete(profileId: Long, term: String)
    @Query("SELECT * FROM follow WHERE profileId = :profileId ORDER BY addedAt") fun observe(profileId: Long): Flow<List<FollowEntity>>

    /**
     * Programmes between [from] and [until] whose title or description contains one of the profile's
     * terms (SQLite's LIKE ignores case; a cup match is often "KNVB Beker" with the clubs only in the
     * description), on every live channel that uses that guide channel. Duplicates (the same
     * programme on an HD and an FHD channel) are folded by [nl.jordy.iptv.data.repo.FollowMatches].
     */
    @Query(
        "SELECT f.term AS term, p.start AS start, p.stop AS stop, p.title AS title, l.streamId AS streamId, " +
            "l.name AS channelName, l.categoryId AS categoryId, l.archiveDays AS archiveDays, l.catchup AS catchup, l.logoUrl AS logoUrl " +
            "FROM follow f JOIN epg_programme p ON (p.title LIKE '%' || f.term || '%' OR IFNULL(p.description, '') LIKE '%' || f.term || '%') " +
            "JOIN epg_channel c ON c.channelId = p.channelId JOIN live_channel l ON l.streamId = c.streamId " +
            "LEFT JOIN category k ON k.type = 'LIVE' AND k.id = l.categoryId " +
            "WHERE f.profileId = :profileId AND p.stop > :from AND p.start < :until AND (:hideAdult = 0 OR IFNULL(k.adult, 0) = 0) " +
            "ORDER BY p.start, l.sortIndex",
    )
    suspend fun matches(profileId: Long, from: Long, until: Long, hideAdult: Boolean): List<FollowMatchRow>
}

/** One-off reminders, per profile. */
@Dao
interface ReminderDao {
    @Upsert suspend fun upsert(item: ReminderEntity)
    @Query("DELETE FROM reminder WHERE profileId = :profileId AND streamId = :streamId AND start = :start")
    suspend fun delete(profileId: Long, streamId: Int, start: Long)
    @Query("SELECT * FROM reminder WHERE profileId = :profileId AND stop > :now ORDER BY start")
    fun observe(profileId: Long, now: Long): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminder WHERE profileId = :profileId AND stop > :now ORDER BY start")
    suspend fun upcoming(profileId: Long, now: Long): List<ReminderEntity>
    /** Programmes that are over: their reminders have done their job. */
    @Query("DELETE FROM reminder WHERE stop <= :now") suspend fun deleteOver(now: Long)
}

/** A calendar appointment on the channel its calendar is tied to. */
data class CalendarMatchRow(
    val name: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val streamId: Int,
    val channelName: String,
    val categoryId: String,
    val hasArchive: Boolean,
    val logoUrl: String?,
)

/** A live channel to tie a calendar to, with the category that tells copies apart. */
data class ChannelPick(
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val categoryName: String,
    val logoUrl: String?,
    val archiveDays: Int,
    val catchup: Boolean,
)

/** Followed calendars per profile, and their appointments (shared per link). */
@Dao
interface CalendarDao {
    @Upsert suspend fun upsert(item: CalendarEntity)
    @Query("DELETE FROM calendar WHERE profileId = :profileId AND url = :url") suspend fun delete(profileId: Long, url: String)
    @Query("SELECT * FROM calendar WHERE profileId = :profileId ORDER BY addedAt") fun observe(profileId: Long): Flow<List<CalendarEntity>>

    /** Every followed link with its oldest fetch, for refreshing; links nobody follows any more are dropped by [dropOrphanEvents]. */
    @Query("SELECT url, MIN(fetchedAt) AS fetchedAt FROM calendar GROUP BY url") suspend fun links(): List<CalendarLink>
    @Query("UPDATE calendar SET fetchedAt = :at WHERE url = :url") suspend fun setFetched(url: String, at: Long)

    @Query("DELETE FROM calendar_event WHERE url = :url") suspend fun clearEvents(url: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvents(items: List<CalendarEventEntity>)
    @Query("DELETE FROM calendar_event WHERE url NOT IN (SELECT url FROM calendar)") suspend fun dropOrphanEvents()

    /** Appointments between [from] and [until] of the profile's calendars, on each calendar's channel. */
    @Query(
        "SELECT c.name AS name, e.start AS start, e.stop AS stop, e.title AS title, c.streamId AS streamId, c.channelName AS channelName, " +
            "c.categoryId AS categoryId, c.hasArchive AS hasArchive, c.logoUrl AS logoUrl " +
            "FROM calendar c JOIN calendar_event e ON e.url = c.url " +
            "WHERE c.profileId = :profileId AND e.stop > :from AND e.start < :until ORDER BY e.start",
    )
    suspend fun matches(profileId: Long, from: Long, until: Long): List<CalendarMatchRow>

    /** The first appointment still to come per link, for the list in Volgen. */
    @Query("SELECT url, MIN(start) AS start, title FROM calendar_event WHERE stop > :now GROUP BY url")
    fun next(now: Long): Flow<List<CalendarNext>>

    /** Live channels whose name contains [query], by the provider's order; one row per category it's in. */
    @Query(
        "SELECT l.streamId, l.name, l.categoryId, k.name AS categoryName, l.logoUrl, l.archiveDays, l.catchup FROM live_channel l " +
            "JOIN category k ON k.type = 'LIVE' AND k.id = l.categoryId " +
            "WHERE l.name LIKE '%' || :query || '%' AND (:hideAdult = 0 OR k.adult = 0) ORDER BY k.sortIndex, l.sortIndex LIMIT 60",
    )
    suspend fun channels(query: String, hideAdult: Boolean): List<ChannelPick>
}

data class CalendarLink(val url: String, val fetchedAt: Long)
data class CalendarNext(val url: String, val start: Long, val title: String)


/** Kijkgeschiedenis, per profile. */
@Dao
interface WatchLogDao {
    @Insert suspend fun insert(item: WatchLogEntity)
    @Query("SELECT * FROM watch_log WHERE profileId = :profileId AND startedAt >= :since ORDER BY startedAt DESC")
    fun since(profileId: Long, since: Long): Flow<List<WatchLogEntity>>
    @Query("DELETE FROM watch_log WHERE startedAt < :before")
    suspend fun prune(before: Long)
}

/** Zenderstatus: live sessions and chosen replacements. */
@Dao
interface ChannelHealthDao {
    @Insert suspend fun insert(item: ChannelCheckEntity)
    @Query("DELETE FROM channel_check WHERE at < :before") suspend fun prune(before: Long)
    @Query("SELECT * FROM channel_check") suspend fun checks(): List<ChannelCheckEntity>
    /** Other versions of a channel: the same cleaned name, any quality or category. */
    @Query("SELECT * FROM live_channel WHERE name = :name COLLATE NOCASE GROUP BY streamId")
    suspend fun liveNamed(name: String): List<LiveChannelEntity>
    @Upsert suspend fun upsertSwap(item: ChannelSwapEntity)
    @Query("DELETE FROM channel_swap WHERE fromStreamId = :from") suspend fun deleteSwap(from: Int)
    @Query("SELECT * FROM channel_swap") suspend fun swaps(): List<ChannelSwapEntity>
    @Query("SELECT * FROM channel_swap ORDER BY at DESC") fun observeSwaps(): Flow<List<ChannelSwapEntity>>
}

@Dao
interface SeriesIntroDao {
    @Upsert suspend fun upsert(item: SeriesIntroEntity)
    @Query("SELECT endMs FROM series_intro WHERE seriesId = :seriesId") suspend fun end(seriesId: Int): Long?
}

/** A film as "Voor jou" weighs it: one row per [FILM_KEY], with its details from movie_info. */
data class TasteFilmRow(
    val filmKey: String,
    val streamId: Int,
    val name: String,
    val posterUrl: String?,
    val year: Int?,
    val rating: Double?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val durationSecs: Int?,
    /** Rows across categories and languages: how widely the provider carries it, a rough "known film". */
    val versions: Int,
)

@Dao
interface TasteDao {
    @Upsert suspend fun upsert(item: TasteVoteEntity)

    @Query("SELECT * FROM taste_vote WHERE profileId = :profileId ORDER BY at DESC")
    fun observe(profileId: Long): Flow<List<TasteVoteEntity>>

    @Query("SELECT * FROM taste_vote WHERE profileId = :profileId")
    suspend fun all(profileId: Long): List<TasteVoteEntity>

    @Query("DELETE FROM taste_vote WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)

    @Query("SELECT COUNT(*) FROM taste_vote WHERE profileId = :profileId AND filmKey = :filmKey")
    suspend fun count(profileId: Long, filmKey: String): Int

    /** The key one version of a film shares with its other versions. */
    @Query("SELECT $FILM_KEY FROM movie m LEFT JOIN movie_info i ON i.streamId = m.streamId WHERE m.streamId = :streamId LIMIT 1")
    suspend fun filmKey(streamId: Int): String?

    /** Films in the given countries whose details have arrived: what can be suggested or swiped. */
    @Query(
        "SELECT $FILM_KEY AS filmKey, m.streamId, m.name, m.posterUrl, m.year, COALESCE(i.rating, m.rating) AS rating, " +
            "i.genre, i.cast, i.director, i.durationSecs, COUNT(*) AS versions " +
            "FROM movie m JOIN category c ON c.type = 'MOVIE' AND c.id = m.categoryId " +
            "JOIN movie_info i ON i.streamId = m.streamId AND i.failed = 0 " +
            "WHERE c.country IN (:countries) AND (:hideAdult = 0 OR c.adult = 0) GROUP BY filmKey",
    )
    suspend fun films(countries: List<String>, hideAdult: Boolean): List<TasteFilmRow>

    /** Details of the voted versions, whatever their country. */
    @Query(
        "SELECT $FILM_KEY AS filmKey, m.streamId, m.name, m.posterUrl, m.year, COALESCE(i.rating, m.rating) AS rating, " +
            "i.genre, i.cast, i.director, i.durationSecs, 1 AS versions " +
            "FROM movie m JOIN movie_info i ON i.streamId = m.streamId WHERE m.streamId IN (:streamIds) GROUP BY m.streamId",
    )
    suspend fun filmsById(streamIds: List<Int>): List<TasteFilmRow>

    /** Series in the given countries, one row per title and year (language versions merged), as "Voor jou" weighs them. */
    @Query(
        "SELECT $SERIES_KEY AS filmKey, s.seriesId AS streamId, s.name, s.coverUrl AS posterUrl, s.year, s.rating, " +
            "s.genre, NULL AS `cast`, NULL AS director, NULL AS durationSecs, COUNT(*) AS versions " +
            "FROM series s JOIN category c ON c.type = 'SERIES' AND c.id = s.categoryId " +
            "WHERE c.country IN (:countries) AND (:hideAdult = 0 OR c.adult = 0) GROUP BY filmKey",
    )
    suspend fun series(countries: List<String>, hideAdult: Boolean): List<TasteFilmRow>

    @Query(
        "SELECT $SERIES_KEY AS filmKey, s.seriesId AS streamId, s.name, s.coverUrl AS posterUrl, s.year, s.rating, " +
            "s.genre, NULL AS `cast`, NULL AS director, NULL AS durationSecs, 1 AS versions " +
            "FROM series s WHERE s.seriesId IN (:seriesIds) GROUP BY s.seriesId",
    )
    suspend fun seriesById(seriesIds: List<Int>): List<TasteFilmRow>

    @Query("SELECT $SERIES_KEY FROM series s WHERE s.seriesId = :seriesId LIMIT 1")
    suspend fun seriesKey(seriesId: Int): String?

    /** Series the profile watched an episode of: not suggested again. */
    @Query(
        "SELECT DISTINCT $SERIES_KEY FROM series s WHERE s.seriesId IN (SELECT e.seriesId FROM episode e WHERE e.episodeId IN " +
            "(SELECT itemId FROM watch_log WHERE profileId = :profileId AND type = 'SERIES' " +
            "UNION SELECT itemId FROM playback_position WHERE profileId = :profileId AND type = 'SERIES') " +
            "UNION SELECT seriesId FROM watched_episode WHERE profileId = :profileId)",
    )
    suspend fun watchedSeriesKeys(profileId: Long): List<String>

    /** Films the profile watched (the check, kept for good) or started: not suggested again. */
    @Query(
        "SELECT DISTINCT $FILM_KEY FROM movie m LEFT JOIN movie_info i ON i.streamId = m.streamId " +
            "WHERE m.streamId IN (SELECT itemId FROM watch_log WHERE profileId = :profileId AND type = 'MOVIE' " +
            "UNION SELECT itemId FROM playback_position WHERE profileId = :profileId AND type = 'MOVIE' " +
            "UNION SELECT streamId FROM watched_film WHERE profileId = :profileId) " +
            "UNION SELECT filmKey FROM watched_film WHERE profileId = :profileId",
    )
    suspend fun watchedKeys(profileId: Long): List<String>
}
