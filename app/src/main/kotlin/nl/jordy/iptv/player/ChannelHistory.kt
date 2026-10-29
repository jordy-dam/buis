package nl.jordy.iptv.player

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last two live channels, for the whole app run: Left in the player flips between them, also when
 * the other one was opened from a different category (F1 in Sport, then NPO 1 from Nederland).
 */
@Singleton
class ChannelHistory @Inject constructor() {

    /** A channel and the list it was zapped through, so going back restores that list too. */
    data class Entry(val streamId: Int, val name: String, val categoryId: String?)

    var current: Entry? = null
        private set
    var previous: Entry? = null
        private set

    /** Call on every channel that starts playing. Watching [previous] again swaps the two. */
    fun watched(entry: Entry) {
        if (entry.streamId == current?.streamId) {
            current = entry
            return
        }
        previous = current
        current = entry
    }
}
