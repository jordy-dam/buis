package nl.jordy.iptv.data.epg

import android.util.Xml
import nl.jordy.iptv.data.db.EpgProgrammeEntity
import nl.jordy.iptv.data.db.LiveGuideRow
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/** A guide besides the provider's: a free XMLTV file for Dutch channels. */
data class GuideSource(val name: String, val url: String)

/**
 * Pure parts of merging several guides into one, testable without a network or database.
 *
 * Every stream gets a guide key: its provider EPG id (own or borrowed, see [XmltvGuide.channels]),
 * else `naam:` plus its [nameKey]. Each source's channels are matched to those keys by id
 * (ignoring case) or by name, and per key the sources are merged in order of preference.
 */
object GuideSources {

    /**
     * In order of preference, before the provider's own guide. Both refresh once a day around
     * 18:00 CET; open-epg allows 20 downloads a day. On 2026-10-30 both had SBS6's late-night
     * change the provider missed.
     */
    val external = listOf(
        GuideSource("open-epg", "https://www.open-epg.com/files/netherlands.xml"),
        GuideSource("epgshare01", "https://epgshare01.online/epgshare01/epg_ripper_NL1.xml.gz"),
    )

    private val quality = Regex("\\b(hd|fhd|uhd|sd|4k|8k|hevc|h265|backup)\\b")
    private val nonAlnum = Regex("[^\\p{L}\\p{N}]")

    /** "Viaplay TV+ HD" → "viaplaytvplus": what two guides call the same channel. */
    fun nameKey(name: String): String =
        name.lowercase().replace("+", " plus ").replace(quality, " ").replace(nonAlnum, "")

    fun streamKey(row: LiveGuideRow, providerId: String?): String = providerId ?: "naam:${nameKey(row.name)}"

    /** "Viaplay.TV.nl" → "Viaplay.TV": a source id without its country suffix, as a name. */
    private fun idStem(id: String): String = id.replace(Regex("\\.[a-z]{2}$", RegexOption.IGNORE_CASE), "")

    /**
     * Which guide keys each source channel feeds. [rows] are the streams the guide covers and
     * [providerIds] their provider EPG ids by stream. Matching by name only uses Dutch streams
     * and streams without a country (pinned packs), since the sources are Dutch guides.
     */
    fun match(rows: List<LiveGuideRow>, providerIds: Map<Int, String>, sourceChannels: Map<String, List<String>>): Map<String, Set<String>> {
        val byId = providerIds.values.associateBy { it.lowercase() }
        val byName = rows.filter { it.country == null || it.country == "NL" }
            .groupBy({ nameKey(it.name) }, { streamKey(it, providerIds[it.streamId]) })
            .filterKeys { it.isNotEmpty() }
        val out = HashMap<String, Set<String>>()
        for ((id, names) in sourceChannels) {
            val keys = byId[id.lowercase()]?.let { setOf(it) }
                ?: (names + idStem(id)).flatMap { byName[nameKey(it)].orEmpty() }.toSet()
            if (keys.isNotEmpty()) out[id] = keys
        }
        return out
    }

    /**
     * One guide from several, [bySource] in order of preference: per key, a programme from a later
     * source only fills time the earlier ones leave empty. A source that has a programme partly
     * over an earlier one's loses it, so a channel never shows two things at once.
     */
    fun merge(bySource: List<List<EpgProgrammeEntity>>): List<EpgProgrammeEntity> {
        val kept = HashMap<String, MutableList<EpgProgrammeEntity>>()
        for (programmes in bySource) {
            val added = programmes.groupBy { it.channelId }
            for ((key, list) in added) {
                val earlier = kept[key].orEmpty().toList()
                val fresh = list.sortedBy { it.start }.filter { p -> earlier.none { it.start < p.stop && p.start < it.stop } }
                // Within one source, the first of two overlapping programmes wins.
                val target = kept.getOrPut(key) { ArrayList() }
                var lastStop = Long.MIN_VALUE
                for (p in fresh) if (p.start >= lastStop) { target += p; lastStop = p.stop }
            }
        }
        return kept.values.flatten().distinctBy { it.channelId to it.start }
    }

    /** A source's channels with their display names. Channels come first in XMLTV, so this stops at the first programme. */
    fun channelNames(input: InputStream): Map<String, List<String>> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }
        val out = LinkedHashMap<String, MutableList<String>>()
        var current: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "channel" -> current = parser.getAttributeValue(null, "id")?.also { out.getOrPut(it) { ArrayList() } }
                "display-name" -> current?.let { id -> parser.nextText().trim().takeIf { it.isNotEmpty() }?.let { out.getValue(id) += it } }
                "programme" -> break
            }
        }
        return out
    }
}
