package nl.jordy.iptv.data.settings

import nl.jordy.iptv.data.db.CategoryEntity

/**
 * Splits a VOD category list into what the profile pinned, services (Netflix, Disney+ …) A–Z,
 * genres (Actie, Horror …) and the rest; genres and the rest keep the order they had (preferred
 * countries first, see [CountryFilter]). Providers name categories after the service or genre, so
 * this works on names alone.
 */
object CategoryGroups {
    enum class Kind(val title: String) { PINNED("Vastgezet"), SERVICE("Diensten"), GENRE("Genres"), OTHER("Overig") }

    /** How a pinned category is stored on the profile; films and series have separate id spaces. */
    fun pinKey(c: CategoryEntity) = "${c.type.name}:${c.id}"

    /** A–Z as people read it: case and leading symbols ("★ Disney+") don't count. */
    private val alphabetical = compareBy<CategoryEntity> { it.name.dropWhile { ch -> !ch.isLetter() }.lowercase() }

    data class Group(val kind: Kind, val categories: List<CategoryEntity>)

    // Word starts, so "MAX" doesn't match "MAXIMAAL" and "PRIME" not "PRIMETIME".
    private val services = Regex(
        """\b(netflix|disney\+?|amazon|prime video|hbo ?max|hbo|max|apple tv\+?|videoland|skyshowtime|viaplay|paramount\+?|""" +
            """npo ?(start|plus)|nlziet|kijk|hulu|peacock|canal\+?|starz|discovery\+?|bbc|itvx?|mgm\+?|crunchyroll|hayu|nick\+?|bet\+?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val genres = Regex(
        """\b(actie|action|avontuur|adventure|horror|drama|romantiek|romance|komedie|comedy|thriller|familie|family|fantasy|""" +
            """sci-?fi|science fiction|documentaires?|documentary|oorlog|war|history|historisch|misdaad|crime|animatie|animation|""" +
            """western|mysterie|mystery|muziek|musical|true crime|kerstfilms?)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun kind(name: String): Kind = when {
        services.containsMatchIn(name) -> Kind.SERVICE
        genres.containsMatchIn(name) -> Kind.GENRE
        else -> Kind.OTHER
    }

    /** Non-empty groups in the order pinned, services, genres, rest. A pinned category shows only once, on top. */
    fun group(categories: List<CategoryEntity>, pinned: Set<String> = emptySet()): List<Group> {
        val byKind = categories.groupBy { if (pinKey(it) in pinned) Kind.PINNED else kind(it.name) }
        return Kind.entries.mapNotNull { k ->
            byKind[k]?.let { Group(k, if (k == Kind.PINNED || k == Kind.SERVICE) it.sortedWith(alphabetical) else it) }
        }
    }
}
