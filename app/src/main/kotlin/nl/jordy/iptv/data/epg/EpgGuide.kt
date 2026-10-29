package nl.jordy.iptv.data.epg

import android.util.Xml
import androidx.room.withTransaction
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
import java.io.InputStream
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
            // Read (or skip) the children up to </programme>.
            while (!(parser.next() == XmlPullParser.END_TAG && parser.name == "programme")) {
                if (parser.eventType == XmlPullParser.END_DOCUMENT) break
                if (keep && parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "title" -> if (title == null) title = parser.nextText().trim()
                        "desc" -> if (desc == null) desc = parser.nextText().trim()
                    }
                }
            }
            if (keep && !title.isNullOrBlank()) out += EpgProgrammeEntity(channel!!, start!!, stop!!, title, desc?.ifBlank { null })
        }
        // Some guides list a programme twice; the table's key is (channel, start).
        return out.distinctBy { it.channelId to it.start }
    }

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
 * Keeps the full guide in Room: downloaded on app start when older than [STALE_AFTER_MS], or
 * on request from Instellingen. Runs in the background; the screens fall back to the
 * per-channel short EPG until it's there.
 */
@Singleton
class EpgGuide @Inject constructor(
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
    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status = _status.asStateFlow()
    val info = db.epg().observeInfo()

    fun refreshIfStale() {
        scope.launch {
            val last = db.epg().info()?.refreshedAt ?: 0
            if (System.currentTimeMillis() - last > STALE_AFTER_MS) refresh()
        }
    }

    /** Starts a refresh unless one is running. */
    @Synchronized
    fun refresh() {
        if (job?.isActive == true) return
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
        // Pinned live categories and favourite channels too, so reminders can find what's on them.
        val pinned = all.flatMap { p -> p.pinnedCategories.split(',') }.filter { it.startsWith("LIVE:") }.map { it.removePrefix("LIVE:") }.distinct()
        val favourites = db.favorites().allLiveIds()
        val channels = XmltvGuide.channels(db.catalog().liveForGuide(countries.toList(), pinned, favourites))
        val wanted = channels.map { it.channelId }.toSet()

        val url = c.baseUrl?.newBuilder()?.addPathSegment("xmltv.php")
            ?.addQueryParameter("username", c.username)?.addQueryParameter("password", c.password)?.build()
            ?: error("no server")
        val now = System.currentTimeMillis() / 1000
        val programmes = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            XmltvGuide.read(resp.body.byteStream(), wanted, from = now - WINDOW_BEFORE_SEC, to = now + WINDOW_AFTER_SEC)
        }

        // Swap in one go, so a channel list never shows half a guide.
        val withGuide = programmes.map { it.channelId }.toSet()
        db.withTransaction {
            val dao = db.epg()
            dao.clearProgrammes()
            dao.clearChannels()
            programmes.chunked(500).forEach { dao.insertProgrammes(it) }
            channels.filter { it.channelId in withGuide }.chunked(500).forEach { dao.insertChannels(it) }
            dao.upsertInfo(EpgInfoEntity(refreshedAt = System.currentTimeMillis(), streams = channels.count { it.channelId in withGuide }))
        }
    }

    private companion object {
        const val STALE_AFTER_MS = 20 * 60 * 60 * 1000L
        const val WINDOW_BEFORE_SEC = 2 * 3600L
        const val WINDOW_AFTER_SEC = 36 * 3600L
    }
}
