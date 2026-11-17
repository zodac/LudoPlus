package net.zodac.ludo.data.records

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each schema version's database opens in this build, its rows kept: written as that version made it (its SQL from
 * `app/shared/schemas/`), then opened through [MIGRATIONS] on Android's SQLite, which Room checks against this build's
 * schema.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a version 1 database opens with its games kept - none with house rules - each with a uuid of its own`() = runBlocking {
        val file = context.getDatabasePath("migration-test.db").apply {
            parentFile?.mkdirs()
            delete()
        }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            VERSION_1_SQL.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO results (playerName, playerKey, isPrimaryPlayer, gameModeId, playedUnmodified, playerCount, endedAtEpochMillis, " +
                    "playMillis, place, won, finishMillis, turns, captures, timesCaptured, pawnsHome) " +
                    "VALUES ('Sam', 'sam', 1, 'classic', 1, 4, 5, 6, 1, 1, 7, 40, 2, 1, 4), " +
                    "('Alex', 'alex', 0, 'classic', 1, 4, 5, 6, 2, 0, 7, 40, 2, 1, 3)",
            )
            db.version = 1
        }

        val database = Room.databaseBuilder<AppDatabase>(context, file.absolutePath).buildAppDatabase()
        try {
            val rows = database.gameResultDao().allByPlayer()
            assertEquals(listOf("Alex" to false, "Sam" to false), rows.map { it.playerName to it.houseRules })
            // Version 3's uuids: one each, random, for the cloud save and progress files to tell rows apart by.
            assertEquals(2, rows.map { it.uuid }.toSet().size)
            rows.forEach { assertTrue(it.uuid, Regex("[0-9a-f]{32}").matches(it.uuid)) }
            assertEquals(1, database.gameResultDao().leaderboardCount("classic", playerCount = 4))
        } finally {
            database.close()
            file.delete()
        }
    }

    private companion object {
        /** Version 1's tables and Room's record of it, from `app/shared/schemas/.../1.json`. */
        val VERSION_1_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playerName` TEXT NOT NULL, " +
                "`playerKey` TEXT NOT NULL, `isPrimaryPlayer` INTEGER NOT NULL, `gameModeId` TEXT NOT NULL, `playedUnmodified` INTEGER NOT NULL, " +
                "`playerCount` INTEGER NOT NULL, `endedAtEpochMillis` INTEGER NOT NULL, `playMillis` INTEGER NOT NULL, `place` INTEGER NOT NULL, " +
                "`won` INTEGER NOT NULL, `finishMillis` INTEGER NOT NULL, `turns` INTEGER NOT NULL, `captures` INTEGER NOT NULL, " +
                "`timesCaptured` INTEGER NOT NULL, `pawnsHome` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_results_leaderboard` ON `results` (`gameModeId`, `playedUnmodified`, `won`, `turns`, `captures`, `endedAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_results_player` ON `results` (`playerKey`, `endedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1b757f88353c035e970eccee9cd2ac64')",
        )
    }
}
