package nl.jordy.iptv.data.settings

import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.xtream.ContentType
import org.junit.Assert.assertEquals
import org.junit.Test

class CountryFilterTest {

    private fun cat(id: String, country: String?, adult: Boolean = false) = CategoryEntity(ContentType.LIVE, id, id, 0, 0, country, adult)

    // Provider order: PPV first, Dutch somewhere in the middle, like the real catalog.
    private val provider = listOf(
        cat("ppv", null), cat("uk1", "GB"), cat("nl1", "NL"), cat("de1", "DE"), cat("nl2", "NL"), cat("be1", "BE"), cat("radio", null),
    )

    @Test
    fun `preferred countries come first, then untagged, others hidden`() {
        assertEquals(listOf("nl1", "nl2", "ppv", "radio"), CountryFilter.apply(provider, setOf("NL"), onlyPreferred = true).map { it.id })
    }

    @Test
    fun `with the filter off everything stays, preferred still on top`() {
        assertEquals(
            listOf("nl1", "nl2", "ppv", "radio", "uk1", "de1", "be1"),
            CountryFilter.apply(provider, setOf("NL"), onlyPreferred = false).map { it.id },
        )
    }

    @Test
    fun `several preferred countries keep the provider's order between them`() {
        assertEquals(listOf("nl1", "nl2", "be1", "ppv", "radio"), CountryFilter.apply(provider, setOf("NL", "BE"), onlyPreferred = true).map { it.id })
    }

    @Test
    fun `a provider without any preferred country doesn't end up with an empty screen`() {
        val foreignOnly = listOf(cat("uk1", "GB"), cat("ppv", null))
        assertEquals(listOf("ppv", "uk1"), CountryFilter.apply(foreignOnly, setOf("NL"), onlyPreferred = true).map { it.id })
    }

    @Test
    fun `country picker lists preferred first, then by size`() {
        val cats = listOf(cat("a", "US"), cat("b", "US"), cat("c", "GB"), cat("d", "NL"), cat("e", null))
        assertEquals(listOf("NL", "US", "GB"), CountryFilter.countries(cats, setOf("NL")))
    }

    @Test
    fun `a kids profile never sees adult categories, not even with the filter off`() {
        val withAdult = provider + cat("xxx", null, adult = true) + cat("nl-adult", "NL", adult = true)
        assertEquals(listOf("nl1", "nl2", "ppv", "radio"), CountryFilter.apply(withAdult, setOf("NL"), onlyPreferred = true, hideAdult = true).map { it.id })
        assertEquals(false, CountryFilter.apply(withAdult, setOf("NL"), onlyPreferred = false, hideAdult = true).any { it.adult })
    }

    @Test
    fun `other profiles keep seeing adult categories as before`() {
        val withAdult = provider + cat("xxx", null, adult = true)
        assertEquals(true, CountryFilter.apply(withAdult, setOf("NL"), onlyPreferred = true).any { it.id == "xxx" })
    }
}
