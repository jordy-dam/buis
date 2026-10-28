package nl.jordy.iptv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

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
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun episodes(): EpisodeDao
    abstract fun favorites(): FavoriteDao
    abstract fun playback(): PlaybackDao
    abstract fun syncInfo(): SyncInfoDao
}
