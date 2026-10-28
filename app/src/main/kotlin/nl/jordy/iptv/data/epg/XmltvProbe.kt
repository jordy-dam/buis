package nl.jordy.iptv.data.epg

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.jordy.iptv.data.auth.CredentialStore
import nl.jordy.iptv.data.db.AppDatabase
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class XmltvStats(
    val bytes: Long,
    val seconds: Long,
    val channels: Int,
    val programmes: Int,
    /** Channel ids with at least one programme in the next 24 hours. */
    val channelsWithGuide: Set<String>,
)

/**
 * Streams an XMLTV document and counts what's in it, without keeping it in memory: provider
 * guides run to tens of megabytes. Pure parsing, so it's testable without a network.
 */
object XmltvCounter {
    private val xmltvTime = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun count(input: InputStream, nowEpochSec: Long): Triple<Int, Int, Set<String>> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }
        var channels = 0
        var programmes = 0
        val withGuide = HashSet<String>()
        val horizon = nowEpochSec + 24 * 3600
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "channel" -> channels++
                "programme" -> {
                    programmes++
                    val stop = parseTime(parser.getAttributeValue(null, "stop"))
                    val start = parseTime(parser.getAttributeValue(null, "start"))
                    if (stop != null && start != null && stop > nowEpochSec && start < horizon) {
                        parser.getAttributeValue(null, "channel")?.let(withGuide::add)
                    }
                }
            }
        }
        return Triple(channels, programmes, withGuide)
    }

    /** "20261028193000 +0100" → epoch seconds; null when unreadable. */
    internal fun parseTime(raw: String?): Long? {
        if (raw == null || raw.length < 14) return null
        return runCatching {
            val local = LocalDateTime.parse(raw.substring(0, 14), xmltvTime)
            val tz = raw.substring(14).trim()
            val offset = if (tz.length == 5) ZoneOffset.of(tz.substring(0, 3) + ":" + tz.substring(3)) else ZoneOffset.UTC
            local.toEpochSecond(offset)
        }.getOrNull()
    }
}

/** Debug-only check of the provider's full guide; decides how phase 2a does the EPG. */
@Singleton
class XmltvProbe @Inject constructor(
    private val http: OkHttpClient,
    private val credentials: CredentialStore,
    private val db: AppDatabase,
) {
    /** Human-readable summary for the settings screen. Never contains the credentials. */
    suspend fun run(preferredCountries: Set<String>): String = withContext(Dispatchers.IO) {
        val c = credentials.current() ?: return@withContext "Niet ingelogd."
        val url = c.baseUrl?.newBuilder()?.addPathSegment("xmltv.php")
            ?.addQueryParameter("username", c.username)?.addQueryParameter("password", c.password)?.build()
            ?: return@withContext "Ongeldige server."
        val started = System.currentTimeMillis()
        val stats = try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext "Gids niet beschikbaar (HTTP ${resp.code})."
                val counting = CountingStream(resp.body.byteStream())
                val (channels, programmes, withGuide) = XmltvCounter.count(counting, System.currentTimeMillis() / 1000)
                XmltvStats(counting.count, (System.currentTimeMillis() - started) / 1000, channels, programmes, withGuide)
            }
        } catch (e: Exception) {
            return@withContext "Gids ophalen mislukt: ${e.javaClass.simpleName}"
        }

        val mine = db.catalog().liveEpgIds(preferredCountries.toList())
        val withId = mine.count { it != null }
        val covered = mine.count { it != null && it in stats.channelsWithGuide }
        "Gids: ${stats.bytes / 1_048_576} MB in ${stats.seconds}s, ${stats.channels} zenders, ${stats.programmes} programma's. " +
            "Voorkeurszenders: ${mine.size}, met EPG-id $withId, met programma's komende 24u $covered."
    }

    private class CountingStream(private val inner: InputStream) : InputStream() {
        var count = 0L
        override fun read(): Int = inner.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) count += it }
        override fun close() = inner.close()
    }
}
