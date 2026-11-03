package nl.jordy.iptv.ui.guide

import nl.jordy.iptv.data.xtream.EpgEntry

/**
 * The pure geometry of the TV guide grid ("Gids"): which blocks a channel row holds inside the time
 * window, where they sit, which block Up/Down lands on and when the timeline has to scroll. Times
 * are epoch seconds throughout; the screen turns them into dp with [GuideGrid.x].
 */
object GuideGrid {
    const val HALF_HOUR = 30 * 60L

    /** Gaps shorter than this stay empty; longer ones get a "Geen gids" block you can land on. */
    const val MIN_FILLER = 5 * 60L

    /**
     * One block in a channel row: a programme cut to the window, or a stretch without guide data
     * ([programme] null). [start]/[stop] are the visible part; the programme keeps its real times.
     */
    data class Block(val start: Long, val stop: Long, val programme: EpgEntry?)

    fun floorHalfHour(sec: Long): Long = sec - Math.floorMod(sec, HALF_HOUR)

    /** Distance from [from] to [sec] on a timeline of [perMinute] units a minute (dp, px). */
    fun x(sec: Long, from: Long, perMinute: Float): Float = (sec - from) / 60f * perMinute

    /**
     * A channel's row from [from] to [to]: programmes clipped to the window, overlaps (two merged
     * guides) cut so blocks never cover each other, gaps of [MIN_FILLER] or more filled with a
     * block without programme. A channel without any data is one such block across the window.
     */
    fun row(programmes: List<EpgEntry>, from: Long, to: Long): List<Block> {
        val blocks = mutableListOf<Block>()
        var cursor = from
        fun gap(until: Long) {
            if (until - cursor >= MIN_FILLER) blocks += Block(cursor, until, null)
        }
        for (p in programmes.sortedBy { it.start }) {
            val start = maxOf(p.start, cursor)
            val stop = minOf(p.stop, to)
            if (stop <= start) continue
            gap(start)
            blocks += Block(start, stop, p)
            cursor = stop
        }
        gap(to)
        return blocks
    }

    /**
     * Where Up/Down lands in [blocks]: the block running at [anchor], else the nearest one, so the
     * time position holds while moving between channels. -1 only for an empty row.
     */
    fun target(blocks: List<Block>, anchor: Long): Int {
        val at = blocks.indexOfFirst { anchor >= it.start && anchor < it.stop }
        if (at >= 0) return at
        return blocks.indices.minByOrNull { i ->
            val b = blocks[i]
            if (anchor < b.start) b.start - anchor else anchor - b.stop + 1
        } ?: -1
    }

    /**
     * Where the visible part of the timeline should start ([viewStart] is now) so [block] can be
     * read: unchanged while some of it shows with room for a title, else on the half hour before
     * its start (half an hour earlier still when moving right, for context). Kept inside the
     * window, never past its end minus one [span].
     */
    fun scrollFor(block: Block, viewStart: Long, span: Long, from: Long, to: Long, minVisible: Long = 20 * 60L): Long {
        val viewEnd = viewStart + span
        val visible = minOf(block.stop, viewEnd) - maxOf(block.start, viewStart)
        if (visible >= minOf(minVisible, block.stop - block.start)) return viewStart
        val wanted = if (block.start < viewStart) floorHalfHour(block.start) else floorHalfHour(block.start) - HALF_HOUR
        return wanted.coerceIn(from, maxOf(from, to - span))
    }

    /** The half hours to label along the top, from [from] (rounded up) to [to]. */
    fun ticks(from: Long, to: Long): List<Long> {
        val first = floorHalfHour(from).let { if (it < from) it + HALF_HOUR else it }
        return generateSequence(first) { it + HALF_HOUR }.takeWhile { it <= to }.toList()
    }
}
