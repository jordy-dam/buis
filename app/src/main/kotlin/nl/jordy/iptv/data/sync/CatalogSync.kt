package nl.jordy.iptv.data.sync

import androidx.room.withTransaction
import kotlinx.serialization.json.JsonElement
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.db.LiveChannelEntity
import nl.jordy.iptv.data.db.MovieEntity
import nl.jordy.iptv.data.db.SearchEntity
import nl.jordy.iptv.data.db.SeriesEntity
import nl.jordy.iptv.data.db.SyncInfoEntity
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.NameParser
import nl.jordy.iptv.data.xtream.ParsedName
import nl.jordy.iptv.data.xtream.XtreamClient
import nl.jordy.iptv.data.xtream.XtreamCredentials
import nl.jordy.iptv.data.xtream.XtreamParser
import javax.inject.Inject
import javax.inject.Singleton

data class SyncProgress(val type: ContentType, val items: Int)

/**
 * Pulls categories and the full Live/VOD/Series catalog into Room. Items are decoded one by one
 * from the response and written in batches, so a 10k+ catalog never sits in memory as JSON.
 * Names are cleaned here ([NameParser]) once, so screens and search never parse provider names.
 */
@Singleton
class CatalogSync @Inject constructor(
    private val client: XtreamClient,
    private val db: AppDatabase,
) {
    private val dao = db.catalog()

    /** What the item step needs to know about one parsed item. */
    private class Item<E>(val entity: E, val id: Int, val title: String, val country: String?)

    suspend fun run(c: XtreamCredentials, onProgress: (SyncProgress) -> Unit = {}) {
        val generation = System.currentTimeMillis()

        sync(c, ContentType.LIVE, "get_live_categories", "get_live_streams", generation, onProgress,
            parse = XtreamParser::liveStream, category = { it.categoryId }, rawName = { it.name },
            toItem = { it, n, cat, i ->
                Item(LiveChannelEntity(cat, it.streamId, n.title, it.logoUrl, it.epgChannelId, i, generation, n.badges.joinToString(","), n.catchup, it.archiveDays), it.streamId, n.title, n.country)
            },
            write = dao::upsertLive, existing = dao::liveCount, deleteOld = dao::deleteOldLive)

        sync(c, ContentType.MOVIE, "get_vod_categories", "get_vod_streams", generation, onProgress,
            parse = XtreamParser::movie, category = { it.categoryId }, rawName = { it.name },
            toItem = { it, n, cat, i ->
                Item(MovieEntity(cat, it.streamId, n.title, it.posterUrl, it.rating, it.year, it.containerExtension, it.addedAt, i, generation, n.badges.joinToString(","), it.tmdbId), it.streamId, n.title, n.country)
            },
            write = dao::upsertMovies, existing = dao::movieCount, deleteOld = dao::deleteOldMovies)

        sync(c, ContentType.SERIES, "get_series_categories", "get_series", generation, onProgress,
            parse = XtreamParser::series, category = { it.categoryId }, rawName = { it.name },
            toItem = { it, n, cat, i ->
                Item(SeriesEntity(cat, it.seriesId, n.title, it.coverUrl, it.backdropUrl, it.plot, it.genre, it.rating, it.year, i, generation, n.badges.joinToString(",")), it.seriesId, n.title, n.country)
            },
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
        rawName: (M) -> String,
        toItem: (M, ParsedName, String, Int) -> Item<E>,
        write: suspend (List<E>) -> Unit,
        existing: suspend () -> Int,
        deleteOld: suspend (Long) -> Unit,
    ) {
        val categories = mutableListOf<CategoryEntity>()
        client.forEachInArray(c, categoriesAction) { el ->
            XtreamParser.category(el)?.let {
                val n = NameParser.parse(it.name, stripQuality = false)
                // "APPLE TV+ · Multi": the badge stays readable next to the name.
                val name = (listOf(n.title) + n.badges).joinToString(" · ")
                categories += CategoryEntity(type, it.id, name, categories.size, generation, n.country, adult = NameParser.isAdult(it.name))
            }
        }
        val categoryCountry = categories.associate { it.id to it.country }

        var count = 0
        var uncategorized = false
        val batch = ArrayList<E>(BATCH_SIZE)
        val searchBatch = ArrayList<SearchEntity>(BATCH_SIZE)
        // The search index is rebuilt per type; a few seconds of partial results mid-sync are fine.
        db.search().deleteType(type.name)
        suspend fun flush() {
            if (batch.isEmpty()) return
            db.withTransaction {
                write(batch)
                db.search().insert(searchBatch)
            }
            batch.clear()
            searchBatch.clear()
            onProgress(SyncProgress(type, count))
        }
        onProgress(SyncProgress(type, 0))
        client.forEachInArray(c, itemsAction) { el ->
            val m = parse(el) ?: return@forEachInArray
            val cat = category(m) ?: UNCATEGORIZED.also { uncategorized = true }
            val item = toItem(m, NameParser.parse(rawName(m), keepHd = type == ContentType.LIVE), cat, count++)
            batch += item.entity
            // An item's own tag ("┃NL┃ Film" in an untagged category) beats its category's country.
            searchBatch += SearchEntity(item.title, type.name, item.id, cat, item.country ?: categoryCountry[cat])
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
