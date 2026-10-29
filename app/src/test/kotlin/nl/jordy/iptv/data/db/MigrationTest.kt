package nl.jordy.iptv.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Updating the app on the tv must never cost the household its favourites or where they left off. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun `1 to 2 keeps favourites and positions, empties the catalog so it re-syncs`() {
        helper.createDatabase(DB, 1).use { it.insertPhase1Data() }

        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            assertEquals("favourites survive", 1, db.count("favorite"))
            assertEquals("resume positions survive", 1, db.count("playback_position"))
            db.query("SELECT positionMs FROM playback_position WHERE itemId = 5001").use { it.moveToFirst(); assertEquals(62000L, it.getLong(0)) }
            assertEquals("catalog emptied for a fresh sync", 0, db.count("live_channel"))
            assertEquals(0, db.count("category"))
            assertEquals("no sync info, so the next launch syncs", 0, db.count("sync_info"))
        }
    }

    @Test
    fun `2 to 3 moves favourites and positions onto a first profile with the old defaults`() {
        helper.createDatabase(DB, 2).use { db ->
            db.insertPhase1Data()
            db.execSQL("INSERT INTO favorite (type, itemId, name, imageUrl, addedAt) VALUES ('MOVIE', 5001, 'Kill Bill', NULL, 4)")
        }

        helper.runMigrationsAndValidate(DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT id, name, preferredCountries, onlyPreferred, kids, pinHash FROM profile").use {
                assertEquals("exactly one profile", 1, it.count)
                it.moveToFirst()
                assertEquals(1L, it.getLong(0))
                assertEquals(AppDatabase.FIRST_PROFILE_NAME, it.getString(1))
                assertEquals("NL", it.getString(2))
                assertEquals("country filter stays on", 1, it.getInt(3))
                assertEquals("not a kids profile", 0, it.getInt(4))
                assertEquals("no PIN", true, it.isNull(5))
            }
            assertEquals("all favourites on profile 1", 2, db.count("favorite WHERE profileId = 1"))
            db.query("SELECT positionMs, durationMs FROM playback_position WHERE profileId = 1 AND type = 'MOVIE' AND itemId = 5001").use {
                it.moveToFirst()
                assertEquals(62000L, it.getLong(0))
                assertEquals(180000L, it.getLong(1))
            }
            assertEquals("catalog emptied so the sync fills category.adult", 0, db.count("category"))
            assertEquals(0, db.count("sync_info"))
        }
    }

    @Test
    fun `1 to 3 in one update keeps a phase 1 household's data`() {
        helper.createDatabase(DB, 1).use { it.insertPhase1Data() }

        helper.runMigrationsAndValidate(DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            assertEquals(1, db.count("favorite WHERE profileId = 1 AND itemId = 101"))
            assertEquals(1, db.count("playback_position WHERE profileId = 1 AND itemId = 5001"))
        }
    }

    @Test
    fun `3 to 4 adds the guide tables and keeps every profile's data`() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt) VALUES (1, 'Anna', 0, NULL, 'NL', 1, 0, 5, 1)")
            db.execSQL("INSERT INTO profile (id, name, avatar, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt) VALUES (2, 'Bas', 1, NULL, 'BE', 1, 1, 4, 2)")
            db.execSQL("INSERT INTO favorite (profileId, type, itemId, name, imageUrl, addedAt) VALUES (2, 'LIVE', 101, 'NPO 1', NULL, 1)")
            db.execSQL("INSERT INTO playback_position (profileId, type, itemId, positionMs, durationMs, updatedAt) VALUES (1, 'MOVIE', 5001, 62000, 180000, 2)")
        }

        helper.runMigrationsAndValidate(DB, 4, true).use { db ->
            assertEquals(2, db.count("profile"))
            assertEquals(1, db.count("favorite WHERE profileId = 2"))
            assertEquals(1, db.count("playback_position WHERE profileId = 1"))
            assertEquals("guide starts empty", 0, db.count("epg_programme"))
        }
    }

    @Test
    fun `4 to 5 adds film details and keeps profiles, positions and the guide`() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt) VALUES (1, 'Anna', 0, NULL, 'NL', 1, 0, 5, 1)")
            db.execSQL("INSERT INTO playback_position (profileId, type, itemId, positionMs, durationMs, updatedAt) VALUES (1, 'MOVIE', 5001, 62000, 180000, 2)")
            db.execSQL("INSERT INTO epg_programme (channelId, start, stop, title, description) VALUES ('npo1.nl', 1, 2, 'Journaal', NULL)")
        }
        helper.runMigrationsAndValidate(DB, 5, true).use { db ->
            assertEquals(1, db.count("profile"))
            assertEquals(1, db.count("playback_position"))
            assertEquals("the guide isn't thrown away", 1, db.count("epg_programme"))
            assertEquals(0, db.count("movie_info"))
        }
    }

    @Test
    fun `5 to 6 adds drawn avatars, existing profiles keep their letter`() {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt) VALUES (1, 'Anna', 3, NULL, 'NL', 1, 0, 5, 1)")
            db.execSQL("INSERT INTO movie_info (streamId, genre, `cast`, director, plot, rating, fetchedAt, failed) VALUES (7, 'Drama', NULL, NULL, NULL, NULL, 1, 0)")
        }
        helper.runMigrationsAndValidate(DB, 6, true).use { db ->
            db.query("SELECT avatar, face FROM profile WHERE id = 1").use { it.moveToFirst(); assertEquals(3, it.getInt(0)); assertEquals(0, it.getInt(1)) }
            assertEquals("film details stay", 1, db.count("movie_info"))
        }
    }

    @Test
    fun `6 to 7 adds TMDB ids, re-syncs the catalog and re-fetches film details without losing them`() {
        helper.createDatabase(DB, 6).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, face, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt) VALUES (1, 'Anna', 3, 2, NULL, 'NL', 1, 0, 5, 1)")
            db.execSQL("INSERT INTO movie_info (streamId, genre, `cast`, director, plot, rating, fetchedAt, failed) VALUES (7, 'Drama', NULL, NULL, NULL, NULL, 99, 0)")
            db.execSQL("INSERT INTO sync_info (id, lastSyncAt, generation) VALUES (0, 3, 1)")
        }
        helper.runMigrationsAndValidate(DB, 7, true).use { db ->
            db.query("SELECT face FROM profile").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
            db.query("SELECT genre, failed, fetchedAt FROM movie_info").use {
                it.moveToFirst()
                assertEquals("genres stay usable meanwhile", "Drama", it.getString(0))
                assertEquals("queued for a re-fetch", 1, it.getInt(1))
                assertEquals(0L, it.getLong(2))
            }
            assertEquals("next launch syncs the catalog", 0, db.count("sync_info"))
        }
    }

    @Test
    fun `7 to 8 adds a sleep timer that starts off and keeps the profile`() {
        helper.createDatabase(DB, 7).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, face, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt) VALUES (1, 'Anna', 3, 2, NULL, 'NL,BE', 1, 0, 5, 1)")
        }
        helper.runMigrationsAndValidate(DB, 8, true).use { db ->
            db.query("SELECT sleepMinutes, preferredCountries FROM profile").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
                assertEquals("NL,BE", it.getString(1))
            }
        }
    }

    @Test
    fun `8 to 9 adds pinned categories, empty, and keeps the sleep timer`() {
        helper.createDatabase(DB, 8).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, face, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt, sleepMinutes) VALUES (1, 'Anna', 3, 2, NULL, 'NL', 1, 0, 5, 1, 60)")
        }
        helper.runMigrationsAndValidate(DB, 9, true).use { db ->
            db.query("SELECT pinnedCategories, sleepMinutes FROM profile").use {
                it.moveToFirst()
                assertEquals("", it.getString(0))
                assertEquals(60, it.getInt(1))
            }
        }
    }

    @Test
    fun `9 to 10 adds catch-up days and re-syncs the catalog, profiles stay`() {
        helper.createDatabase(DB, 9).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, face, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt, sleepMinutes, pinnedCategories) VALUES (1, 'Anna', 3, 2, NULL, 'NL', 1, 0, 5, 1, 30, 'MOVIE:12')")
            db.execSQL("INSERT INTO sync_info (id, lastSyncAt, generation) VALUES (0, 3, 1)")
        }
        helper.runMigrationsAndValidate(DB, 10, true).use { db ->
            db.query("SELECT pinnedCategories, sleepMinutes FROM profile").use {
                it.moveToFirst()
                assertEquals("MOVIE:12", it.getString(0))
                assertEquals(30, it.getInt(1))
            }
            assertEquals("next launch syncs the catalog", 0, db.count("sync_info"))
        }
    }

    @Test
    fun `10 to 11 adds an empty follow list and keeps the catalog`() {
        helper.createDatabase(DB, 10).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, face, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt, sleepMinutes, pinnedCategories) VALUES (1, 'Anna', 3, 2, NULL, 'NL', 1, 0, 5, 1, 0, '')")
            db.execSQL("INSERT INTO sync_info (id, lastSyncAt, generation) VALUES (0, 3, 1)")
        }
        helper.runMigrationsAndValidate(DB, 11, true).use { db ->
            assertEquals(0, db.count("follow"))
            assertEquals("no catalog re-sync for this one", 1, db.count("sync_info"))
        }
    }

    @Test
    fun `11 to 12 adds an empty reminder list`() {
        helper.createDatabase(DB, 11).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, face, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt, sleepMinutes, pinnedCategories) VALUES (1, 'Anna', 3, 2, NULL, 'NL', 1, 0, 5, 1, 0, '')")
            db.execSQL("INSERT INTO follow (profileId, term, addedAt) VALUES (1, 'Formule 1', 1)")
        }
        helper.runMigrationsAndValidate(DB, 12, true).use { db ->
            assertEquals(0, db.count("reminder"))
            assertEquals("follow terms stay", 1, db.count("follow"))
        }
    }

    @Test
    fun `12 to 13 adds a Pauzemoment that starts off and keeps the sleep timer`() {
        helper.createDatabase(DB, 12).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, face, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt, sleepMinutes, pinnedCategories) VALUES (1, 'Anna', 3, 2, NULL, 'NL', 1, 0, 5, 1, 90, '')")
        }
        helper.runMigrationsAndValidate(DB, 13, true).use { db ->
            db.query("SELECT breakMinutes, sleepMinutes FROM profile").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
                assertEquals(90, it.getInt(1))
            }
        }
    }

    @Test
    fun `13 to 14 adds a Pauzeduur of one minute and keeps the Pauzemoment`() {
        helper.createDatabase(DB, 13).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, face, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt, sleepMinutes, breakMinutes, pinnedCategories) VALUES (1, 'Anna', 3, 2, NULL, 'NL', 1, 0, 5, 1, 0, 60, '')")
        }
        helper.runMigrationsAndValidate(DB, 14, true).use { db ->
            db.query("SELECT breakLength, breakMinutes FROM profile").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(60, it.getInt(1))
            }
        }
    }

    @Test
    fun `14 to 15 adds track languages and the clock with today's behaviour`() {
        helper.createDatabase(DB, 14).use { db ->
            db.execSQL("INSERT INTO profile (id, name, avatar, face, pinHash, preferredCountries, onlyPreferred, kids, lastUsedAt, createdAt, sleepMinutes, breakMinutes, breakLength, pinnedCategories) VALUES (1, 'Anna', 3, 2, NULL, 'NL', 1, 0, 5, 1, 0, 60, 5, '')")
        }
        helper.runMigrationsAndValidate(DB, 15, true).use { db ->
            db.query("SELECT audioLang, subtitleLang, showClock, breakLength FROM profile").use {
                it.moveToFirst()
                assertEquals("stream default audio", "", it.getString(0))
                assertEquals("subtitles off", "", it.getString(1))
                assertEquals(1, it.getInt(2))
                assertEquals(5, it.getInt(3))
            }
        }
    }

    private fun SupportSQLiteDatabase.insertPhase1Data() {
        execSQL("INSERT INTO favorite (type, itemId, name, imageUrl, addedAt) VALUES ('LIVE', 101, '┃NL┃ NPO 1 HD', NULL, 1)")
        execSQL("INSERT INTO playback_position (type, itemId, positionMs, durationMs, updatedAt) VALUES ('MOVIE', 5001, 62000, 180000, 2)")
        execSQL("INSERT INTO category (type, id, name, sortIndex, generation) VALUES ('LIVE', '1', '┃NL┃ ALGEMEEN', 0, 1)")
        execSQL("INSERT INTO live_channel (categoryId, streamId, name, logoUrl, epgChannelId, sortIndex, generation) VALUES ('1', 101, '┃NL┃ NPO 1 HD', NULL, NULL, 0, 1)")
        execSQL("INSERT INTO sync_info (id, lastSyncAt, generation) VALUES (0, 3, 1)")
    }

    private fun SupportSQLiteDatabase.count(tableAndWhere: String) =
        query("SELECT COUNT(*) FROM $tableAndWhere").use { it.moveToFirst(); it.getInt(0) }

    private companion object {
        const val DB = "migration-test.db"
    }
}
