package nl.jordy.iptv.player

import java.util.Locale

/**
 * Names for audio and subtitle tracks in plain Dutch. Streams label tracks with ISO codes, two or
 * three letters ("nl", "nld", "dut"), sometimes "und" or nothing, so the name falls back to the
 * track's own label and then to its position.
 */
object TrackLabels {
    /** What Instellingen offers as a profile's default: code to name. Empty code = stream default / off. */
    val AUDIO_CHOICES = listOf("" to "Standaard", "nl" to "Nederlands", "en" to "Engels")
    val SUBTITLE_CHOICES = listOf("" to "Uit", "nl" to "Nederlands", "en" to "Engels")

    fun next(current: String, choices: List<Pair<String, String>>): String =
        choices[(choices.indexOfFirst { it.first == current }.coerceAtLeast(0) + 1) % choices.size].first

    fun name(current: String, choices: List<Pair<String, String>>): String =
        choices.firstOrNull { it.first == current }?.second ?: current.uppercase()

    private val nl = Locale.forLanguageTag("nl")

    /** Three-letter codes the JDK doesn't name reliably, mapped to their two-letter form. */
    private val threeLetter = mapOf(
        "nld" to "nl", "dut" to "nl", "eng" to "en", "deu" to "de", "ger" to "de", "fra" to "fr", "fre" to "fr",
        "spa" to "es", "ita" to "it", "por" to "pt", "tur" to "tr", "ara" to "ar", "pol" to "pl",
    )

    /** "nl" → "Nederlands"; null for no code or "und". */
    fun language(code: String?): String? {
        val raw = code?.lowercase()?.substringBefore('-')?.trim()?.takeIf { it.isNotEmpty() && it != "und" } ?: return null
        val c = threeLetter[raw] ?: raw
        val name = Locale.forLanguageTag(c).getDisplayLanguage(nl)
        return if (name.isBlank() || name.equals(c, ignoreCase = true)) c.uppercase() else name.replaceFirstChar { it.titlecase(nl) }
    }

    private fun channels(count: Int): String? = when (count) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> null
    }

    /** "Nederlands · 5.1"; [index] is the track's position, for streams that name nothing. */
    fun audio(language: String?, label: String?, channelCount: Int, index: Int): String =
        listOfNotNull(language(language) ?: label?.takeIf { it.isNotBlank() } ?: "Spoor ${index + 1}", channels(channelCount))
            .joinToString(" · ")

    fun subtitle(language: String?, label: String?, forced: Boolean, index: Int): String =
        (language(language) ?: label?.takeIf { it.isNotBlank() } ?: "Ondertitels ${index + 1}") + if (forced) " (alleen vertaling)" else ""
}
