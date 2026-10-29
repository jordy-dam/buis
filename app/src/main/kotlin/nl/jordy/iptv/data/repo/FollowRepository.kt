package nl.jordy.iptv.data.repo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.FollowEntity
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

    /** Matches from now until the end of the guide window (about 36 hours); a kids profile skips adult channels. */
    suspend fun upcoming(nowSec: Long = System.currentTimeMillis() / 1000, hours: Int = 36): List<FollowMatch> =
        FollowMatches.fold(db.follows().matches(profiles.currentId(), nowSec, nowSec + hours * 3600L, profiles.currentPrefs().hideAdult))
}
