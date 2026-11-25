package nl.jordy.iptv.data.sync

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.CatalogKey
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
import nl.jordy.iptv.data.xtream.int
import nl.jordy.iptv.data.xtream.str
import javax.inject.Inject
import javax.inject.Singleton

data class SyncProgress(val type: ContentType, val items: Int)

/** Rows written (new or changed) and removed per type in one sync. */
data class SyncResult(val changed: Map<ContentType, Int>, val removed: Map<ContentType, Int>) {
    fun touched(type: ContentType) = (changed[type] ?: 0) + (removed[type] ?: 0) > 0
}

/**
 * Pulls categories and the full Live/VOD/Series catalog into Room. Items are decoded one by one
 * from the response, so a 10k+ catalog never sits in memory as JSON.
 *
 * The provider has no "changed since", so the whole list comes in every time, but only what
 * changed is worked on: each row keeps a fingerprint of its provider entry, and an entry with the
 * same fingerprint skips name cleaning ([NameParser], the slow part on the tv), the write and the
 * search index. A new app install cleans every name again, since the parser may have changed.
 */
@Singleton
class CatalogSync @Inject constructor(
    private val client: XtreamClient,
    private val db: AppDatabase,
    @ApplicationContext context: Context,
) {
    private val dao = db.catalog()
    private val lock = Mutex()

    /** Changes with every install of the app, so a new parser re-cleans the whole catalog once. */
    private val salt: Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    }.getOrDefault(0L)

    val isRunning get() = lock.isLocked

    /** What the item step needs to know about one parsed item. */
    private class Item<E>(val entity: E, val id: Int, val title: String, val country: String?)

    /** Runs one sync; a second caller waits for the running one and then syncs again (cheap: nothing changed). */
    suspend fun run(c: XtreamCredentials, onProgress: (SyncProgress) -> Unit = {}): SyncResult = lock.withLock {
        val generation = System.currentTimeMillis()
        val rebuild = db.syncInfo().get()?.salt != salt
        val changed = mutableMapOf<ContentType, Int>()
        val removed = mutableMapOf<ContentType, Int>()
        fun record(type: ContentType, r: Pair<Int, Int>) { changed[type] = r.first; removed[type] = r.second }

        record(ContentType.LIVE, sync(c, ContentType.LIVE, "get_live_categories", "get_live_streams", "stream_id", generation, rebuild, onProgress,
            parse = XtreamParser::liveStream, rawName = { it.name },
            toItem = { it, n, cat, i, hash ->
                Item(LiveChannelEntity(cat, it.streamId, n.title, it.logoUrl, it.epgChannelId, i, generation, n.badges.joinToString(","), n.catchup, it.archiveDays, hash), it.streamId, n.title, n.country)
            },
            write = dao::upsertLive, keys = dao::liveKeys, move = dao::moveLive, delete = dao::deleteLive))

        record(ContentType.MOVIE, sync(c, ContentType.MOVIE, "get_vod_categories", "get_vod_streams", "stream_id", generation, rebuild, onProgress,
            parse = XtreamParser::movie, rawName = { it.name },
            toItem = { it, n, cat, i, hash ->
                Item(MovieEntity(cat, it.streamId, n.title, it.posterUrl, it.rating, it.year, it.containerExtension, it.addedAt, i, generation, n.badges.joinToString(","), it.tmdbId, hash), it.streamId, n.title, n.country)
            },
            write = dao::upsertMovies, keys = dao::movieKeys, move = dao::moveMovie, delete = dao::deleteMovie))

        record(ContentType.SERIES, sync(c, ContentType.SERIES, "get_series_categories", "get_series", "series_id", generation, rebuild, onProgress,
            parse = XtreamParser::series, rawName = { it.name },
            toItem = { it, n, cat, i, hash ->
                Item(SeriesEntity(cat, it.seriesId, n.title, it.coverUrl, it.backdropUrl, it.plot, it.genre, it.rating, it.year, i, generation, n.badges.joinToString(","), hash), it.seriesId, n.title, n.country)
            },
            write = dao::upsertSeries, keys = dao::seriesKeys, move = dao::moveSeries, delete = dao::deleteSeries))

        db.syncInfo().upsert(SyncInfoEntity(lastSyncAt = System.currentTimeMillis(), generation = generation, salt = salt))
        SyncResult(changed, removed)
    }

    /** One type; returns (rows written, rows removed). */
    private suspend fun <M, E> sync(
        c: XtreamCredentials,
        type: ContentType,
        categoriesAction: String,
        itemsAction: String,
        idField: String,
        generation: Long,
        rebuild: Boolean,
        onProgress: (SyncProgress) -> Unit,
        parse: (JsonElement) -> M?,
        rawName: (M) -> String,
        toItem: (M, ParsedName, String, Int, Long) -> Item<E>,
        write: suspend (List<E>) -> Unit,
        keys: suspend () -> List<CatalogKey>,
        move: suspend (String, Int, Int) -> Unit,
        delete: suspend (String, Int) -> Unit,
    ): Pair<Int, Int> {
        val categories = mutableListOf<CategoryEntity>()
        client.forEachInArray(c, categoriesAction) { el ->
            XtreamParser.category(el)?.let {
                // "ODIDO FHD 50FPS" → "ODIDO" with its labels, drawn next to the name; HD stays, it tells categories apart.
                val n = NameParser.parse(it.name, keepHd = true)
                categories += CategoryEntity(type, it.id, n.title, categories.size, generation, n.country, adult = NameParser.isAdult(it.name), badges = n.badges.joinToString(","))
            }
        }
        val categoryCountry = categories.associate { it.id to it.country }

        // What the catalog holds now; whatever is left in here after the list is gone at the provider.
        val existing = HashMap<String, CatalogKey>()
        keys().forEach { existing[key(it.categoryId, it.id)] = it }
        val hadRows = existing.isNotEmpty()
        val seen = HashSet<String>()

        var count = 0
        var written = 0
        var uncategorized = false
        val batch = ArrayList<E>(BATCH_SIZE)
        val searchBatch = ArrayList<SearchEntity>(BATCH_SIZE)
        val stale = ArrayList<Pair<String, Int>>() // changed rows whose old search entry goes
        val moves = ArrayList<CatalogKey>()
        // A rebuild redoes the type's search index whole; otherwise only changed rows touch it.
        if (rebuild) db.search().deleteType(type.name)
        suspend fun flush() {
            if (batch.isEmpty() && moves.isEmpty()) return
            db.withTransaction {
                if (stale.isNotEmpty()) dropSearch(type, stale)
                write(batch)
                db.search().insert(searchBatch)
                moves.forEach { move(it.categoryId, it.id, it.sortIndex) }
            }
            batch.clear()
            searchBatch.clear()
            stale.clear()
            moves.clear()
        }
        onProgress(SyncProgress(type, 0))
        client.forEachInArray(c, itemsAction) { el ->
            val o = el as? JsonObject ?: return@forEachInArray
            val id = o.int(idField) ?: return@forEachInArray
            val cat = o.str("category_id") ?: UNCATEGORIZED.also { uncategorized = true }
            val k = key(cat, id)
            // A provider that lists the same item twice in one category: the first one counts.
            if (!seen.add(k)) return@forEachInArray
            val index = count++
            if (count % BATCH_SIZE == 0) onProgress(SyncProgress(type, count))
            val hash = fingerprint(o, categoryCountry[cat])
            val old = existing.remove(k)
            if (!rebuild && old != null && old.hash == hash) {
                if (old.sortIndex != index) moves += old.copy(sortIndex = index)
                if (moves.size >= BATCH_SIZE) flush()
                return@forEachInArray
            }
            val m = parse(el) ?: return@forEachInArray
            val item = toItem(m, NameParser.parse(rawName(m), keepHd = type == ContentType.LIVE), cat, index, hash)
            batch += item.entity
            if (old != null && !rebuild) stale += cat to id
            // An item's own tag ("┃NL┃ Film" in an untagged category) beats its category's country.
            searchBatch += SearchEntity(item.title, type.name, item.id, cat, item.country ?: categoryCountry[cat])
            written++
            if (batch.size >= BATCH_SIZE) flush()
        }
        flush()
        onProgress(SyncProgress(type, count))

        if (uncategorized) categories += CategoryEntity(type, UNCATEGORIZED, "Overig", Int.MAX_VALUE, generation)

        // A provider hiccup that returns an empty list must not wipe a catalog that was fine yesterday.
        if (count == 0 && hadRows) return written to 0
        val gone = existing.values.map { it.categoryId to it.id }
        db.withTransaction {
            dao.upsertCategories(categories)
            dao.deleteOldCategories(type, generation)
            gone.forEach { (cat, id) -> delete(cat, id) }
            if (!rebuild) gone.chunked(BATCH_SIZE).forEach { dropSearch(type, it) }
        }
        return written to gone.size
    }

    /** Removes the search entries of these (category, id) rows; an item's entries in other categories stay. */
    private suspend fun dropSearch(type: ContentType, rows: List<Pair<String, Int>>) {
        val wanted = rows.toHashSet()
        val refs = db.search().refs(type.name, rows.map { it.second }.distinct())
        val rowids = refs.filter { (it.categoryId to it.itemId) in wanted }.map { it.rowid }
        rowids.chunked(BATCH_SIZE).forEach { db.search().deleteRows(it) }
    }

    companion object {
        const val BATCH_SIZE = 500
        const val UNCATEGORIZED = ""

        private fun key(categoryId: String, id: Int) = "$categoryId\u0001$id"

        /**
         * FNV-1a over the provider's entry, without `num` (its place in the list: one new film near
         * the top would otherwise change every entry after it), plus the category's country, which
         * the search index takes over.
         */
        internal fun fingerprint(o: JsonObject, categoryCountry: String?): Long {
            var h = -0x340d631b7bdddcdbL // FNV offset basis
            fun feed(s: String) {
                for (ch in s) { h = (h xor ch.code.toLong()) * 0x100000001b3L }
                h = (h xor 0x1fL) * 0x100000001b3L // separator, so "ab"+"c" differs from "a"+"bc"
            }
            for ((k, v) in o) {
                if (k == "num") continue
                feed(k)
                feed(v.toString())
            }
            feed(categoryCountry.orEmpty())
            return h
        }
    }
}
