package nl.jordy.iptv.data.xtream

import java.util.Locale

/**
 * A provider name split into what people want to read and what the app can use.
 *
 * `┃NL┃ NPO 1 FHD` → title "NPO 1", country "NL", badges ["FHD"]
 * `┃MULTI / LINGO┃ APPLE TV+` → title "APPLE TV+", badges ["Multi"]
 * `┃NL┃ TV WEST ⏺ʳᵉᶜ` → title "TV WEST", catchup = true
 */
data class ParsedName(
    val title: String,
    /** Group code for country filtering: ISO country (`NL`, `US`, `GB`) or `EN` for English-language VOD. */
    val country: String?,
    val badges: List<String>,
    val catchup: Boolean,
)

object NameParser {

    // Providers use the Unicode bar ┃ (U+2503) as often as |, plus a few look-alikes.
    private const val BARS = "|┃│｜"
    private val leadingTag = Regex("""^\s*([$BARS\[(]?)\s*([A-Za-z0-9+/ \-]{2,20}?)\s*([$BARS\])])\s*""")
    // "NL: Name" / "NL - Name" without bars.
    private val leadingCode = Regex("""^\s*([A-Z]{2,3})\s*[:\-]\s+""")
    private val catchupMark = Regex("""\s*⏺\s*[ʳᵉᶜ]*\s*""")
    // "… [NL]", "… [DOLBY]", "… (NL GESPROKEN)"; years like "(2024)" are left alone.
    private val trailingTag = Regex("""\s*[$BARS\[(]\s*([^$BARS\[\]()]{2,16}?)\s*[$BARS\])]\s*$""")
    private val year = Regex("""^(19|20)\d{2}$""")
    private val qualitySuffix = Regex("""\s+(FHD|UHD|4K|HD|SD|HEVC|H265|RAW)\s*$""", RegexOption.IGNORE_CASE)
    private val decoration = Regex("""^[\s=#*─━_~$BARS-]+|[\s=#*─━_~$BARS-]+$""")
    private val spaces = Regex("""\s{2,}""")

    /** Trailing tags worth keeping as a badge, with the country they imply. */
    private val trailingTags = mapOf(
        "NL GESPROKEN" to ("NL gesproken" to "NL"),
        "NL GESUB" to ("NL sub" to "NL"),
        "NL ONDERTITELD" to ("NL sub" to "NL"),
        "ONDERTITELD" to ("NL sub" to "NL"),
        "DUTCH SUBS" to ("NL sub" to "NL"),
        "DUTCH SUBBED" to ("NL sub" to "NL"),
        "DOLBY" to ("Dolby" to null),
        "DOLBY VISION" to ("Dolby Vision" to null),
        "ATMOS" to ("Atmos" to null),
        "HDR" to ("HDR" to null),
    )

    private val isoCountries: Set<String> = Locale.getISOCountries().toSet()
    private val aliases = mapOf("USA" to "US", "UK" to "GB")
    private val languageGroups = setOf("EN")

    /** [stripQuality] is off for category names: "NEDERLAND 4K ULTRA" is a category, not a badge. */
    /**
     * [keepHd]: channels keep "HD" as a badge, so "NPO 1 HD" and "NPO 1 SD" stay apart in the lists;
     * on films it's on nearly everything and says nothing.
     */
    fun parse(raw: String, stripQuality: Boolean = true, keepHd: Boolean = false): ParsedName {
        var rest = raw
        var country: String? = null
        val badges = mutableListOf<String>()

        val catchup = catchupMark.containsMatchIn(rest)
        rest = catchupMark.replace(rest, " ")

        val bracketed = leadingTag.find(rest)
        val classified = if (bracketed != null) {
            // Only bars mark a provider tag for sure; "(500) Days of Summer" must stay intact.
            val barred = bracketed.groupValues[3].first() in BARS && bracketed.groupValues[1].let { it.isEmpty() || it.first() in BARS }
            classify(bracketed.groupValues[2], allowNonCountry = barred)?.let { it to bracketed }
        } else {
            // "NL: Name" only for real country codes, so "WWE: Raw" keeps its title.
            leadingCode.find(rest)?.let { m -> country(m.groupValues[1])?.let { (it to null as String?) to m } }
        }
        if (classified != null) {
            val (result, match) = classified
            country = result.first
            result.second?.let(badges::add)
            rest = rest.substring(match.range.last + 1)
        }

        while (true) {
            val m = trailingTag.find(rest) ?: break
            val tag = spaces.replace(m.groupValues[1].trim().uppercase(), " ")
            if (year.matches(tag)) break
            val known = trailingTags[tag]
            val code = country(tag)
            val language = if (known == null && code == null) classify(tag, allowNonCountry = false) else null
            when {
                known != null -> { badges += known.first; if (country == null) country = known.second }
                code != null -> if (country == null) country = code
                // "[UK AUDIO]": a language badge, same shape as the leading "┃NL-AUDIO┃".
                language?.second != null -> { badges += language.second!!; if (country == null) country = language.first }
                else -> break
            }
            rest = rest.substring(0, m.range.first)
        }

        if (stripQuality) {
            val found = mutableListOf<String>()
            while (true) {
                val m = qualitySuffix.find(rest) ?: break
                found += m.groupValues[1].uppercase()
                rest = rest.substring(0, m.range.first)
            }
            // "HD" is on most films and says nothing there; the rarer ones help tell variants apart.
            found.asReversed().map { if (it == "UHD") "4K" else it }.filter { keepHd || it != "HD" }.distinct().forEach(badges::add)
        }

        val title = spaces.replace(decoration.replace(rest, ""), " ").trim()
        return ParsedName(
            title = title.ifEmpty { raw.trim() },
            country = country,
            badges = badges.distinct(),
            catchup = catchup,
        )
    }

    /** Returns (country, badge) for a recognised tag, or null when it isn't a tag (then nothing is stripped). */
    private fun classify(rawTag: String, allowNonCountry: Boolean): Pair<String?, String?>? {
        val tag = spaces.replace(rawTag.trim().uppercase(), " ")
        if (tag.contains("MULTI") || tag.contains("LINGO")) return (null as String?) to "Multi"
        // NL-SUB / NL SUBS / NL-AUDIO / NL DUBBED: subtitles or dubbing, in all the spellings panels use.
        Regex("""^([A-Z]{2})[- ]?(SUBS?|SUBBED|SUBTITLED|GESUBD?|AUDIO|DUB|DUBBED|GESPROKEN)$""").matchEntire(tag)?.let { m ->
            val cc = m.groupValues[1]
            val badge = when (m.groupValues[2]) {
                "AUDIO" -> "$cc audio"
                "DUB", "DUBBED", "GESPROKEN" -> if (cc == "NL") "NL gesproken" else "$cc audio"
                else -> "$cc sub"
            }
            return country(cc) to badge
        }
        // "CA EN" / "CA FR": a country with a language.
        Regex("""^([A-Z]{2}) ([A-Z]{2})$""").matchEntire(tag)?.let { m ->
            return country(m.groupValues[1]) to m.groupValues[2]
        }
        country(tag)?.let { return it to null }
        // PPV, UCL, F1TV, ADULT…: a real tag, but not a country. Keep it visible as a badge.
        if (allowNonCountry && tag.length in 2..8 && tag.any { it.isLetter() } && tag.none { it == ' ' } && tag.all { it.isLetterOrDigit() || it == '+' }) return null to tag
        return null
    }

    private fun country(code: String): String? {
        val c = aliases[code] ?: code
        return c.takeIf { it in isoCountries || it in languageGroups }
    }

    // Whole words only: "XXX" and "ADULT(S)", plus "18+"; "ADULTSWIM" or "MIDDLESEX" don't count.
    private val adultWord = Regex("""(?<![A-Z0-9])(ADULTS?|XXX|18\+)(?![A-Z0-9])""", RegexOption.IGNORE_CASE)

    /** Adult category by its raw provider name (`ADULT ---`, `===== XXX =====`, `┃18+┃ …`). */
    fun isAdult(rawName: String): Boolean = adultWord.containsMatchIn(rawName)

    /** Dutch display name for a group code: "NL" → "Nederland", "EN" → "Engelstalig". */
    fun countryName(code: String): String = when (code) {
        "EN" -> "Engelstalig"
        else -> Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.forLanguageTag("nl-NL")).ifEmpty { code }
    }
}
