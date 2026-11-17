package net.zodac.ludo.data.records

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
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
@Database(entities = [GameResult::class], version = 1, exportSchema = true)
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

/** Every migration between released schema versions, oldest first. None yet: version 1 is the first. */
val MIGRATIONS: Array<Migration> = emptyArray()

/**
 * Finishes a platform's builder the same way everywhere. No destructive fallback of any kind: a missing migration must
 * fail loudly in testing, never quietly wipe a player's records.
 */
fun RoomDatabase.Builder<AppDatabase>.buildAppDatabase(): AppDatabase = this
    .setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(*MIGRATIONS)
    .build()
