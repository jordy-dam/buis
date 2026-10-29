package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.MovieVersionRow
import java.util.Locale

/** The language versions of one film, as the detail screen offers them. */
object MovieVersions {

    /** What a version is, in two lines: the language in plain Dutch, then title and picture quality. */
    data class Label(val language: String, val detail: String)

    /**
     * "Nederlands gesproken", "Nederlands ondertiteld", "Meerdere talen" or "Origineel", from the
     * provider's tags. The title goes in [Label.detail] only when the versions carry different titles
     * (a Dutch and an English one), with quality tags (4K, HDR) after it. Equal names get a number.
     */
    fun labels(versions: List<MovieVersionRow>): List<Label> {
        val titlesDiffer = versions.map { it.movie.name.lowercase() }.distinct().size > 1
        val raw = versions.map { v ->
            val tags = v.movie.badges.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val language = tags.firstNotNullOfOrNull(::language) ?: "Origineel"
            val rest = tags.filter { language(it) == null }
            Label(language, (listOfNotNull(v.movie.name.takeIf { titlesDiffer }) + rest).joinToString(" · "))
        }
        val seen = mutableMapOf<Label, Int>()
        return raw.map { label ->
            val n = (seen[label] ?: 0) + 1
            seen[label] = n
            if (raw.count { it == label } > 1) label.copy(language = "${label.language} $n") else label
        }
    }

    private val languageOfCountry = mapOf("UK" to "en", "GB" to "en", "US" to "en")

    /** "NL gesproken" → "Nederlands gesproken", "EN sub" → "Engels ondertiteld", "Multi" → "Meerdere talen"; else null. */
    private fun language(tag: String): String? {
        if (tag == "Multi") return "Meerdere talen"
        val m = Regex("""^([A-Z]{2}) (gesproken|audio|sub)$""").matchEntire(tag) ?: return null
        val code = m.groupValues[1]
        val name = Locale.forLanguageTag(languageOfCountry[code] ?: code.lowercase())
            .getDisplayLanguage(Locale.forLanguageTag("nl-NL")).replaceFirstChar { it.uppercase() }.ifEmpty { code }
        return if (m.groupValues[2] == "sub") "$name ondertiteld" else "$name gesproken"
    }

    /** Which version Play starts: the one watched most recently, else the one that was opened. */
    fun pickDefault(versionIds: List<Int>, lastWatched: Map<Int, Long>, opened: Int): Int =
        versionIds.filter { it in lastWatched }.maxByOrNull { lastWatched.getValue(it) }
            ?: opened.takeIf { it in versionIds }
            ?: versionIds.first()
}
