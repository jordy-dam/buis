package nl.jordy.iptv.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        LiveChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        ProfileEntity::class,
        FavoriteEntity::class,
        PlaybackPositionEntity::class,
        SyncInfoEntity::class,
        SearchEntity::class,
        EpgProgrammeEntity::class,
        EpgChannelEntity::class,
        EpgInfoEntity::class,
        MovieInfoEntity::class,
        FollowEntity::class,
        ReminderEntity::class,
        WatchLogEntity::class,
        ChannelCheckEntity::class,
        ChannelSwapEntity::class,
        SeriesIntroEntity::class,
        GuideEntity::class,
        GuideChannelEntity::class,
        CalendarEntity::class,
        CalendarEventEntity::class,
        TasteVoteEntity::class,
        WatchedFilmEntity::class,
        WatchedEpisodeEntity::class,
    ],
    version = 32,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AppDatabase.CatalogResync::class),
        // Guide tables only; filled by the first guide refresh.
        AutoMigration(from = 3, to = 4),
        // Film details table only; filled in the background.
        AutoMigration(from = 4, to = 5),
        // Drawn avatars; existing profiles keep their letter (face 0).
        AutoMigration(from = 5, to = 6),
        // TMDB ids, to merge language versions with different titles: catalog re-syncs, film details are fetched again.
        AutoMigration(from = 6, to = 7, spec = AppDatabase.TmdbRefetch::class),
        // Sleep timer per profile, off for everyone at first.
        AutoMigration(from = 7, to = 8),
        // Pinned services per profile, none at first.
        AutoMigration(from = 8, to = 9),
        // Catch-up days per channel: catalog re-syncs to fill them.
        AutoMigration(from = 9, to = 10, spec = AppDatabase.CatalogResync::class),
        // What each profile follows ("Formule 1"); a new, empty table.
        AutoMigration(from = 10, to = 11),
        // One-off reminders for a single programme, set from a channel's guide; a new, empty table.
        AutoMigration(from = 11, to = 12),
        // Pauzemoment per profile, off for everyone at first.
        AutoMigration(from = 12, to = 13),
        // How long the Pauzemoment lasts; one minute for everyone at first.
        AutoMigration(from = 13, to = 14),
        // Audio and subtitle language and the player clock per profile: stream default, subtitles off, clock on.
        AutoMigration(from = 14, to = 15),
        // Bedtijd per profile, off for everyone at first.
        AutoMigration(from = 15, to = 16),
        // Kijkgeschiedenis; a new, empty table.
        AutoMigration(from = 16, to = 17),
        // Zenderstatus: live sessions and replacements; new, empty tables.
        AutoMigration(from = 17, to = 18),
        // Intro overslaan, learned per series; a new, empty table.
        AutoMigration(from = 18, to = 19),
        // Film running time for the length filter: film details are fetched again quietly.
        AutoMigration(from = 19, to = 20, spec = AppDatabase.DurationRefetch::class),
        // Category labels ("PPV", "FHD") apart from the name, and cleaner channel names: catalog re-syncs.
        AutoMigration(from = 21, to = 22, spec = AppDatabase.CatalogResync::class),
        // Automatisch overschakelen per profile, off for everyone at first.
        AutoMigration(from = 22, to = 23),
        // A gadget on the avatar per profile, none for everyone at first.
        AutoMigration(from = 23, to = 24),
        // Followed calendars, none for anyone at first.
        AutoMigration(from = 25, to = 26),
        // A look per profile, Standaard for everyone at first.
        AutoMigration(from = 26, to = 27),
        // A car per profile for Garage, the 911 for everyone at first.
        AutoMigration(from = 27, to = 28),
        // "Voor jou": thumbs per profile and film; a new, empty table.
        AutoMigration(from = 28, to = 29),
        // Series in "Voor jou": a vote says whether it was a film or a series; existing votes are films.
        AutoMigration(from = 29, to = 30),
        // A check on watched films; filled once from the watch log (40 minutes or more of a film).
        AutoMigration(from = 30, to = 31, spec = AppDatabase.WatchedFromLog::class),
        // The same for episodes, filled once from saved positions and the watch log.
        AutoMigration(from = 31, to = 32, spec = AppDatabase.WatchedEpisodesFromLog::class),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun episodes(): EpisodeDao
    abstract fun profiles(): ProfileDao
    abstract fun favorites(): FavoriteDao
    abstract fun guides(): GuideDao
    abstract fun playback(): PlaybackDao
    abstract fun syncInfo(): SyncInfoDao
    abstract fun search(): SearchDao
    abstract fun epg(): EpgDao
    abstract fun movieInfo(): MovieInfoDao
    abstract fun follows(): FollowDao
    abstract fun reminders(): ReminderDao
    abstract fun calendars(): CalendarDao
    abstract fun watchLog(): WatchLogDao
    abstract fun channelHealth(): ChannelHealthDao
    abstract fun seriesIntro(): SeriesIntroDao
    abstract fun taste(): TasteDao

    /**
     * Films already watched before the check existed: 70% or more in a saved position, or (no
     * duration in the log) 40 minutes or more of them in the watch log, 90 days back.
     */
    class WatchedFromLog : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO watched_film (profileId, streamId, filmKey, at) " +
                    "SELECT w.profileId, w.itemId, (SELECT $FILM_KEY FROM movie m LEFT JOIN movie_info i ON i.streamId = m.streamId WHERE m.streamId = w.itemId LIMIT 1), MAX(w.startedAt) " +
                    "FROM watch_log w WHERE w.type = 'MOVIE' GROUP BY w.profileId, w.itemId HAVING SUM(w.seconds) >= 2400 " +
                    "AND EXISTS (SELECT 1 FROM movie m WHERE m.streamId = w.itemId)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO watched_film (profileId, streamId, filmKey, at) " +
                    "SELECT p.profileId, p.itemId, (SELECT $FILM_KEY FROM movie m LEFT JOIN movie_info i ON i.streamId = m.streamId WHERE m.streamId = p.itemId LIMIT 1), p.updatedAt " +
                    "FROM playback_position p WHERE p.type = 'MOVIE' AND p.durationMs > 0 AND p.positionMs >= p.durationMs * 0.7 " +
                    "AND EXISTS (SELECT 1 FROM movie m WHERE m.streamId = p.itemId)",
            )
        }
    }

    /**
     * Episodes already watched before episodes had a check: 70% or more in a saved position, or in
     * the watch log 70% of the episode's length (20 minutes when the length is unknown). Only
     * episodes BUIS has seen the list of, which is every series that was opened.
     */
    class WatchedEpisodesFromLog : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO watched_episode (profileId, episodeId, seriesId, at) " +
                    "SELECT p.profileId, p.itemId, e.seriesId, p.updatedAt FROM playback_position p JOIN episode e ON e.episodeId = p.itemId " +
                    "WHERE p.type = 'SERIES' AND p.durationMs > 0 AND p.positionMs >= p.durationMs * 0.7",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO watched_episode (profileId, episodeId, seriesId, at) " +
                    "SELECT w.profileId, w.itemId, e.seriesId, MAX(w.startedAt) FROM watch_log w JOIN episode e ON e.episodeId = w.itemId " +
                    "WHERE w.type = 'SERIES' GROUP BY w.profileId, w.itemId " +
                    "HAVING SUM(w.seconds) >= CASE WHEN IFNULL(e.durationSecs, 0) > 0 THEN e.durationSecs * 0.7 ELSE 1200 END",
            )
        }
    }

    /**
     * The catalog is a cache of the provider: after a schema change it's emptied and the next
     * launch re-syncs it with the new columns filled. Favourites and resume positions are the
     * user's own data and are never touched here.
     */
    class CatalogResync : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            listOf("category", "live_channel", "movie", "series", "sync_info").forEach { db.execSQL("DELETE FROM `$it`") }
        }
    }

    /**
     * Schema 7 adds TMDB ids: the catalog is emptied so the next sync fills `movie.tmdbId`, and
     * film details are marked for a quiet re-fetch (their genres keep working meanwhile).
     */
    /** Schema 20 stores the running time: details are re-fetched in the background, genres keep working meanwhile. */
    class DurationRefetch : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE movie_info SET failed = 1, fetchedAt = 0")
        }
    }

    class TmdbRefetch : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            CatalogResync().onPostMigrate(db)
            db.execSQL("UPDATE movie_info SET failed = 1, fetchedAt = 0")
        }
    }

    companion object {
        /**
         * Channels keep their "HD" badge from now on (HD and SD versions looked the same): the catalog
         * is emptied so the next launch re-syncs it with the badges. Nothing else changes.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) = CatalogResync().onPostMigrate(db)
        }

        /** What a profile's favourite channels become: its first guide. */
        const val FIRST_GUIDE_NAME = "Mijn gids"

        /**
         * Guides: every profile with favourite channels gets "Mijn gids" holding them, newest first as
         * Favorieten showed them. Films and series stay favourites.
         */
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `guide` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_guide_profileId` ON `guide` (`profileId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `guide_channel` (`guideId` INTEGER NOT NULL, `streamId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `logoUrl` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`guideId`, `streamId`), " +
                        "FOREIGN KEY(`guideId`) REFERENCES `guide`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "INSERT INTO guide (profileId, name, position) " +
                        "SELECT DISTINCT profileId, '$FIRST_GUIDE_NAME', 0 FROM favorite WHERE type = 'LIVE'",
                )
                db.execSQL(
                    "INSERT INTO guide_channel (guideId, streamId, name, logoUrl, position) " +
                        "SELECT g.id, f.itemId, f.name, f.imageUrl, " +
                        "(SELECT COUNT(*) FROM favorite n WHERE n.profileId = f.profileId AND n.type = 'LIVE' AND n.addedAt > f.addedAt) " +
                        "FROM favorite f JOIN guide g ON g.profileId = f.profileId WHERE f.type = 'LIVE'",
                )
                db.execSQL("DELETE FROM favorite WHERE type = 'LIVE'")
            }
        }

        /** The profile that takes over everything saved before profiles existed. */
        const val FIRST_PROFILE_NAME = "Profiel 1"

        /**
         * Profiles: a first profile gets the existing favourites and resume positions. The two
         * tables are rebuilt because their primary key gains `profileId`. The catalog is emptied
         * (as in [CatalogResync]) so the next sync fills the new `category.adult` column.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`avatar` INTEGER NOT NULL, `pinHash` TEXT, `preferredCountries` TEXT NOT NULL, `onlyPreferred` INTEGER NOT NULL, " +
                        "`kids` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO profile (id, name, avatar, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt) " +
                        "VALUES (1, '$FIRST_PROFILE_NAME', 0, NULL, 'NL', 1, 0, $now, $now)",
                )

                db.execSQL(
                    "CREATE TABLE `favorite_new` (`profileId` INTEGER NOT NULL, `type` TEXT NOT NULL, `itemId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `imageUrl` TEXT, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `type`, `itemId`), " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("INSERT INTO favorite_new (profileId, type, itemId, name, imageUrl, addedAt) SELECT 1, type, itemId, name, imageUrl, addedAt FROM favorite")
                db.execSQL("DROP TABLE favorite")
                db.execSQL("ALTER TABLE favorite_new RENAME TO favorite")

                db.execSQL(
                    "CREATE TABLE `playback_position_new` (`profileId` INTEGER NOT NULL, `type` TEXT NOT NULL, `itemId` INTEGER NOT NULL, " +
                        "`positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`profileId`, `type`, `itemId`), " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "INSERT INTO playback_position_new (profileId, type, itemId, positionMs, durationMs, updatedAt) " +
                        "SELECT 1, type, itemId, positionMs, durationMs, updatedAt FROM playback_position",
                )
                db.execSQL("DROP TABLE playback_position")
                db.execSQL("ALTER TABLE playback_position_new RENAME TO playback_position")

                db.execSQL("ALTER TABLE category ADD COLUMN `adult` INTEGER NOT NULL DEFAULT 0")
                CatalogResync().onPostMigrate(db)
            }
        }
    }
}
