package nl.jordy.iptv.data.settings

import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.settings.CategoryGroups.Kind
import nl.jordy.iptv.data.xtream.ContentType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryGroupsTest {

    private fun cat(name: String) = CategoryEntity(ContentType.MOVIE, name, name, 0, 0, "NL")

    @Test
    fun `services first, then genres, then the rest, each in the order it came`() {
        // Shapes as measured on the real provider (cleaned names), in provider order.
        val shown = listOf("BIOSCOOP", "NETFLIX", "ACTIE", "4K HDR | SDR", "DISNEY+ 4K HDR | SDR", "HORROR", "HBOMAX", "GOLDEN CLASSICS", "NETFLIX DOCUMENTAIRES")
            .map(::cat)
        val groups = CategoryGroups.group(shown)
        assertEquals(listOf(Kind.SERVICE, Kind.GENRE, Kind.OTHER), groups.map { it.kind })
        assertEquals(listOf("NETFLIX", "DISNEY+ 4K HDR | SDR", "HBOMAX", "NETFLIX DOCUMENTAIRES"), groups[0].categories.map { it.name })
        assertEquals(listOf("ACTIE", "HORROR"), groups[1].categories.map { it.name })
        assertEquals(listOf("BIOSCOOP", "4K HDR | SDR", "GOLDEN CLASSICS"), groups[2].categories.map { it.name })
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
