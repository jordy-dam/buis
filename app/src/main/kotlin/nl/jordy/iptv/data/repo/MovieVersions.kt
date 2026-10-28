package nl.jordy.iptv.data.repo

import nl.jordy.iptv.data.db.MovieVersionRow

/** The language versions of one film, as the detail screen offers them. */
object MovieVersions {

    /**
     * A short name per version: its labels ("NL gesproken", "NL sub · 4K"), else "Origineel".
     * When the versions carry different titles (a Dutch and an English one), the title is added,
     * because that's what tells them apart. Equal names get a number.
     */
    fun labels(versions: List<MovieVersionRow>): List<String> {
        val titlesDiffer = versions.map { it.movie.name.lowercase() }.distinct().size > 1
        val raw = versions.map { v ->
            val tags = v.movie.badges.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Origineel" }
            if (titlesDiffer) "$tags · ${v.movie.name}" else tags
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
