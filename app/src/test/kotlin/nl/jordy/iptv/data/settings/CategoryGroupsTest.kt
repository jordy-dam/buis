package nl.jordy.iptv.data.settings

import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.settings.CategoryGroups.Kind
import nl.jordy.iptv.data.xtream.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryGroupsTest {

    private fun cat(name: String) = CategoryEntity(ContentType.MOVIE, name, name, 0, 0, "NL")

    @Test
    fun `services first and A to Z, then genres and the rest in the order they came`() {
        // Shapes as measured on the real provider (cleaned names), in provider order.
        val shown = listOf("BIOSCOOP", "NETFLIX", "ACTIE", "4K HDR | SDR", "DISNEY+ 4K HDR | SDR", "HORROR", "HBOMAX", "GOLDEN CLASSICS", "NETFLIX DOCUMENTAIRES")
            .map(::cat)
        val groups = CategoryGroups.group(shown)
        assertEquals(listOf(Kind.SERVICE, Kind.GENRE, Kind.OTHER), groups.map { it.kind })
        assertEquals(listOf("DISNEY+ 4K HDR | SDR", "HBOMAX", "NETFLIX", "NETFLIX DOCUMENTAIRES"), groups[0].categories.map { it.name })
        assertEquals(listOf("ACTIE", "HORROR"), groups[1].categories.map { it.name })
        assertEquals(listOf("BIOSCOOP", "4K HDR | SDR", "GOLDEN CLASSICS"), groups[2].categories.map { it.name })
    }

    @Test
    fun `the provider's own picks go on top, even pinned, and look-alikes don't`() {
        val shown = listOf("NETFLIX", "TOP 100", "ACTIE", "NEW RELEASES", "TOPGEAR SPECIALS", "NEWFAITHNETWORK", "BIOSCOOP").map(::cat)
        val groups = CategoryGroups.group(shown, pinned = setOf("MOVIE:TOP 100"))
        assertEquals(Kind.FEATURED, groups.first().kind)
        assertEquals(listOf("TOP 100", "NEW RELEASES"), groups.first().categories.map { it.name })
        assertEquals("a channel named New-something is no pick", Kind.OTHER, CategoryGroups.kind("NEWFAITHNETWORK"))
        assertEquals(Kind.OTHER, CategoryGroups.kind("TOPGEAR SPECIALS"))
    }

    @Test
    fun `provider picks read as what they are, top list first`() {
        val (rankTop, top) = CategoryGroups.featured(cat("TOP 100"))
        val (rankNew, new) = CategoryGroups.featured(cat("NEW RELEASES"))
        assertEquals("Top 100", top.name)
        assertEquals("Nieuwe films", new.name)
        assertEquals("Nieuwe series", CategoryGroups.featured(cat("NEW RELEASES"), series = true).second.name)
        assertTrue(rankTop < rankNew)
    }

    @Test
    fun `pinned categories move to the top, A to Z, and leave their own group`() {
        val shown = listOf("NETFLIX", "VIDEOLAND", "APPLE TV+", "ACTIE", "BIOSCOOP").map(::cat)
        val pinned = setOf("MOVIE:VIDEOLAND", "MOVIE:APPLE TV+")
        val groups = CategoryGroups.group(shown, pinned)
        assertEquals(listOf(Kind.PINNED, Kind.SERVICE, Kind.GENRE, Kind.OTHER), groups.map { it.kind })
        assertEquals(listOf("APPLE TV+", "VIDEOLAND"), groups[0].categories.map { it.name })
        assertEquals(listOf("NETFLIX"), groups[1].categories.map { it.name })
    }

    @Test
    fun `a pin for series does not pin the film category with the same id`() {
        val groups = CategoryGroups.group(listOf(cat("NETFLIX")), setOf("SERIES:NETFLIX"))
        assertEquals(listOf(Kind.SERVICE), groups.map { it.kind })
    }

    @Test
    fun `A to Z ignores case and leading symbols`() {
        val groups = CategoryGroups.group(listOf("netflix", "★ DISNEY+", "Apple TV+").map(::cat))
        assertEquals(listOf("Apple TV+", "★ DISNEY+", "netflix"), groups[0].categories.map { it.name })
    }

    @Test
    fun `whole words only, so look-alikes stay where they belong`() {
        assertEquals(Kind.OTHER, CategoryGroups.kind("PRIMETIME KLASSIEKERS"))
        assertEquals(Kind.OTHER, CategoryGroups.kind("STAR WARS SAGA"))
        assertEquals(Kind.SERVICE, CategoryGroups.kind("Amazon Prime Video"))
        assertEquals(Kind.SERVICE, CategoryGroups.kind("APPLE TV+"))
        assertEquals(Kind.GENRE, CategoryGroups.kind("DRAMA ┃ ROMANTIEK"))
        assertEquals("a service named after a genre is still a service", Kind.SERVICE, CategoryGroups.kind("NETFLIX KIDS"))
    }
}
