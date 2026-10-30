package nl.jordy.iptv.data.profile

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.ProfileEntity
import nl.jordy.iptv.data.settings.SettingsStore
import nl.jordy.iptv.data.settings.UserPrefs
import javax.inject.Inject
import javax.inject.Singleton

val ProfileEntity.prefs
    get() = UserPrefs(
        preferredCountries = preferredCountries.split(',').filter { it.isNotBlank() }.toSet().ifEmpty { setOf("NL") },
        onlyPreferred = onlyPreferred,
        hideAdult = kids,
        sleepMinutes = sleepMinutes,
        breakMinutes = breakMinutes,
        breakLength = breakLength,
        audioLang = audioLang,
        subtitleLang = subtitleLang,
        showClock = showClock,
        bedtime = bedtime,
        bedtimeDays = bedtimeDays,
        autoSwitch = autoSwitch,
    )

/**
 * Household profiles and which one is watching.
 *
 * The active profile is simply the one used last: picking a profile bumps its `lastUsedAt`. That
 * survives process death, so a restored back stack never runs without a profile, and the picker
 * can put focus on the last used one.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val db: AppDatabase,
    private val legacy: SettingsStore,
) {
    private val dao = db.profiles()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val profiles: Flow<List<ProfileEntity>> = dao.observeAll()

    /** Null only between a logout wipe and [ensureOne]. */
    val current: StateFlow<ProfileEntity?> = profiles
        .map { all -> all.maxByOrNull { it.lastUsedAt } }
        // A closed database (a test's teardown) ends the stream quietly instead of crashing the next test.
        .catch { }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val currentId: Flow<Long> = current.filterNotNull().map { it.id }.distinctUntilChanged()
    val prefs: Flow<UserPrefs> = current.filterNotNull().map { it.prefs }.distinctUntilChanged()

    suspend fun currentId(): Long = current.filterNotNull().first().id
    /** Read from the table, not [current], so it already reflects a change made a moment ago. */
    suspend fun currentPrefs(): UserPrefs = fresh().prefs

    /**
     * Makes sure someone can watch: after a fresh install or a logout wipe there is no profile yet.
     * Also hands the 2a global settings to the first profile, once.
     */
    suspend fun ensureOne() {
        db.withTransaction {
            if (dao.all().isEmpty()) {
                val now = System.currentTimeMillis()
                // A fresh household starts with the cat.
                dao.insert(ProfileEntity(name = AppDatabase.FIRST_PROFILE_NAME, avatar = 0, face = 1, lastUsedAt = now, createdAt = now))
            }
        }
        legacy.takeLegacy()?.let { old ->
            val first = dao.all().first()
            dao.update(first.copy(preferredCountries = old.preferredCountries.joinToString(","), onlyPreferred = old.onlyPreferred))
        }
    }

    suspend fun all(): List<ProfileEntity> = dao.all()
    suspend fun get(id: Long): ProfileEntity? = dao.get(id)

    /** Makes [id] the watching profile and waits until every screen sees it. */
    suspend fun select(id: Long) {
        dao.touch(id, System.currentTimeMillis())
        current.first { it?.id == id }
    }

    suspend fun create(name: String, avatar: Int, kids: Boolean, pinHash: String?, face: Int = 0): Long {
        val now = System.currentTimeMillis()
        // Inherit the household's countries: a new profile in an NL+BE home wants NL+BE too.
        val template = runCatching { fresh() }.getOrNull()
        return dao.insert(
            ProfileEntity(
                name = name.trim(),
                avatar = avatar,
                face = face,
                kids = kids,
                pinHash = pinHash,
                preferredCountries = template?.preferredCountries ?: "NL",
                onlyPreferred = template?.onlyPreferred ?: true,
                createdAt = now,
            ),
        )
    }

    suspend fun update(profile: ProfileEntity) = dao.update(profile.copy(name = profile.name.trim()))

    /** Removes the profile with its favourites and history. The last profile can't be removed. */
    suspend fun delete(id: Long): Boolean = db.withTransaction {
        if (dao.all().size <= 1) return@withTransaction false
        dao.delete(id)
        true
    }

    suspend fun setPreferredCountries(codes: Set<String>) = updateCurrent { it.copy(preferredCountries = codes.sorted().joinToString(",")) }
    suspend fun setOnlyPreferred(only: Boolean) = updateCurrent { it.copy(onlyPreferred = only) }
    suspend fun setSleepMinutes(minutes: Int) = updateCurrent { it.copy(sleepMinutes = minutes) }
    suspend fun setBreakMinutes(minutes: Int) = updateCurrent { it.copy(breakMinutes = minutes) }
    suspend fun setBreakLength(minutes: Int) = updateCurrent { it.copy(breakLength = minutes) }
    suspend fun setAudioLang(code: String) = updateCurrent { it.copy(audioLang = code) }
    suspend fun setSubtitleLang(code: String) = updateCurrent { it.copy(subtitleLang = code) }
    suspend fun setShowClock(on: Boolean) = updateCurrent { it.copy(showClock = on) }
    suspend fun setBedtime(minutes: Int) = updateCurrent { it.copy(bedtime = minutes) }
    suspend fun setBedtimeDays(mask: Int) = updateCurrent { it.copy(bedtimeDays = mask) }
    suspend fun setAutoSwitch(on: Boolean) = updateCurrent { it.copy(autoSwitch = on) }

    /** What the active profile pinned in Films and Series. */
    val pinned: Flow<Set<String>> = current.filterNotNull().map { it.pinnedSet() }.distinctUntilChanged()

    /** Pins a category for the active profile, or unpins it when it was pinned. */
    suspend fun togglePinned(key: String) = updateCurrent { p ->
        val set = p.pinnedSet()
        p.copy(pinnedCategories = (if (key in set) set - key else set + key).sorted().joinToString(","))
    }

    private fun ProfileEntity.pinnedSet() = pinnedCategories.split(',').filter { it.isNotBlank() }.toSet()

    private suspend fun updateCurrent(change: (ProfileEntity) -> ProfileEntity) = dao.update(change(fresh()))

    private suspend fun fresh(): ProfileEntity = dao.get(currentId()) ?: current.filterNotNull().first()

    companion object {
        const val MAX_PROFILES = 8
        const val FACES = 5
        const val COLOURS = 8

        /** A new profile's look: the first drawn face and colour nobody uses yet, so faces stay apart. */
        fun defaultLook(profiles: List<ProfileEntity>): Pair<Int, Int> {
            val faces = profiles.map { it.face }.toSet()
            val colours = profiles.map { it.avatar }.toSet()
            val face = (1..FACES).firstOrNull { it !in faces } ?: 1
            val colour = (0 until COLOURS).firstOrNull { it !in colours } ?: 0
            return face to colour
        }

        /**
         * PIN hashes that unlock profile management. Once there is a kids profile, adding, editing
         * and removing profiles asks for the PIN of a grown-up profile, so a child can't switch
         * their own kids setting off or make a new profile without one. Empty: no lock.
         */
        fun managementLock(profiles: List<ProfileEntity>): List<String> =
            if (profiles.none { it.kids }) emptyList() else profiles.filter { !it.kids }.mapNotNull { it.pinHash }

        /**
         * The PIN checks before [profile] may be edited, in order; each entry is a set of hashes of
         * which one must match. A grown-up's PIN (when management is locked) and the profile's own
         * PIN; one entry when they're the same PIN.
         */
        fun editGates(profile: ProfileEntity, profiles: List<ProfileEntity>): List<List<String>> {
            val lock = managementLock(profiles)
            val own = profile.pinHash
            return listOfNotNull(
                lock.takeIf { it.isNotEmpty() && own !in it },
                own?.let { listOf(it) },
            )
        }
    }
}
