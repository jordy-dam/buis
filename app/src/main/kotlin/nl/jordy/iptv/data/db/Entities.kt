package nl.jordy.iptv.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import nl.jordy.iptv.data.xtream.ContentType

/*
 * Catalog rows carry a `generation`: a sync writes the new generation with REPLACE, then deletes
 * everything older. The UI never sees an empty catalog mid-sync, and removed items disappear.
 *
 * Live/movie/series rows are keyed by (categoryId, id): providers list the same item in several
 * categories, and it should show up in each of them.
 */

@Entity(tableName = "category", primaryKeys = ["type", "id"])
data class CategoryEntity(
    val type: ContentType,
    val id: String,
    val name: String,
    val sortIndex: Int,
    val generation: Long,
    /** ISO-ish country tag from the provider's prefix (`┃NL┃ …` → "NL"); null for PPV, sports packs etc. */
    val country: String? = null,
)

@Entity(tableName = "live_channel", primaryKeys = ["categoryId", "streamId"], indices = [Index("streamId")])
data class LiveChannelEntity(
    val categoryId: String,
    val streamId: Int,
    val name: String,
    val logoUrl: String?,
    val epgChannelId: String?,
    val sortIndex: Int,
    val generation: Long,
    /** Display labels split off the raw name ("4K", "Multi"), comma-separated. */
    @ColumnInfo(defaultValue = "") val badges: String = "",
    /** Provider marks the channel as having catch-up (`⏺` in the name). */
    @ColumnInfo(defaultValue = "0") val catchup: Boolean = false,
)

@Entity(tableName = "movie", primaryKeys = ["categoryId", "streamId"], indices = [Index("streamId")])
data class MovieEntity(
    val categoryId: String,
    val streamId: Int,
    val name: String,
    val posterUrl: String?,
    val rating: Double?,
    val year: Int?,
    val containerExtension: String,
    val addedAt: Long?,
    val sortIndex: Int,
    val generation: Long,
    @ColumnInfo(defaultValue = "") val badges: String = "",
)

@Entity(tableName = "series", primaryKeys = ["categoryId", "seriesId"], indices = [Index("seriesId")])
data class SeriesEntity(
    val categoryId: String,
    val seriesId: Int,
    val name: String,
    val coverUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val genre: String?,
    val rating: Double?,
    val year: Int?,
    val sortIndex: Int,
    val generation: Long,
    @ColumnInfo(defaultValue = "") val badges: String = "",
)

/** Fetched on demand when a series is opened, not during the catalog sync. */
@Entity(tableName = "episode", indices = [Index("seriesId")])
data class EpisodeEntity(
    @PrimaryKey val episodeId: Int,
    val seriesId: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val containerExtension: String,
    val plot: String?,
    val imageUrl: String?,
    val durationSecs: Int?,
)

/** Name and image are a snapshot, so a favourite still renders if a re-sync drops or renames the item. */
@Entity(tableName = "favorite", primaryKeys = ["type", "itemId"])
data class FavoriteEntity(
    val type: ContentType,
    val itemId: Int,
    val name: String,
    val imageUrl: String?,
    val addedAt: Long,
)

/** Movies use type MOVIE + streamId; episodes use type SERIES + episodeId. */
@Entity(tableName = "playback_position", primaryKeys = ["type", "itemId"])
data class PlaybackPositionEntity(
    val type: ContentType,
    val itemId: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Entity(tableName = "sync_info")
data class SyncInfoEntity(
    @PrimaryKey val id: Int = 0,
    val lastSyncAt: Long,
    val generation: Long,
)

/**
 * Full-text index over cleaned titles of the whole catalog, filled during sync. Keyed by
 * (type, itemId) so a hit opens the item; country is kept to rank preferred countries first.
 */
@Fts4(notIndexed = ["type", "itemId", "country", "categoryId"])
@Entity(tableName = "search")
data class SearchEntity(
    val title: String,
    val type: String,
    val itemId: Int,
    val categoryId: String,
    val country: String?,
)
