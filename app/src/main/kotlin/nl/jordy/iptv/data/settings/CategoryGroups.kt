package nl.jordy.iptv.data.settings

import nl.jordy.iptv.data.db.CategoryEntity

/**
 * Splits a VOD category list into what the profile pinned, services (Netflix, Disney+ …) A–Z,
 * genres (Actie, Horror …) and the rest; genres and the rest keep the order they had (preferred
 * countries first, see [CountryFilter]). Providers name categories after the service or genre, so
 * this works on names alone.
 */
object CategoryGroups {
    enum class Kind(val title: String) { FEATURED("Uitgelicht"), PINNED("Vastgezet"), SERVICE("Diensten"), GENRE("Genres"), OTHER("Overig") }

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

    /** The provider's own picks ("TOP 100", "NEW RELEASES"): what's worth watching, so on top. */
    private val featured = Regex(
        """^\W*(top ?\d+|new releases?|nieuwe? (films|releases|series)|trending|populair|popular|must ?see)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val topList = Regex("""^\W*top ?(\d+)""", RegexOption.IGNORE_CASE)
    private val newList = Regex("""^\W*(new releases?|nieuwe? (films|releases|series))\b""", RegexOption.IGNORE_CASE)

    /**
     * A provider pick as it reads under "Uitgelicht", and where it goes: "TOP 100" → "Top 100" first,
     * "NEW RELEASES" → "Nieuwe films" (new titles) next, anything else as it came. Apart from BUIS's
     * own "Net toegevoegd" (newly added to the catalog, old films too), which follows them.
     */
    fun featured(c: CategoryEntity, series: Boolean = false): Pair<Int, CategoryEntity> {
        topList.find(c.name)?.let { return 0 to c.copy(name = "Top ${it.groupValues[1]}") }
        if (newList.containsMatchIn(c.name)) return 1 to c.copy(name = if (series) "Nieuwe series" else "Nieuwe films")
        return 2 to c
    }

    fun kind(name: String): Kind = when {
        featured.containsMatchIn(name) -> Kind.FEATURED
        services.containsMatchIn(name) -> Kind.SERVICE
        genres.containsMatchIn(name) -> Kind.GENRE
        else -> Kind.OTHER
    }

    /**
     * Non-empty groups in the order featured, pinned, services, genres, rest. A pinned category shows
     * only once, on top; the provider's picks stay with the featured ones even when pinned.
     */
    fun group(categories: List<CategoryEntity>, pinned: Set<String> = emptySet()): List<Group> {
        val byKind = categories.groupBy { kind(it.name).let { k -> if (k != Kind.FEATURED && pinKey(it) in pinned) Kind.PINNED else k } }
        return Kind.entries.mapNotNull { k ->
            byKind[k]?.let { Group(k, if (k == Kind.PINNED || k == Kind.SERVICE) it.sortedWith(alphabetical) else it) }
        }
    }
}
