package nl.jordy.iptv.data.db

import androidx.room.testing.MigrationTestHelper
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
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO favorite (type, itemId, name, imageUrl, addedAt) VALUES ('LIVE', 101, '┃NL┃ NPO 1 HD', NULL, 1)")
            db.execSQL("INSERT INTO playback_position (type, itemId, positionMs, durationMs, updatedAt) VALUES ('MOVIE', 5001, 62000, 180000, 2)")
            db.execSQL("INSERT INTO category (type, id, name, sortIndex, generation) VALUES ('LIVE', '1', '┃NL┃ ALGEMEEN', 0, 1)")
            db.execSQL("INSERT INTO live_channel (categoryId, streamId, name, logoUrl, epgChannelId, sortIndex, generation) VALUES ('1', 101, '┃NL┃ NPO 1 HD', NULL, NULL, 0, 1)")
            db.execSQL("INSERT INTO sync_info (id, lastSyncAt, generation) VALUES (0, 3, 1)")
        }

        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            fun count(table: String) = db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
            assertEquals("favourites survive", 1, count("favorite"))
            assertEquals("resume positions survive", 1, count("playback_position"))
            db.query("SELECT positionMs FROM playback_position WHERE itemId = 5001").use { it.moveToFirst(); assertEquals(62000L, it.getLong(0)) }
            assertEquals("catalog emptied for a fresh sync", 0, count("live_channel"))
            assertEquals(0, count("category"))
            assertEquals("no sync info, so the next launch syncs", 0, count("sync_info"))
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
