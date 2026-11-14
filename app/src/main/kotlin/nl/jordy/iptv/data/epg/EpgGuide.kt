package nl.jordy.iptv.data.epg

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.EpgChannelEntity
import nl.jordy.iptv.data.db.EpgInfoEntity
import nl.jordy.iptv.data.db.EpgProgrammeEntity
import nl.jordy.iptv.data.db.LiveGuideRow
import nl.jordy.iptv.data.profile.ProfileRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Pure parts of the guide import, testable without a network or database. */
object XmltvGuide {

    /**
     * Streams an XMLTV document and keeps the programmes of [wanted] channels that overlap
     * [from]..[to] (epoch seconds). Broken programmes are skipped, never fatal.
     */
    fun read(input: InputStream, wanted: Set<String>, from: Long, to: Long): List<EpgProgrammeEntity> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }
        val out = ArrayList<EpgProgrammeEntity>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "programme") continue
            val channel = parser.getAttributeValue(null, "channel")
            val start = XmltvCounter.parseTime(parser.getAttributeValue(null, "start"))
            val stop = XmltvCounter.parseTime(parser.getAttributeValue(null, "stop"))
            val keep = channel != null && channel in wanted && start != null && stop != null && stop > start && stop > from && start < to
            var title: String? = null
            var desc: String? = null
            var subtitle: String? = null
            var episode: String? = null
            var year: String? = null
            var country: String? = null
            var director: String? = null
            val genres = ArrayList<String>()
            val cast = ArrayList<String>()
            // Read (or skip) the children up to </programme>.
            while (!(parser.next() == XmlPullParser.END_TAG && parser.name == "programme")) {
                if (parser.eventType == XmlPullParser.END_DOCUMENT) break
                if (keep && parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "title" -> if (title == null) title = parser.nextText().trim()
                        "desc" -> if (desc == null) desc = parser.nextText().trim()
                        "sub-title" -> if (subtitle == null) subtitle = parser.nextText().trim()
                        "episode-num" -> {
                            val system = parser.getAttributeValue(null, "system")
                            val found = episodeLabel(system, parser.nextText())
                            // "onscreen" says it as people do; xmltv_ns only when that's all there is.
                            if (found != null && (episode == null || system == "onscreen")) episode = found
                        }
                        "date" -> if (year == null) year = parser.nextText().trim().take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
                        "country" -> if (country == null) country = parser.nextText().trim()
                        "category" -> parser.nextText().trim().let { g -> if (g.isNotEmpty() && genres.size < 2 && genres.none { it.equals(g, true) }) genres += g }
                        "director" -> if (director == null) director = parser.nextText().trim().trim('"')
                        "actor", "presenter" -> parser.nextText().trim().trim('"').let { if (it.isNotEmpty() && cast.size < 4 && it !in cast) cast += it }
                    }
                }
            }
            if (keep && !title.isNullOrBlank()) {
                val t: String = title
                val d = desc?.ifBlank { null }
                // Some guides put the episode title in the description: say it once.
                val sub = subtitle?.ifBlank { null }?.takeUnless { it.equals(t, true) }
                out += EpgProgrammeEntity(
                    channel!!, start!!, stop!!, t, d?.takeUnless { it.equals(t, true) || it.equals(sub, true) },
                    subtitle = sub,
                    episode = episode,
                    year = year,
                    genres = genres.joinToString(", ").ifEmpty { null },
                    country = country?.ifBlank { null },
                    director = director?.ifBlank { null },
                    cast = cast.joinToString(", ").ifEmpty { null },
                )
            }
        }
        // Some guides list a programme twice; the table's key is (channel, start).
        return out.distinctBy { it.channelId to it.start }
    }

    /**
     * "S16 E13" from an XMLTV episode number: "onscreen" as written ("S16 E13", "S1"), xmltv_ns
     * counted from zero ("15.12.0" -> "S16 E13", "0.19/20.0" -> "S1 E20"). Anything else: null.
     */
    fun episodeLabel(system: String?, raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (system == "xmltv_ns") {
            val parts = text.split('.')
            fun part(i: Int) = parts.getOrNull(i)?.substringBefore('/')?.trim()?.toIntOrNull()?.plus(1)
            val season = part(0)
            val ep = part(1)
            return listOfNotNull(season?.let { "S$it" }, ep?.let { "E$it" }).joinToString(" ").ifEmpty { null }
        }
        // onscreen and unnamed systems: only what reads like an episode, not a production code.
        return text.takeIf { Regex("""^S\d+(\s*E\d+)?$""", RegexOption.IGNORE_CASE).matches(it) }?.uppercase()
    }

    /**
     * Whether to fetch again: after 20 h, or on the first chance after 18:30 when the last fetch
     * was before it, since the open guides publish the next days around 18:00.
     */
    fun isStale(lastMs: Long, nowMs: Long, zone: ZoneId): Boolean {
        if (nowMs - lastMs > STALE_AFTER_MS) return true
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val published = now.toLocalDate().atTime(PUBLISHED).atZone(zone).toInstant().toEpochMilli()
        return nowMs >= published && lastMs < published
    }

    private const val STALE_AFTER_MS = 20 * 60 * 60 * 1000L
    private val PUBLISHED = LocalTime.of(18, 30)

    /**
     * The guide channel per stream: its own EPG id, else that of a stream with the same cleaned
     * name in the same country. Providers carry "NPO 1", "NPO 1 HD" and "NPO 1 4K" as separate
     * streams and often give only one of them an id.
     */
    fun channels(rows: List<LiveGuideRow>): List<EpgChannelEntity> {
        fun key(r: LiveGuideRow) = r.country to r.name.trim().lowercase()
        val idByName = rows.filter { !it.epgChannelId.isNullOrBlank() }
            .groupBy(::key)
            .mapValues { (_, v) -> v.first().epgChannelId!! }
        return rows.mapNotNull { r ->
            val id = r.epgChannelId?.takeIf { it.isNotBlank() } ?: idByName[key(r)]
            id?.let { EpgChannelEntity(r.streamId, it) }
        }
    }
}

/**
 * Keeps the full guide in Room: the provider's merged with the open guides in [GuideSources],
 * fetched in quiet moments when [XmltvGuide.isStale], or on request from Instellingen. Runs in
 * the background; the screens fall back to the per-channel short EPG until it's there.
 */
@Singleton
class EpgGuide @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val credentials: CredentialStore,
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
) {
    sealed interface Status {
        data object Idle : Status
        data object Running : Status
        data class Failed(val reason: String) : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var lastAttempt = 0L
    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status = _status.asStateFlow()
    val info = db.epg().observeInfo()

    /**
     * Called whenever the tv has a quiet moment: the app comes to the front, the screensaver
     * starts. Cheap when the guide is fresh; a failed attempt waits an hour before the next.
     */
    fun refreshIfStale() {
        scope.launch {
            val now = System.currentTimeMillis()
            if (now - lastAttempt < RETRY_AFTER_MS || credentials.current() == null) return@launch
            val last = db.epg().info()?.refreshedAt ?: 0
            if (XmltvGuide.isStale(last, now, ZoneId.systemDefault())) refresh()
        }
    }

    /** Starts a refresh unless one is running. */
    @Synchronized
    fun refresh() {
        if (job?.isActive == true) return
        lastAttempt = System.currentTimeMillis()
        job = scope.launch {
            _status.value = Status.Running
            _status.value = runCatching { download() }.fold({ Status.Idle }, { Status.Failed(it.javaClass.simpleName) })
        }
    }

    private suspend fun download() {
        val c = credentials.current() ?: error("not logged in")
        // Union over profiles: one guide serves the whole household.
        val all = profiles.all()
        val countries = all.flatMap { p -> p.preferredCountries.split(',') }.filter { it.isNotBlank() }.toSet()
        // Pinned live categories and the channels in everyone's guides too, so reminders can find what's on them.
        val pinned = all.flatMap { p -> p.pinnedCategories.split(',') }.filter { it.startsWith("LIVE:") }.map { it.removePrefix("LIVE:") }.distinct()
        val favourites = db.guides().allChannelIds()
        val rows = db.catalog().liveForGuide(countries.toList(), pinned, favourites)
        val providerIds = XmltvGuide.channels(rows).associate { it.streamId to it.channelId }
        val wanted = providerIds.values.toSet()

        val url = c.baseUrl?.newBuilder()?.addPathSegment("xmltv.php")
            ?.addQueryParameter("username", c.username)?.addQueryParameter("password", c.password)?.build()
            ?: error("no server")
        val now = System.currentTimeMillis() / 1000
        val from = now - WINDOW_BEFORE_SEC
        val to = now + WINDOW_AFTER_SEC
        // The open guides first: one that fails or is down is skipped, the provider's still counts.
        val external = GuideSources.external.map { source ->
            runCatching { readSource(source, rows, providerIds, from, to) }
                .onFailure { Log.w(TAG, "guide ${source.name}: ${it.javaClass.simpleName}") }
                .getOrDefault(emptyList())
        }
        val provider = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                XmltvGuide.read(resp.body.byteStream(), wanted, from, to)
            }
        }
        if (provider.isFailure && external.all { it.isEmpty() }) throw provider.exceptionOrNull()!!
        val programmes = GuideSources.merge(external + listOf(provider.getOrDefault(emptyList())))

        // Swap in one go, so a channel list never shows half a guide.
        val withGuide = programmes.map { it.channelId }.toSet()
        val channels = rows.map { EpgChannelEntity(it.streamId, GuideSources.streamKey(it, providerIds[it.streamId])) }
            .filter { it.channelId in withGuide }
        db.withTransaction {
            val dao = db.epg()
            dao.clearProgrammes()
            dao.clearChannels()
            programmes.chunked(500).forEach { dao.insertProgrammes(it) }
            channels.chunked(500).forEach { dao.insertChannels(it) }
            dao.upsertInfo(EpgInfoEntity(refreshedAt = System.currentTimeMillis(), streams = channels.size))
        }
    }

    /**
     * One open guide, its programmes filed under our guide keys. Saved to a file first: the
     * channel list is read before the programmes, and a .gz is unpacked on the way.
     */
    private fun readSource(source: GuideSource, rows: List<LiveGuideRow>, providerIds: Map<Int, String>, from: Long, to: Long): List<EpgProgrammeEntity> {
        val file = File(context.cacheDir, "guide-${source.name}.xml")
        http.newCall(Request.Builder().url(source.url).build()).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            val body = resp.body.byteStream().let { if (source.url.endsWith(".gz")) GZIPInputStream(it) else it }
            file.outputStream().use { out -> body.copyTo(out) }
        }
        try {
            val keys = file.inputStream().buffered().use { GuideSources.match(rows, providerIds, GuideSources.channelNames(it)) }
            if (keys.isEmpty()) return emptyList()
            val programmes = file.inputStream().buffered().use { XmltvGuide.read(it, keys.keys, from, to) }
            return programmes.flatMap { p -> keys.getValue(p.channelId).map { p.copy(channelId = it) } }
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val TAG = "EpgGuide"
        const val RETRY_AFTER_MS = 60 * 60 * 1000L
        const val WINDOW_BEFORE_SEC = 2 * 3600L
        const val WINDOW_AFTER_SEC = 36 * 3600L
    }
}
