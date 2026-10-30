package nl.jordy.iptv.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.GuideChannelEntity
import nl.jordy.iptv.data.db.GuideEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/** A guide with its channels in the profile's order. */
data class Guide(val id: Long, val name: String, val channels: List<GuideChannelEntity>)

/**
 * The watching profile's own guides ("Mijn gids", "Sport"): channels from any category, in an order
 * the profile chooses. They replace favourite channels; the flows follow a profile switch.
 */
@Singleton
class GuideRepository @Inject constructor(private val db: AppDatabase, private val profiles: ProfileRepository) {
    private val dao = db.guides()

    @OptIn(ExperimentalCoroutinesApi::class)
    val guides: Flow<List<Guide>> = profiles.currentId.flatMapLatest { profile ->
        combine(dao.guides(profile), dao.channelsOf(profile)) { guides, channels ->
            val byGuide = channels.groupBy { it.guideId }
            guides.map { Guide(it.id, it.name, byGuide[it.id].orEmpty()) }
        }
    }

    /** Channels in any of the profile's guides: the heart in Live TV. */
    val channelIds: Flow<Set<Int>> = guides.map { g -> g.flatMap { it.channels }.map { it.streamId }.toSet() }

    /** A blank name becomes "Mijn gids". Returns the new guide's id. */
    suspend fun create(name: String): Long {
        val profile = profiles.currentId()
        return dao.insert(GuideEntity(profileId = profile, name = nameOrDefault(name), position = dao.guidesNow(profile).size))
    }

    suspend fun rename(id: Long, name: String) = dao.rename(id, nameOrDefault(name))

    suspend fun delete(id: Long) = dao.delete(id)

    /** Adds the channel to the end of the guide, or takes it out. Returns true when it's now in. */
    suspend fun toggle(guideId: Long, streamId: Int, name: String, logoUrl: String?): Boolean = db.withTransaction {
        val channels = dao.channels(guideId)
        if (channels.any { it.streamId == streamId }) {
            dao.removeChannel(guideId, streamId)
            false
        } else {
            dao.upsertChannels(listOf(GuideChannelEntity(guideId, streamId, name, logoUrl, (channels.maxOfOrNull { it.position } ?: -1) + 1)))
            true
        }
    }

    /** Long-press with one guide or none: toggle in that guide, creating "Mijn gids" first when there's none. */
    suspend fun toggleInOnlyGuide(streamId: Int, name: String, logoUrl: String?): Boolean {
        val guide = dao.guidesNow(profiles.currentId()).firstOrNull()?.id ?: create("")
        return toggle(guide, streamId, name, logoUrl)
    }

    suspend fun remove(guideId: Long, streamId: Int) = dao.removeChannel(guideId, streamId)

    /** Moves a channel one place up ([delta] -1) or down (+1); positions are renumbered 0..n on the way. */
    suspend fun move(guideId: Long, streamId: Int, delta: Int) = db.withTransaction {
        val moved = moved(dao.channels(guideId), streamId, delta)
        dao.upsertChannels(moved.mapIndexed { i, c -> c.copy(position = i) })
    }

    /** Channels of one guide in order, for zapping through it in the player. */
    suspend fun channels(guideId: Long): List<GuideChannelEntity> = dao.channels(guideId)

    companion object {
        const val DEFAULT_NAME = AppDatabase.FIRST_GUIDE_NAME

        fun nameOrDefault(name: String): String = name.trim().ifEmpty { DEFAULT_NAME }

        /** [channels] with the one for [streamId] swapped with its neighbour; unchanged at either end. */
        fun moved(channels: List<GuideChannelEntity>, streamId: Int, delta: Int): List<GuideChannelEntity> {
            val from = channels.indexOfFirst { it.streamId == streamId }
            val to = from + delta
            if (from < 0 || to !in channels.indices) return channels
            return channels.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
}
