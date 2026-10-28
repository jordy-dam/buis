package nl.jordy.iptv.data.repo

/**
 * Series genres as providers send them ("Drama, Misdaad", "Action & Adventure", "Comedy"),
 * turned into a short list of chips. English and Dutch spellings of the same genre become one
 * Dutch chip, and the filter matches either spelling.
 */
object Genres {
    private val dutch = mapOf(
        "comedy" to "Komedie", "crime" to "Misdaad", "mystery" to "Mysterie", "documentary" to "Documentaire",
        "animation" to "Animatie", "family" to "Familie", "action" to "Actie", "adventure" to "Avontuur",
        "war" to "Oorlog", "romance" to "Romantiek", "dram" to "Drama",
    )

    /** One raw genre field → clean, distinct Dutch names. */
    fun split(raw: String?): List<String> =
        raw.orEmpty().split(',', '/', '&', '|')
            .map { it.trim() }
            .filter { it.length >= 3 }
            .map { g -> dutch[g.lowercase()] ?: g.replaceFirstChar { it.uppercase() } }
            .distinct()

    /** The [max] most common genres in a category, most common first. */
    fun top(raws: List<String?>, max: Int = 8): List<String> =
        raws.flatMap(::split).groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(max).map { it.key }

    /** LIKE patterns that find [genre] in a raw field, in Dutch or the provider's English. */
    fun patterns(genre: String): Pair<String, String> {
        val english = dutch.entries.firstOrNull { it.value == genre && it.key != "dram" }?.key ?: genre
        return "%$genre%" to "%$english%"
    }
}
