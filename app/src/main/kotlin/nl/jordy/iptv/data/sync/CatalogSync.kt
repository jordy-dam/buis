package nl.jordy.iptv.data.sync

import androidx.room.withTransaction
import kotlinx.serialization.json.JsonElement
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.db.LiveChannelEntity
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.SeriesEntity
import nl.jordy.iptv.data.db.SyncInfoEntity
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.XtreamClient
import nl.jordy.iptv.data.xtream.XtreamCredentials
import nl.jordy.iptv.data.xtream.XtreamParser
import javax.inject.Inject
import javax.inject.Singleton

data class SyncProgress(val type: ContentType, val items: Int)

/**
 * Pulls categories and the full Live/VOD/Series catalog into Room. Items are decoded one by one
 * from the response and written in batches, so a 10k+ catalog never sits in memory as JSON.
 */
@Singleton
class CatalogSync @Inject constructor(
    private val client: XtreamClient,
    private val db: AppDatabase,
) {
    private val dao = db.catalog()

    suspend fun run(c: XtreamCredentials, onProgress: (SyncProgress) -> Unit = {}) {
        val generation = System.currentTimeMillis()

        sync(c, ContentType.LIVE, "get_live_categories", "get_live_streams", generation, onProgress,
            parse = XtreamParser::liveStream, category = { it.categoryId },
            toEntity = { it, cat, i -> LiveChannelEntity(cat, it.streamId, it.name, it.logoUrl, it.epgChannelId, i, generation) },
            write = dao::upsertLive, existing = dao::liveCount, deleteOld = dao::deleteOldLive)

        sync(c, ContentType.MOVIE, "get_vod_categories", "get_vod_streams", generation, onProgress,
            parse = XtreamParser::movie, category = { it.categoryId },
            toEntity = { it, cat, i -> MovieEntity(cat, it.streamId, it.name, it.posterUrl, it.rating, it.year, it.containerExtension, it.addedAt, i, generation) },
            write = dao::upsertMovies, existing = dao::movieCount, deleteOld = dao::deleteOldMovies)

        sync(c, ContentType.SERIES, "get_series_categories", "get_series", generation, onProgress,
            parse = XtreamParser::series, category = { it.categoryId },
            toEntity = { it, cat, i -> SeriesEntity(cat, it.seriesId, it.name, it.coverUrl, it.backdropUrl, it.plot, it.genre, it.rating, it.year, i, generation) },
            write = dao::upsertSeries, existing = dao::seriesCount, deleteOld = dao::deleteOldSeries)

        db.syncInfo().upsert(SyncInfoEntity(lastSyncAt = System.currentTimeMillis(), generation = generation))
    }

    private suspend fun <M, E> sync(
        c: XtreamCredentials,
        type: ContentType,
        categoriesAction: String,
        itemsAction: String,
        generation: Long,
        onProgress: (SyncProgress) -> Unit,
        parse: (JsonElement) -> M?,
        category: (M) -> String?,
        toEntity: (M, String, Int) -> E,
        write: suspend (List<E>) -> Unit,
        existing: suspend () -> Int,
        deleteOld: suspend (Long) -> Unit,
    ) {
        val categories = mutableListOf<CategoryEntity>()
        client.forEachInArray(c, categoriesAction) { el ->
            XtreamParser.category(el)?.let { categories += CategoryEntity(type, it.id, it.name, categories.size, generation) }
        }

        var count = 0
        var uncategorized = false
        val batch = ArrayList<E>(BATCH_SIZE)
        suspend fun flush() {
            if (batch.isEmpty()) return
            db.withTransaction { write(batch) }
            batch.clear()
            onProgress(SyncProgress(type, count))
        }
        onProgress(SyncProgress(type, 0))
        client.forEachInArray(c, itemsAction) { el ->
            val item = parse(el) ?: return@forEachInArray
            val cat = category(item) ?: UNCATEGORIZED.also { uncategorized = true }
            batch += toEntity(item, cat, count++)
            if (batch.size >= BATCH_SIZE) flush()
        }
        flush()

        if (uncategorized) categories += CategoryEntity(type, UNCATEGORIZED, "Overig", Int.MAX_VALUE, generation)

        // A provider hiccup that returns an empty list must not wipe a catalog that was fine yesterday.
        if (count == 0 && existing() > 0) return
        db.withTransaction {
            dao.upsertCategories(categories)
            dao.deleteOldCategories(type, generation)
            deleteOld(generation)
        }
    }

    companion object {
        const val BATCH_SIZE = 500
        const val UNCATEGORIZED = ""
    }
}
