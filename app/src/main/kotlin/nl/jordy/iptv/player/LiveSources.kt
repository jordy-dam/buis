package nl.jordy.iptv.player

import nl.jordy.iptv.data.xtream.StreamUrls
import nl.jordy.iptv.data.xtream.XtreamCredentials
import okhttp3.HttpUrl

/**
 * Picks the URL for a live channel: HLS first (starts fastest), raw MPEG-TS when HLS fails.
 * Channels whose HLS failed once go straight to TS for the rest of the session, so zapping back
 * to them doesn't pay for the failed attempt again.
 */
class LiveSources(private val base: HttpUrl, private val credentials: XtreamCredentials) {

    data class Source(val url: String, val isHls: Boolean)

    private val hlsBroken = mutableSetOf<Int>()

    fun first(streamId: Int): Source =
        if (streamId in hlsBroken) ts(streamId) else Source(StreamUrls.liveHls(base, credentials, streamId), isHls = true)

    /** Next source to try after a playback error, or null when there's nothing left to try. */
    fun fallback(streamId: Int, failed: Source): Source? {
        if (!failed.isHls) return null
        hlsBroken += streamId
        return ts(streamId)
    }

    private fun ts(streamId: Int) = Source(StreamUrls.liveTs(base, credentials, streamId), isHls = false)
}
