package nl.jordy.iptv.data.repo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.FollowEntity
import nl.jordy.iptv.data.db.ReminderEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/** What the watching profile follows, and the guide programmes that match it. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FollowRepository @Inject constructor(private val db: AppDatabase, private val profiles: ProfileRepository) {

    val terms: Flow<List<String>> = profiles.current.filterNotNull()
        .flatMapLatest { db.follows().observe(it.id) }
        .map { list -> list.map(FollowEntity::term) }

    /** Two letters at least: "F" would match half the guide. */
    suspend fun add(term: String) {
        val t = term.trim().replace(Regex("""\s+"""), " ")
        if (t.length >= 2) db.follows().upsert(FollowEntity(profiles.currentId(), t, System.currentTimeMillis()))
    }

    suspend fun remove(term: String) = db.follows().delete(profiles.currentId(), term)

    /**
     * Matches from now until the end of the guide window (about 36 hours), followed terms and one-off
     * reminders together; a kids profile skips adult channels for terms.
     */
    suspend fun upcoming(nowSec: Long = System.currentTimeMillis() / 1000, hours: Int = 36): List<FollowMatch> {
        val id = profiles.currentId()
        val followed = FollowMatches.fold(db.follows().matches(id, nowSec, nowSec + hours * 3600L, profiles.currentPrefs().hideAdult))
        val oneOffs = db.reminders().upcoming(id, nowSec).map {
            FollowMatch(it.title, it.title, it.start, it.stop, it.streamId, it.channelName, it.categoryId, it.hasArchive, it.logoUrl, oneOff = true)
        }
        return FollowMatches.merge(followed, oneOffs)
    }

    /** "streamId:start" of the profile's one-off reminders, for marking them in the guide. */
    val reminderKeys: Flow<Set<String>> = profiles.current.filterNotNull()
        .flatMapLatest { db.reminders().observe(it.id, System.currentTimeMillis() / 1000) }
        .map { list -> list.map { "${it.streamId}:${it.start}" }.toSet() }

    /** OK on a programme in the guide: a reminder for just that one, or off again. */
    suspend fun toggleReminder(r: ReminderEntity) {
        val id = profiles.currentId()
        if (db.reminders().upcoming(id, 0).any { it.streamId == r.streamId && it.start == r.start }) db.reminders().delete(id, r.streamId, r.start)
        else db.reminders().upsert(r.copy(profileId = id))
    }

    suspend fun clearOverReminders(nowSec: Long = System.currentTimeMillis() / 1000) = db.reminders().deleteOver(nowSec)
}
