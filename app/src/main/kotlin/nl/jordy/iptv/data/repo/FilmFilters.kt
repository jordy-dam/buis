package nl.jordy.iptv.data.repo

/**
 * Stemming and lengte at Films. A mood is a handful of genres ("Licht" = comedy, family,
 * animation); a length is a maximum running time. Both work on the film details BUIS fetches in
 * the background, so they get better as more details arrive.
 */
object FilmFilters {
    val MOODS: Map<String, List<String>> = linkedMapOf(
        "Licht" to listOf("Komedie", "Familie", "Animatie"),
        "Spannend" to listOf("Thriller", "Actie", "Misdaad", "Horror"),
    )

    /** Seconds to label; 0 is any length. OK on the chip steps through them. */
    val LENGTHS = listOf(0 to "Elke lengte", 100 * 60 to "Korter dan 100 min", 120 * 60 to "Korter dan 2 uur")

    fun nextLength(current: Int): Int = LENGTHS[(LENGTHS.indexOfFirst { it.first == current }.coerceAtLeast(0) + 1) % LENGTHS.size].first
    fun lengthLabel(secs: Int): String = LENGTHS.firstOrNull { it.first == secs }?.second ?: "Elke lengte"

    /** The query takes at most this many genre patterns. */
    const val SLOTS = 6

    /**
     * LIKE patterns for a chip: a mood's genres or one genre, each in Dutch and the provider's
     * English, without doubles. Empty for "Alle".
     */
    fun patterns(chip: String?): List<String> {
        val genres = MOODS[chip] ?: listOfNotNull(chip)
        return genres.flatMap { Genres.patterns(it).toList() }.distinct().take(SLOTS)
    }

    /** [patterns] padded to [SLOTS] for the query; padding repeats a real pattern so it can't widen the match. */
    fun slots(chip: String?): List<String> {
        val p = patterns(chip)
        return if (p.isEmpty()) List(SLOTS) { "" } else p + List(SLOTS - p.size) { p.first() }
    }
}
