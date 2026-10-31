package nl.jordy.iptv.data.repo

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.CalendarEntity
import nl.jordy.iptv.data.db.CalendarEventEntity
import nl.jordy.iptv.data.db.ChannelPick
import nl.jordy.iptv.data.profile.ProfileRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** A calendar to pick without typing a link on the remote, with what to search the channel by. */
data class CalendarPreset(val group: String, val label: String, val name: String, val url: String, val channelQuery: String)

/**
 * Calendars a profile follows, each tied to one channel: for sports whose own channels have no
 * guide (Viaplay's F1 streams, F1 TV Pro), so following a term can't find them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CalendarRepository @Inject constructor(
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
    private val http: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastAttempt = 0L

    val calendars: Flow<List<CalendarEntity>> = profiles.current.filterNotNull().flatMapLatest { db.calendars().observe(it.id) }

    /** Per link the first appointment still to come. */
    fun next() = db.calendars().next(System.currentTimeMillis() / 1000)

    suspend fun channels(query: String): List<ChannelPick> =
        if (query.isBlank()) emptyList() else db.calendars().channels(query.trim(), profiles.currentPrefs().hideAdult)

    /**
     * Fetches [input] and follows it on [channel]. Fails (and saves nothing) when the link can't be
     * fetched or isn't a calendar, so a typo never sits there silently reminding of nothing.
     */
    suspend fun add(input: String, name: String?, channel: ChannelPick): Result<Unit> = runCatching {
        val url = Ics.normalizeUrl(input)
        val text = download(url)
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.calendars().upsert(
                CalendarEntity(
                    profiles.currentId(), url, name?.takeIf { it.isNotBlank() } ?: Ics.name(text) ?: hostOf(url),
                    channel.streamId, channel.name, channel.categoryId, channel.logoUrl, channel.archiveDays > 0 || channel.catchup, now, now,
                ),
            )
            store(url, text, now)
        }
    }.onFailure { Log.w(TAG, "add calendar: $it") }

    /** Another channel for a calendar the profile follows. */
    suspend fun setChannel(calendar: CalendarEntity, channel: ChannelPick) = db.calendars().upsert(
        calendar.copy(streamId = channel.streamId, channelName = channel.name, categoryId = channel.categoryId, logoUrl = channel.logoUrl, hasArchive = channel.archiveDays > 0 || channel.catchup),
    )

    suspend fun remove(url: String) = db.withTransaction {
        db.calendars().delete(profiles.currentId(), url)
        db.calendars().dropOrphanEvents()
    }

    /** Appointments from [from] to [until] for the watching profile, as reminders on each calendar's channel. */
    suspend fun matches(from: Long, until: Long): List<FollowMatch> = db.calendars().matches(profiles.currentId(), from, until).map {
        FollowMatch(it.name, it.title, it.start, it.stop, it.streamId, it.channelName, it.categoryId, it.hasArchive, it.logoUrl, fromCalendar = true)
    }

    /** In a quiet moment (the app comes to the front): fetches links older than [STALE_MS]; a failure waits an hour. */
    fun refreshIfStale() {
        scope.launch {
            val now = System.currentTimeMillis()
            if (now - lastAttempt < RETRY_AFTER_MS) return@launch
            val stale = db.calendars().links().filter { now - it.fetchedAt > STALE_MS }
            if (stale.isEmpty()) return@launch
            lastAttempt = now
            stale.forEach { link -> runCatching { download(link.url) }.onSuccess { text -> db.withTransaction { store(link.url, text, now) } } }
        }
    }

    private suspend fun store(url: String, text: String, now: Long) {
        val events = Ics.parse(text, now / 1000).map { CalendarEventEntity(url, it.start, it.stop, it.title) }
        db.calendars().clearEvents(url)
        events.chunked(500).forEach { db.calendars().insertEvents(it) }
        db.calendars().setFetched(url, now)
    }

    private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            resp.body.string().also { check(Ics.looksLikeCalendar(it)) { "not a calendar" } }
        }
    }

    private fun hostOf(url: String) = url.substringAfter("://").substringBefore('/').removePrefix("www.")

    companion object {
        private const val TAG = "Calendars"
        const val STALE_MS = 12 * 60 * 60 * 1000L
        private const val RETRY_AFTER_MS = 60 * 60 * 1000L

        private fun fotmob(team: Int) = "https://pub.fotmob.com/prod/pub/api/v2/calendar/team/$team.ics"

        /**
         * Calendars to browse, checked by hand (2026-10-31) to be live and to carry kick-off times.
         * The channel search starts where the sport usually is: Viaplay for F1, ESPN for the Eredivisie.
         */
        val presets = listOf(
            CalendarPreset("Motorsport", "Formule 1 · alle sessies", "Formule 1", "https://f1calendar.com/download/f1-calendar_p1_p2_p3_qualifying_sprint_gp.ics", "viaplay formule 1"),
            CalendarPreset("Motorsport", "Formule 1 · kwalificatie, sprint en race", "Formule 1", "https://f1calendar.com/download/f1-calendar_qualifying_sprint_gp.ics", "viaplay formule 1"),
            CalendarPreset("Voetbal", "FC Utrecht", "FC Utrecht", fotmob(9908), "espn"),
            CalendarPreset("Voetbal", "Ajax", "Ajax", fotmob(8593), "espn"),
            CalendarPreset("Voetbal", "PSV", "PSV", fotmob(8640), "espn"),
            CalendarPreset("Voetbal", "Feyenoord", "Feyenoord", fotmob(10235), "espn"),
            CalendarPreset("Voetbal", "Oranje", "Oranje", fotmob(6708), "npo 1"),
            CalendarPreset("Voetbal", "Eredivisie · alle wedstrijden", "Eredivisie", "https://www.smartcalendars.ai/cal/dc21165147e184d0e549a30e814f017bd98f8afbfd798b8fddc33ee7ea64ed51.ics", "espn"),
        )
    }
}
