package net.zodac.ludoplus.data.records

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * The game records database: every finished game's [GameResult]s, for the Leaderboard and Statistics. Room's
 * multiplatform build: each platform opens it with its own [RoomDatabase.Builder] (on Android, the framework's SQLite)
 * and finishes it with [buildAppDatabase].
 *
 * Every schema change after the first release needs a version bump and a migration in [MIGRATIONS]; the exported
 * schemas in app/shared/schemas/ are what a migration test compares against.
 */
@Database(entities = [GameResult::class], version = 6, exportSchema = true)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gameResultDao(): GameResultDao

    companion object {
        /** The database's file name, the same on every platform, each in its own app-private directory. */
        const val FILE_NAME = "ludo.db"
    }
}

/** Room's compiler generates the `actual` for each target. */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Every migration between schema versions, oldest first. */
val MIGRATIONS: Array<Migration> = arrayOf(
    // House rules (Phase 9): each game's flag, none before them.
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `results` ADD COLUMN `houseRules` INTEGER NOT NULL DEFAULT 0")
        }
    },
    // The cloud save and progress files (Phase 10): each row's uuid, a random one for every row before them.
    object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `results` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
            connection.execSQL("UPDATE `results` SET `uuid` = lower(hex(randomblob(16)))")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_results_uuid` ON `results` (`uuid`)")
        }
    },
    // Colour Rush: each game's squares, none before it. Quick, which it replaced, is gone, and its games with it.
    object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `results` ADD COLUMN `squares` INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("DELETE FROM `results` WHERE `gameModeId` = 'quick'")
        }
    },
    // Survival's Leaderboard by the squares travelled: each game's, unknown before it.
    object : Migration(4, 5) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `results` ADD COLUMN `squaresMoved` INTEGER")
        }
    },
    // Each player count's Leaderboard read from the index: the player count in it, and captures most first.
    object : Migration(5, 6) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP INDEX IF EXISTS `index_results_leaderboard`")
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_results_leaderboard` ON `results` " +
                    "(`gameModeId` ASC, `playedUnmodified` ASC, `won` ASC, `playerCount` ASC, `turns` ASC, `captures` DESC, `endedAtEpochMillis` ASC)",
            )
        }
    },
)

/**
 * Finishes a platform's builder the same way everywhere. No destructive fallback of any kind: a missing migration must
 * fail loudly in testing, never quietly wipe a player's records.
 */
fun RoomDatabase.Builder<AppDatabase>.buildAppDatabase(): AppDatabase = this
    .setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(*MIGRATIONS)
    .build()
