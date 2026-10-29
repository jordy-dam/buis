package nl.jordy.iptv.data.xtream

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the user typed on the login screen. Never logged: the password is part of every stream URL. */
@Serializable
data class XtreamCredentials(val serverUrl: String, val username: String, val password: String) {
    override fun toString() = "XtreamCredentials(serverUrl=$serverUrl, username=***, password=***)"

    val baseUrl: HttpUrl? get() = normalizeServerUrl(serverUrl)
}

/**
 * Accepts what people paste: "host:port", "http://host:port/", or a full ".../player_api.php?..." link.
 * Returns the server root, or null when it can't be a URL.
 */
fun normalizeServerUrl(input: String): HttpUrl? {
    var s = input.trim()
    if (s.isEmpty()) return null
    if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) s = "http://$s"
    val url = s.toHttpUrlOrNull() ?: return null
    val segments = url.pathSegments.filter { it.isNotEmpty() && !it.endsWith(".php") }
    return url.newBuilder()
        .query(null)
        .fragment(null)
        .encodedPath("/")
        .apply { segments.forEach { addPathSegment(it) } }
        .build()
}

object StreamUrls {
    fun liveHls(base: HttpUrl, c: XtreamCredentials, streamId: Int) = stream(base, "live", c, "$streamId.m3u8")
    fun liveTs(base: HttpUrl, c: XtreamCredentials, streamId: Int) = stream(base, "live", c, "$streamId.ts")
    fun movie(base: HttpUrl, c: XtreamCredentials, streamId: Int, ext: String) = stream(base, "movie", c, "$streamId.${ext.ifBlank { "mp4" }}")
    fun episode(base: HttpUrl, c: XtreamCredentials, episodeId: Int, ext: String) = stream(base, "series", c, "$episodeId.${ext.ifBlank { "mp4" }}")

    /**
     * A recorded stretch of a channel, the Xtream way: `timeshift/<user>/<pass>/<minutes>/<start>/<id>.ts`,
     * with the start as `yyyy-MM-dd:HH-mm` in the panel's own time zone (not the tv's).
     */
    fun timeshift(base: HttpUrl, c: XtreamCredentials, streamId: Int, startEpochSec: Long, minutes: Int, zone: ZoneId): String =
        base.newBuilder()
            .addPathSegment("timeshift").addPathSegment(c.username).addPathSegment(c.password)
            .addPathSegment("$minutes")
            .addPathSegment(timeshiftStart.withZone(zone).format(Instant.ofEpochSecond(startEpochSec)))
            .addPathSegment("$streamId.ts")
            .build().toString()

    private val timeshiftStart = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")

    private fun stream(base: HttpUrl, kind: String, c: XtreamCredentials, file: String): String =
        base.newBuilder().addPathSegment(kind).addPathSegment(c.username).addPathSegment(c.password).addPathSegment(file).build().toString()
}
