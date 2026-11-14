package nl.jordy.iptv.ui.components

import nl.jordy.iptv.data.xtream.ProgrammeExtra
import java.util.Locale

/*
 * How a guide programme's details read, the same in the player's "i" and in a channel's guide:
 * each line null when the guide has nothing for it, so the screen simply leaves it out.
 */

/** "S1 E3 · The whistleblower"; a title that only says "Aflevering 308" next to E308 adds nothing. */
fun ProgrammeExtra.episodeLine(): String? {
    val title = subtitle?.takeUnless { episode != null && NUMBERED.matches(it) }
    return listOfNotNull(episode, title).joinToString(" · ").ifEmpty { null }
}

private val NUMBERED = Regex("""(aflevering|episode|afl\.?)\s*\d+""", RegexOption.IGNORE_CASE)

/** "Docudrama · 2026 · Verenigd Koninkrijk". */
fun ProgrammeExtra.factsLine(): String? {
    val land = country?.let { code ->
        if (code.length == 2) Locale("", code).getDisplayCountry(Locale("nl")).ifBlank { code } else code
    }
    return listOfNotNull(genres, year, land).joinToString(" · ").ifEmpty { null }
}

/** "Met David Thewlis, Jason Watkins · Regie Joseph Bullman". */
fun ProgrammeExtra.creditsLine(): String? =
    listOfNotNull(cast?.let { "Met $it" }, director?.let { "Regie $it" }).joinToString(" · ").ifEmpty { null }
