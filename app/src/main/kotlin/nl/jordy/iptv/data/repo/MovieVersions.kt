package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.MovieVersionRow
import nl.jordy.iptv.data.xtream.NameParser

/** The language versions of one film, as the detail screen offers them. */
object MovieVersions {

    /**
     * A short name per version from its labels ("NL gesproken", "NL sub · 4K"); without labels
     * the country of its category, else "Origineel". Equal names get a number, so every button
     * says something different.
     */
    fun labels(versions: List<MovieVersionRow>): List<String> {
        val raw = versions.map { v ->
            v.movie.badges.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
                .ifEmpty { v.country?.let(NameParser::countryName) ?: "Origineel" }
        }
        val seen = mutableMapOf<String, Int>()
        return raw.map { label ->
            val n = (seen[label] ?: 0) + 1
            seen[label] = n
            if (raw.count { it == label } > 1) "$label $n" else label
        }
    }

    /** Which version Play starts: the one watched most recently, else the one that was opened. */
    fun pickDefault(versionIds: List<Int>, lastWatched: Map<Int, Long>, opened: Int): Int =
        versionIds.filter { it in lastWatched }.maxByOrNull { lastWatched.getValue(it) }
            ?: opened.takeIf { it in versionIds }
            ?: versionIds.first()
}
