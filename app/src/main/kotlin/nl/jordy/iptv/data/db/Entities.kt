package nl.jordy.iptv.data.db

import androidx.room.Entity
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
