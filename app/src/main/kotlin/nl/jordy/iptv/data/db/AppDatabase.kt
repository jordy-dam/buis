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
    ],
    version = 7,
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
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun episodes(): EpisodeDao
    abstract fun profiles(): ProfileDao
    abstract fun favorites(): FavoriteDao
    abstract fun playback(): PlaybackDao
    abstract fun syncInfo(): SyncInfoDao
    abstract fun search(): SearchDao
    abstract fun epg(): EpgDao
    abstract fun movieInfo(): MovieInfoDao

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
    class TmdbRefetch : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            CatalogResync().onPostMigrate(db)
            db.execSQL("UPDATE movie_info SET failed = 1, fetchedAt = 0")
        }
    }

    companion object {
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
