package nl.jordy.iptv.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
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
    /** Adult content by the provider's name (`ADULT`, `XXX`, `18+`); hidden on kids profiles. */
    @ColumnInfo(defaultValue = "0") val adult: Boolean = false,
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
    /** Days the panel keeps for "vanaf begin" (`tv_archive_duration`); 0 = none, see [hasArchive]. */
    @ColumnInfo(defaultValue = "0") val archiveDays: Int = 0,
) {
    /** The `⏺` mark alone also counts: some panels mark catch-up in the name and leave `tv_archive` at 0. */
    val hasArchive get() = archiveDays > 0 || catchup
}

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
    /** TMDB id from the film list, when the panel sends it. Language versions share it. */
    val tmdbId: Int? = null,
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

/**
 * A household member. Their country settings live on the row, so switching profile switches them.
 * [pinHash] is a household lock against children, not security (see [nl.jordy.iptv.data.profile.Pin]).
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Index into the built-in avatar colours. */
    val avatar: Int,
    /** Drawn avatar 1..5 (cat, fox, robot, owl, monster), or 0 for the initial letter. */
    @ColumnInfo(defaultValue = "0") val face: Int = 0,
    val pinHash: String? = null,
    /** Comma-separated group codes ("NL,BE"). */
    val preferredCountries: String = "NL",
    val onlyPreferred: Boolean = true,
    /** Hides adult categories everywhere. */
    val kids: Boolean = false,
    /** Sleep timer in minutes without a key press, 0 = off (2g). */
    @ColumnInfo(defaultValue = "0") val sleepMinutes: Int = 0,
    /** Pauzemoment: a one-minute break after this many minutes of films/episodes, 0 = off. */
    @ColumnInfo(defaultValue = "0") val breakMinutes: Int = 0,
    /** How long that break lasts, in minutes. */
    @ColumnInfo(defaultValue = "1") val breakLength: Int = 1,
    /** Audio language the player picks when the stream has it ("nl", "en"); empty = the stream's default. */
    @ColumnInfo(defaultValue = "") val audioLang: String = "",
    /** Subtitle language the player switches on when the stream has it; empty = subtitles off. */
    @ColumnInfo(defaultValue = "") val subtitleLang: String = "",
    /** A small clock in the player bar. */
    @ColumnInfo(defaultValue = "1") val showClock: Boolean = true,
    /** Categories pinned to the top of Films/Series, as [nl.jordy.iptv.data.settings.CategoryGroups.pinKey]s, comma-separated. */
    @ColumnInfo(defaultValue = "") val pinnedCategories: String = "",
    /** The profile with the highest value is the active one; picking a profile bumps it. */
    val lastUsedAt: Long = 0,
    val createdAt: Long = 0,
)

/** Name and image are a snapshot, so a favourite still renders if a re-sync drops or renames the item. */
@Entity(
    tableName = "favorite",
    primaryKeys = ["profileId", "type", "itemId"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class FavoriteEntity(
    val profileId: Long,
    val type: ContentType,
    val itemId: Int,
    val name: String,
    val imageUrl: String?,
    val addedAt: Long,
)

/** Movies use type MOVIE + streamId; episodes use type SERIES + episodeId. */
@Entity(
    tableName = "playback_position",
    primaryKeys = ["profileId", "type", "itemId"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class PlaybackPositionEntity(
    val profileId: Long,
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

/**
 * The provider's full guide (XMLTV), only for channels in the household's countries and only
 * from two hours ago to a day and a half ahead. Replaced as a whole on every refresh.
 */
@Entity(tableName = "epg_programme", primaryKeys = ["channelId", "start"], indices = [Index("stop")])
data class EpgProgrammeEntity(
    val channelId: String,
    /** Epoch seconds; [stop] is exclusive. */
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

/**
 * Which guide channel a live stream uses: its own EPG id, or that of a sibling with the same
 * cleaned name in the same country ("NPO 1 HD" without an id uses the id of "NPO 1").
 */
@Entity(tableName = "epg_channel")
data class EpgChannelEntity(
    @PrimaryKey val streamId: Int,
    val channelId: String,
)

@Entity(tableName = "epg_info")
data class EpgInfoEntity(
    @PrimaryKey val id: Int = 0,
    val refreshedAt: Long,
    /** Live streams that have a guide after this refresh. */
    val streams: Int,
)

/**
 * Per-film details from the provider's `get_vod_info`, fetched in the background (see
 * [nl.jordy.iptv.data.repo.MovieEnricher]) or when a film is opened. Kept across catalog syncs:
 * it's keyed by stream id only, and it took hours to collect.
 */
@Entity(tableName = "movie_info")
data class MovieInfoEntity(
    @PrimaryKey val streamId: Int,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val plot: String?,
    val rating: Double?,
    val fetchedAt: Long,
    /** The request failed; retried after a day. */
    @ColumnInfo(defaultValue = "0") val failed: Boolean = false,
    /** TMDB id from `get_vod_info`: groups language versions whose titles differ. */
    val tmdbId: Int? = null,
)

/** A search term a profile follows ("Formule 1", "FC Utrecht"): matching programmes get a reminder. */
@Entity(
    tableName = "follow",
    primaryKeys = ["profileId", "term"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class FollowEntity(
    val profileId: Long,
    val term: String,
    val addedAt: Long,
)

/**
 * A reminder for one programme on one channel ("this match, tonight"), set from the channel's guide.
 * Unlike a [FollowEntity] term it never matches anything else; it's removed once the programme is over.
 */
@Entity(
    tableName = "reminder",
    primaryKeys = ["profileId", "streamId", "start"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class ReminderEntity(
    val profileId: Long,
    val streamId: Int,
    val start: Long,
    val stop: Long,
    val title: String,
    val channelName: String,
    val categoryId: String,
    val logoUrl: String?,
    val hasArchive: Boolean,
)

