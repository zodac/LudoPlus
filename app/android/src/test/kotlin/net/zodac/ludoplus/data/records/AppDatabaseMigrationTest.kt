package net.zodac.ludoplus.data.records

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each schema version's database opens in this build, its rows kept: written as that version made it (its tables,
 * indices and Room's record of it, from its exported schema in `app/shared/schemas/`), then opened through [MIGRATIONS]
 * on Android's SQLite, which Room checks against this build's schema.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a version 1 database opens with its games kept - none with house rules - each with a uuid of its own - and Quick dropped`() =
        opensFrom(1)

    @Test
    fun `a version 2 database opens with its house rules kept - each game with a uuid of its own - and Quick dropped`() = opensFrom(2)

    @Test
    fun `a version 3 database opens with its uuids kept - and Quick dropped`() = opensFrom(3)

    @Test
    fun `a version 4 database opens with its squares kept - and none travelled`() = opensFrom(4)

    @Test
    fun `a version 5 database opens with its squares travelled kept`() = opensFrom(5)

    @Test
    fun `every exported schema but the newest has a test here`() {
        val versions = schemaDirectory.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.sorted()
        assertEquals((1..NEWEST_TESTED + 1).toList(), versions)
    }

    /**
     * Writes [version]'s database with two games of Classic (Sam's win, Alex's loss) and, before version 4, one of Quick,
     * each with what that version kept, then opens it in this build and checks what each later version added.
     */
    private fun opensFrom(version: Int) = runBlocking {
        val file = context.getDatabasePath("migration-test-$version.db").apply {
            parentFile?.mkdirs()
            delete()
        }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schemaSql(version).forEach(db::execSQL)
            insert(db, version, "Sam", place = 1, mode = "classic", uuid = SAM_UUID, houseRules = false, squares = 7, squaresMoved = 50)
            insert(db, version, "Alex", place = 2, mode = "classic", uuid = ALEX_UUID, houseRules = true, squares = 0, squaresMoved = 30)
            // A game of Quick, a mode since gone: version 4 drops it.
            if (version < 4) insert(db, version, "Sam", place = 1, mode = "quick", uuid = QUICK_UUID, houseRules = false, squares = 0, squaresMoved = 0)
            db.version = version
        }

        val database = Room.databaseBuilder<AppDatabase>(context, file.absolutePath).buildAppDatabase()
        try {
            val rows = database.gameResultDao().allByPlayer()
            assertEquals(listOf("Alex" to "classic", "Sam" to "classic"), rows.map { it.playerName to it.gameModeId })
            // Version 2's house rules: none before them.
            assertEquals(listOf(version >= 2, false), rows.map { it.houseRules })
            // Version 3's uuids: one each, random before them, for the cloud save and progress files to tell rows apart by.
            if (version >= 3) {
                assertEquals(listOf(ALEX_UUID, SAM_UUID), rows.map { it.uuid })
            } else {
                assertEquals(2, rows.map { it.uuid }.toSet().size)
                rows.forEach { assertTrue(it.uuid, Regex("[0-9a-f]{32}").matches(it.uuid)) }
            }
            // Version 4's squares: none before Colour Rush.
            assertEquals(listOf(0, if (version >= 4) 7 else 0), rows.map { it.squares })
            // Version 5's squares travelled: unknown before it.
            assertEquals(if (version >= 5) listOf(30, 50) else listOf(null, null), rows.map { it.squaresMoved })
            // Version 6's Leaderboard index, with the player count in it.
            assertEquals(1, database.gameResultDao().leaderboardCount("classic", playerCount = 4))
            assertEquals(0, database.gameResultDao().leaderboardCount("classic", playerCount = 2))
            assertEquals(listOf("Sam"), database.gameResultDao().leaderboard("classic", playerCount = 4, limit = 10, offset = 0).map { it.playerName })
        } finally {
            database.close()
            file.delete()
        }
    }

    /** [version]'s tables, indices, and Room's record of its identity, as its exported schema has them. */
    private fun schemaSql(version: Int): List<String> {
        val database = JSONObject(schemaDirectory.resolve("$version.json").readText()).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        val sql = mutableListOf<String>()
        for (e in 0 until entities.length()) {
            val entity = entities.getJSONObject(e)
            val table = entity.getString("tableName")
            sql += entity.getString("createSql").replace("\${TABLE_NAME}", table)
            val indices = entity.optJSONArray("indices") ?: continue
            for (i in 0 until indices.length()) sql += indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table)
        }
        val setup = database.getJSONArray("setupQueries")
        for (q in 0 until setup.length()) sql += setup.getString(q)
        return sql
    }

    /** One game's row, in a 4-player game, with only the columns [version] had. */
    @Suppress("LongParameterList")
    private fun insert(
        db: SQLiteDatabase,
        version: Int,
        name: String,
        place: Int,
        mode: String,
        uuid: String,
        houseRules: Boolean,
        squares: Int,
        squaresMoved: Int,
    ) {
        val columns = linkedMapOf<String, Any>(
            "playerName" to name,
            "playerKey" to name.lowercase(),
            "isPrimaryPlayer" to if (name == "Sam") 1 else 0,
            "gameModeId" to mode,
            "playedUnmodified" to 1,
            "playerCount" to 4,
            "endedAtEpochMillis" to 5,
            "playMillis" to 6,
            "place" to place,
            "won" to if (place == 1) 1 else 0,
            "finishMillis" to 7,
            "turns" to 40,
            "captures" to 2,
            "timesCaptured" to 1,
            "pawnsHome" to 4 - place,
        )
        if (version >= 2) columns["houseRules"] = if (houseRules) 1 else 0
        if (version >= 3) columns["uuid"] = uuid
        if (version >= 4) columns["squares"] = squares
        if (version >= 5) columns["squaresMoved"] = squaresMoved
        db.execSQL(
            "INSERT INTO results (${columns.keys.joinToString()}) VALUES (${columns.keys.joinToString { "?" }})",
            columns.values.toTypedArray(),
        )
    }

    private companion object {
        /** The newest version a test here starts from: every one before this build's. */
        const val NEWEST_TESTED = 5

        const val SAM_UUID = "0123456789abcdef0123456789abcdef"
        const val ALEX_UUID = "fedcba9876543210fedcba9876543210"
        const val QUICK_UUID = "00000000000000000000000000000001"

        /** Room's exported schemas, one file a version (the build passes the folder in). */
        val schemaDirectory: File = File(requireNotNull(System.getProperty("ludo.roomSchemas")), AppDatabase::class.qualifiedName!!)
    }
}
