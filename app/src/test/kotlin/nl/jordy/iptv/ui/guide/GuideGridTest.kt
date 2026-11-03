package nl.jordy.iptv.ui.guide

import nl.jordy.iptv.data.xtream.EpgEntry
import nl.jordy.iptv.ui.guide.GuideGrid.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The grid only works on a remote if every row covers the window without overlaps (or Left/Right
 * skips and repeats programmes) and Up/Down keeps the time you were looking at.
 */
class GuideGridTest {

    private val from = 1_800_000_000L - Math.floorMod(1_800_000_000L, 1800L)
    private val to = from + 7 * 3600
    private fun min(m: Int) = from + m * 60L
    private fun p(title: String, start: Int, stop: Int) = EpgEntry(title, null, min(start), min(stop))

    @Test
    fun `time maps to a distance at the scale`() {
        assertEquals(0f, GuideGrid.x(from, from, 5f))
        assertEquals(150f, GuideGrid.x(min(30), from, 5f))
        assertEquals(-25f, GuideGrid.x(min(-5), from, 5f))
    }

    @Test
    fun `programmes are clipped at both window edges but keep their real times`() {
        val film = p("Film", -40, 50)
        val late = p("Laat", 400, 480)
        val row = GuideGrid.row(listOf(film, p("Nieuws", 50, 400), late), from, to)
        assertEquals(Block(from, min(50), film), row.first())
        assertEquals(Block(min(400), to, late), row.last())
        assertEquals(min(-40), row.first().programme?.start)
    }

    @Test
    fun `programmes entirely outside the window are left out`() {
        val row = GuideGrid.row(listOf(p("Gisteren", -120, -60), p("Nu", 0, 420), p("Morgen", 500, 560)), from, to)
        assertEquals(listOf("Nu"), row.map { it.programme?.title })
    }

    @Test
    fun `a channel without guide data is one Geen gids block across the window`() {
        assertEquals(listOf(Block(from, to, null)), GuideGrid.row(emptyList(), from, to))
    }

    @Test
    fun `overlapping programmes from merged guides never cover each other`() {
        val row = GuideGrid.row(listOf(p("A", 0, 60), p("B", 45, 120), p("C", 120, 420)), from, to)
        assertEquals(listOf(min(0) to min(60), min(60) to min(120), min(120) to to), row.map { it.start to it.stop })
    }

    @Test
    fun `a long gap gets a block you can land on, a short one stays empty`() {
        val row = GuideGrid.row(listOf(p("A", 0, 60), p("B", 62, 120), p("C", 180, 420)), from, to)
        assertEquals(listOf("A", "B", null, "C"), row.map { it.programme?.title })
        assertEquals(Block(min(120), min(180), null), row[2])
    }

    @Test
    fun `Up or Down lands on the programme running at the anchor`() {
        val row = GuideGrid.row(listOf(p("A", 0, 60), p("B", 60, 90), p("C", 90, 420)), from, to)
        assertEquals(1, GuideGrid.target(row, min(75)))
        assertEquals(1, GuideGrid.target(row, min(60)))
        assertEquals(2, GuideGrid.target(row, min(90)))
    }

    @Test
    fun `Up or Down in a short gap lands on the nearest programme`() {
        val row = GuideGrid.row(listOf(p("A", 0, 60), p("B", 63, 420)), from, to)
        assertEquals(1, GuideGrid.target(row, min(62)))
        assertEquals(0, GuideGrid.target(row, min(60)))
        assertEquals(-1, GuideGrid.target(emptyList(), min(60)))
    }

    @Test
    fun `the timeline stays put while the focused block can be read`() {
        val view = min(30)
        assertEquals(view, GuideGrid.scrollFor(Block(min(0), min(60), null), view, 2 * 3600, from, to))
        assertEquals(view, GuideGrid.scrollFor(Block(min(130), min(200), null), view, 2 * 3600, from, to))
    }

    @Test
    fun `moving right past the edge scrolls to half an hour before the block`() {
        val block = Block(min(145), min(200), null)
        assertEquals(min(90), GuideGrid.scrollFor(block, min(30), 2 * 3600, from, to))
    }

    @Test
    fun `moving left past the edge scrolls back to the block's half hour`() {
        val block = Block(min(5), min(25), null)
        assertEquals(min(0), GuideGrid.scrollFor(block, min(60), 2 * 3600, from, to))
    }

    @Test
    fun `scrolling never leaves the window`() {
        assertEquals(to - 2 * 3600, GuideGrid.scrollFor(Block(min(410), to, null), min(30), 2 * 3600, from, to))
        assertEquals(from, GuideGrid.scrollFor(Block(from, min(10), null), min(120), 2 * 3600, from, to))
    }

    @Test
    fun `ticks are the half hours inside the window`() {
        val ticks = GuideGrid.ticks(from + 60, from + 3600)
        assertEquals(listOf(min(30), min(60)), ticks)
        assertNull(GuideGrid.ticks(min(10), min(20)).firstOrNull())
    }
}
