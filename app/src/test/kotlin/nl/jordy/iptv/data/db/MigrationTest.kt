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
