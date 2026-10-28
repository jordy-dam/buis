package nl.jordy.iptv.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        LiveChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        FavoriteEntity::class,
        PlaybackPositionEntity::class,
        SyncInfoEntity::class,
        SearchEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = AppDatabase.CatalogResync::class)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun episodes(): EpisodeDao
    abstract fun favorites(): FavoriteDao
    abstract fun playback(): PlaybackDao
    abstract fun syncInfo(): SyncInfoDao
    abstract fun search(): SearchDao

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
}
