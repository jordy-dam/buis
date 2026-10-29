package nl.jordy.iptv.data.auth

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.jordy.iptv.data.db.AppDatabase
import nl.jordy.iptv.data.db.CategoryEntity
import nl.jordy.iptv.data.db.FavoriteEntity
import nl.jordy.iptv.data.db.FollowEntity
import nl.jordy.iptv.data.db.MovieInfoEntity
import nl.jordy.iptv.data.db.PlaybackPositionEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.data.settings.SettingsStore
import nl.jordy.iptv.data.xtream.ContentType
import nl.jordy.iptv.data.xtream.XtreamCredentials
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Logging out by accident, or renewing a subscription, must not cost the household its profiles,
 * history and hours of fetched film details. Only another provider's ids make old data meaningless.
 */
@RunWith(RobolectricTestRunner::class)
class SubscriptionDataTest {

    private lateinit var db: AppDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var subscription: SubscriptionData
    private val panel = XtreamCredentials("http://panel.example:8080", "jordy", "pw")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("subscription", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        profiles = ProfileRepository(db, SettingsStore(context))
        subscription = SubscriptionData(context, db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun household(): Long {
        profiles.ensureOne()
        val id = profiles.currentId()
        profiles.togglePinned("MOVIE:12")
        db.favorites().upsert(FavoriteEntity(id, ContentType.LIVE, 101, "NPO 1", null, 1))
        db.playback().upsert(PlaybackPositionEntity(id, ContentType.MOVIE, 5001, 600_000, 6_000_000, 1))
        db.follows().upsert(FollowEntity(id, "Formule 1", 1))
        db.movieInfo().upsert(MovieInfoEntity(5001, "Drama", null, null, null, null, 1))
        db.catalog().upsertCategories(listOf(CategoryEntity(ContentType.LIVE, "1", "NL", 0, 1, "NL")))
        return id
    }

    @Test
    fun `logging out keeps profiles, favourites, history and film details, only the catalog goes`() = runTest {
        val id = household()
        subscription.onLogout(panel)
        assertTrue("catalog is refilled by the next sync", db.catalog().allCategories().isEmpty())
        assertEquals(1, profiles.all().size)
        assertEquals(1, db.favorites().all(id).first().size)
        assertEquals(600_000L, db.playback().get(id, ContentType.MOVIE, 5001)?.positionMs)
        assertEquals("Drama", db.movieInfo().get(5001)?.genre)
    }

    @Test
    fun `a new account on the same panel keeps everything`() = runTest {
        val id = household()
        subscription.onLogout(panel)
        subscription.onLogin(panel.copy(username = "nieuw-abonnement", password = "x"))
        assertEquals(1, db.favorites().all(id).first().size)
        assertEquals("Drama", db.movieInfo().get(5001)?.genre)
        assertEquals("MOVIE:12", profiles.all().single().pinnedCategories)
    }

    @Test
    fun `another provider clears what points at the old ids, the household itself stays`() = runTest {
        val id = household()
        subscription.onLogout(panel)
        subscription.onLogin(XtreamCredentials("http://other-provider.example", "jordy", "pw"))
        assertTrue(db.favorites().all(id).first().isEmpty())
        assertEquals(null, db.playback().get(id, ContentType.MOVIE, 5001))
        assertEquals(null, db.movieInfo().get(5001))
        assertEquals("", profiles.all().single().pinnedCategories)
        assertEquals("profiles and follow terms stay", 1, profiles.all().size)
        assertEquals(listOf("Formule 1"), db.follows().observe(id).first().map { it.term })
    }

    @Test
    fun `Alles wissen leaves nothing, and a later login starts clean without clearing again`() = runTest {
        household()
        subscription.onLogout(panel)
        subscription.wipeAll()
        assertTrue(profiles.all().isEmpty())
        assertEquals(null, db.movieInfo().get(5001))
        // The remembered server is gone too: logging in elsewhere is a first login, not a provider switch.
        profiles.ensureOne()
        db.movieInfo().upsert(MovieInfoEntity(7, "Actie", null, null, null, null, 1))
        subscription.onLogin(XtreamCredentials("http://other-provider.example", "a", "b"))
        assertEquals("Actie", db.movieInfo().get(7)?.genre)
    }
}
