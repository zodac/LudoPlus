package net.zodac.ludo.store

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.test.junit4.ComposeTestRule
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.game.CpuStrategy
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.LudoEngine
import net.zodac.ludo.game.RollOff
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.ui.game.StartedGame
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/**
 * Shared by the store-asset generators ([StoreScreensGeneratorTest]): where they write, which parts a run asked for, the
 * app state they stage, and how a screen is captured. See `.claude/STORE_ASSETS.md` - the script
 * `scripts/generate-store-assets.sh` runs these and finishes what they write.
 */
internal object StoreAssets {

    /** The parts a generator test can be asked for, by `-PstoreAssetParts` (all of them when it's empty). */
    const val PHONE = "phone"
    const val TABLET = "tablet"

    /** The raw renders, before the script captions and flattens them. Never inside the repo's tracked files. */
    val outDir: File
        get() = System.getProperty("ludo.storeAssetsDir")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(repoRoot, "build/store-assets/raw")

    private val repoRoot: File
        get() {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
            return dir ?: File(".").absoluteFile
        }

    private fun wants(part: String): Boolean {
        val parts = System.getProperty("ludo.storeAssetParts").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return parts.isEmpty() || part in parts
    }

    /**
     * A generator, not a check: a test marked [StorePart] only runs with `-PgenerateStoreAssets`, and only when its part
     * was asked for. First in a class's rule order, so a skipped run doesn't launch the app.
     */
    fun onlyWhenAsked(): TestRule = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue("Run scripts/generate-store-assets.sh to make the store assets", System.getProperty("ludo.generateStoreAssets") == "true")
                val part = checkNotNull(description.getAnnotation(StorePart::class.java)) { "${description.methodName} has no @StorePart" }.value
                assumeTrue("Not asked for: $part", wants(part))
                base.evaluate()
            }
        }
    }

    fun save(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** Which part of the store assets a generator test makes - see [StoreAssets.onlyWhenAsked]. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class StorePart(val value: String)

/** The people at the table in every staged game and in the recorded history. Short, so no name is ever cut. */
internal object Cast {
    const val YOU = "Sam"
    val FRIENDS = listOf("Jo", "Alex")
    const val CPU = "Ada"

    /** Sam, the CPU Ada, then Jo and Alex: seats 1 to 4, in the board's colours. */
    val TABLE: List<PlayerConfig> = listOf(
        PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = YOU),
        PlayerConfig(slot = 2, type = PlayerType.AI, name = CPU, difficulty = Difficulty.MEDIUM),
        PlayerConfig(slot = 3, type = PlayerType.HUMAN, name = FRIENDS[0]),
        PlayerConfig(slot = 4, type = PlayerType.HUMAN, name = FRIENDS[1]),
    )
}

/** The app state behind every store screen: a believable history, and the staged games to resume. */
internal object StoreState {

    private const val DAY_MILLIS = 86_400_000L
    private const val HISTORY_GAMES = 24

    /** 10 hours 27 minutes of play, for Statistics' time played. */
    private const val STAGED_TIME_PLAYED_SECONDS = 10 * 3_600 + 27 * 60 + 12

    /** Resets everything, then seeds the history and Sam's name for player 1. Waits until it's all on disk. */
    fun seed(container: AppContainer) = runBlocking {
        container.gameRecordRepository.reset()
        container.inProgressGameRepository.clear()
        withTimeout(10_000) { container.inProgressGameRepository.hasInProgressGame.first { !it } }
        val now = System.currentTimeMillis()
        val random = Random(2026)
        // Two months of games, a few days apart, each played to its end by the CPU's own play for everyone.
        repeat(HISTORY_GAMES) { index ->
            val state = playToEnd(GameState.new(GameMode.CLASSIC, 4, first = index % 4), random)
            val playMillis = 12 * 60_000L + random.nextLong(14 * 60_000L)
            val finish = state.finishingOrder.withIndex().associate { (place, player) -> player to playMillis * (place + 3) / 6 }
            val game = StartedGame(GameMode.CLASSIC, Cast.TABLE, state, RollOff(emptyList(), index % 4), playMillis = playMillis, finishMillis = finish)
            container.gameRecordRepository.recordGame(game, endedAtEpochMillis = now - (HISTORY_GAMES - index) * 2 * DAY_MILLIS)
        }
        container.settingsRepository.batch { setPlayerName(1, Cast.YOU) }
        // Statistics' time played: about the history's games, and a little more besides (a fresh app has none).
        container.settingsRepository.addPlaySeconds(STAGED_TIME_PLAYED_SECONDS)
    }

    /** A staged game's time on screen so far, so its results say how long it took. */
    private const val STAGED_PLAY_MILLIS = 23 * 60_000L + 41_000L

    /** Saves [state] as the game to continue, at [Cast.TABLE]. */
    fun saveGame(container: AppContainer, state: GameState) {
        container.inProgressGameRepository.save(StartedGame(GameMode.CLASSIC, Cast.TABLE, state, RollOff(emptyList(), 0), playMillis = STAGED_PLAY_MILLIS))
        runBlocking { withTimeout(10_000) { container.inProgressGameRepository.hasInProgressGame.first { it } } }
    }

    /** The next action of [state], played by the Medium CPU for whoever's turn it is. */
    fun step(state: GameState, random: Random): GameState = when (val phase = state.phase) {
        TurnPhase.AwaitingRoll -> LudoEngine.roll(state, random)
        is TurnPhase.AwaitingMove -> CpuStrategy.of(Difficulty.MEDIUM).choose(state, phase.moves, random).let { LudoEngine.move(state, it.pawn, it.owner) }
        TurnPhase.Finished -> state
    }

    private fun playToEnd(start: GameState, random: Random): GameState {
        var state = start
        while (!state.isFinished) state = step(state, random)
        return state
    }
}

/** The staged games: each a real game, played by the CPU for everyone up to the moment shown, found by a seed search. */
internal object Scenes {

    /**
     * Mid-game, Sam to move with a choice: two or more moves, one of them a capture, and every player with pawns out on
     * the track, so the board looks lived in.
     */
    fun midGame(): GameState = search(maxActions = 600) { state ->
        val phase = state.phase as? TurnPhase.AwaitingMove ?: return@search false
        state.current == 0 && phase.moves.size >= 2 && phase.moves.any { it.captures.isNotEmpty() } &&
            state.players.all { player -> player.pawns.count { it in 0..<state.board.trackSteps } >= 2 }
    }

    /**
     * Sam about to win: three pawns home, and the move that brings the last one home waiting to be made. Stopping at the
     * first winner (Settings' setting, saved with the game), so that move ends the game and brings up its results.
     */
    fun lastMove(): GameState = search(maxActions = 3_000) { state ->
        val phase = state.phase as? TurnPhase.AwaitingMove ?: return@search false
        val sam = state.players[0]
        state.current == 0 && state.finishingOrder.isEmpty() && sam.pawnsHome(state.board) == 3 &&
            phase.moves.any { it.to == state.board.homeProgress }
    }.let { it.copy(rules = it.rules.copy(stopAtFirstWinner = true)) }

    /** The first state, over seeds from 1, that [wanted] says yes to within [maxActions] of a new game. */
    private fun search(maxActions: Int, wanted: (GameState) -> Boolean): GameState {
        for (seed in 1..2_000) {
            val random = Random(seed)
            var state = GameState.new(GameMode.CLASSIC, 4)
            repeat(maxActions) {
                if (wanted(state)) return state
                if (state.isFinished) return@repeat
                state = StoreState.step(state, random)
            }
        }
        error("No seed stages this scene")
    }
}

/**
 * What the screen shows right now, as the phone would: the app's window, with every overlay window (a dialog) drawn
 * over it where its gravity puts it. Robolectric neither places a window by its gravity nor captures an overlay window's
 * own pixels, so each window's view tree is drawn here directly, bottom-most first.
 */
internal fun ComposeTestRule.captureScreen(): Bitmap {
    waitForIdle()
    val windows = windowViews()
    val main = windows.first()
    val screen = Bitmap.createBitmap(main.width, main.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(screen)
    main.draw(canvas)
    for (overlay in windows.drop(1)) {
        if (overlay.width == 0 || overlay.height == 0 || overlay.visibility != View.VISIBLE) continue
        val gravity = (overlay.layoutParams as? WindowManager.LayoutParams)?.gravity ?: Gravity.CENTER
        val left = (screen.width - overlay.width) / 2f
        val top = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.BOTTOM -> (screen.height - overlay.height).toFloat()
            Gravity.TOP -> 0f
            else -> (screen.height - overlay.height) / 2f
        }
        canvas.save()
        canvas.translate(left, top)
        overlay.draw(canvas)
        canvas.restore()
    }
    return screen
}

/** Every window's root view, the activity's first - read from the framework's own list of them. */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
private fun windowViews(): List<View> {
    val global = Class.forName("android.view.WindowManagerGlobal")
    val instance = global.getMethod("getInstance").invoke(null)
    val field = global.getDeclaredField("mViews").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    val views = (field.get(instance) as List<View>).filter { it.isAttachedToWindow }
    val main = views.maxBy { it.width.toLong() * it.height }
    return listOf(main) + views.filter { it !== main }
}
