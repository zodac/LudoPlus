package net.zodac.ludoplus.store

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.SoundPool
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Duration
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.zodac.ludoplus.app.AppContainer
import net.zodac.ludoplus.data.settings.DefaultStyleIds
import net.zodac.ludoplus.game.CpuStrategy
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LogEntry
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.MoveLog
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCategory
import net.zodac.ludoplus.model.AchievementCounter
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.ui.game.StartedGame
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSoundPool

/**
 * Shared by the store-asset generators ([StoreScreensGeneratorTest], [StoreArtGeneratorTest]): where they write, which parts a run asked for, the
 * app state they stage, and how a screen is captured. See `.claude/STORE_ASSETS.md` - the script
 * `scripts/generate-store-assets.sh` runs these and finishes what they write.
 */
internal object StoreAssets {

    /** The parts a generator test can be asked for, by `-PstoreAssetParts` (all of them when it's empty). */
    const val PHONE = "phone"
    const val TABLET = "tablet"
    const val ICON = "icon"
    const val FEATURE = "feature"
    const val VIDEO = "video"

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

    /** [TABLE], and two more, on a bigger board: with six, the phone still has room for the move log. */
    val BIG_TABLE: List<PlayerConfig> = TABLE + listOf(
        PlayerConfig(slot = 5, type = PlayerType.HUMAN, name = "Kim"),
        PlayerConfig(slot = 6, type = PlayerType.HUMAN, name = "Max"),
    )
}

/** The styles a scene is dressed in: the app's own by default. Each must be unlocked, or the app draws the default. */
internal data class StylePicks(
    val board: String = DefaultStyleIds.BOARD,
    val pawns: String = DefaultStyleIds.PAWNS,
    val dice: String = "classic_ivory",
    val background: String = "classic_navy",
)

/** The app state behind every store screen: a believable history, and the staged games to resume. */
internal object StoreState {

    private const val DAY_MILLIS = 86_400_000L
    private const val HISTORY_GAMES = 24

    /** 10 hours 27 minutes of play, for Statistics' time played. */
    private const val STAGED_TIME_PLAYED_SECONDS = 10 * 3_600 + 27 * 60 + 12

    /** Resets everything (the achievements too), then seeds the history and Sam's name for player 1. Waits until it's all on disk. */
    fun seed(container: AppContainer) = runBlocking {
        container.gameRecordRepository.reset()
        // The time played with them, which would add up from part to part.
        container.achievementsRepository.resetAll()
        container.inProgressGameRepository.clear()
        withTimeout(10_000) { container.inProgressGameRepository.hasInProgressGame.first { !it } }
        val now = System.currentTimeMillis()
        val random = Random(2026)
        // Two months of games, a few days apart, each played to its end by the CPU's own play for everyone.
        repeat(HISTORY_GAMES) { index ->
            val state = playToEnd(GameState.new(GameMode.CLASSIC, 4, first = index % 4), random)
            val playMillis = 12 * 60_000L + random.nextLong(14 * 60_000L)
            val finish = state.finishingOrder.withIndex().associate { (place, player) -> player to playMillis * (place + 3) / 6 }
            val game = StartedGame(GameMode.CLASSIC, Cast.TABLE, state, first = index % 4, playMillis = playMillis, finishMillis = finish)
            container.gameRecordRepository.recordGame(game, endedAtEpochMillis = now - (HISTORY_GAMES - index) * 2 * DAY_MILLIS)
        }
        container.settingsRepository.batch { setPlayerName(1, Cast.YOU) }
        // Statistics' time played: about the history's games, and a little more besides (a fresh app has none).
        container.achievementsRepository.addPlaySeconds(STAGED_TIME_PLAYED_SECONDS)
    }

    /** A staged game's time on screen so far, so its results say how long it took. */
    const val STAGED_PLAY_MILLIS = 23 * 60_000L + 41_000L

    /** Records every achievement but [locked] as earned, quietly: no banner, as a long-played app has them. */
    fun unlockAllBut(container: AppContainer, locked: Set<Achievement>) = runBlocking {
        val now = System.currentTimeMillis()
        val earned = Achievement.entries.filter { it !in locked }
        // One every few hours or so, at no regular time of day.
        val unlockedAt = earned.mapIndexed { index, achievement -> achievement to now - DAY_MILLIS - (earned.size - index) * EARNED_EVERY_MILLIS }.toMap()
        container.achievementsRepository.record(unlockedAt, STAGED_COUNTERS)
    }

    private const val EARNED_EVERY_MILLIS = 11 * 3_600_000L + 37 * 60_000L

    /** The totals behind the long climbs: past those earned, and some way short of those left ([Scenes.LOCKED]). */
    private val STAGED_COUNTERS = mapOf(
        AchievementCounter.GAMES_PLAYED to 312,
        AchievementCounter.CAPTURES to 748,
        AchievementCounter.PAWNS_HOME to 861,
        AchievementCounter.SQUARES_MOVED to 64_230,
    )

    /** Saves [game] as the game to continue. */
    fun saveGame(container: AppContainer, game: StartedGame) {
        container.inProgressGameRepository.save(game)
        runBlocking { withTimeout(10_000) { container.inProgressGameRepository.hasInProgressGame.first { it } } }
    }

    /** Picks [styles] for every table from now on. */
    fun applyStyles(container: AppContainer, styles: StylePicks) = runBlocking {
        val settings = container.settingsRepository
        settings.setBoardStyleId(styles.board)
        settings.setPawnStyleId(styles.pawns)
        settings.setDiceStyleId(styles.dice)
        settings.setTableBackgroundId(styles.background)
        withTimeout(10_000) { settings.tableBackgroundId.first { it == styles.background } }
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

    /** The achievement the last screenshot and the video's last scene unlock, banner and all. */
    val UNLOCK = Achievement.LAP_OF_HONOUR

    /**
     * What the listing leaves locked: its own unlock, the longest climbs, so the Achievements page isn't all done, and the
     * Easter eggs, which would count past the total ("110 of 100") and look like a slip.
     */
    val LOCKED: Set<Achievement> = setOf(
        UNLOCK, Achievement.COMPLETIONIST, Achievement.PUBLIC_ENEMY, Achievement.LIFETIME_ACHIEVEMENT, Achievement.ULTRAMARATHON,
        Achievement.GLOBETROTTER,
    ) + Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS }

    /** The Portals game's table: ducks on the water. */
    val POND = StylePicks(board = "ocean_lagoon", pawns = "animal_duck", dice = "ocean_lagoon", background = "ocean_waves")

    /** The Haunted House game's table: dark, so its ghosts stand out. */
    val CRYPT = StylePicks(board = "stone_slate", pawns = DefaultStyleIds.PAWNS, dice = "obsidian_lava", background = "spotlight_purple")

    /** A new Classic game, Sam to roll. */
    fun newGame(): StartedGame = started(Cast.TABLE, listOf(GameState.new(GameMode.CLASSIC, 4, first = 0)))

    /**
     * Mid-game, Sam to move with a choice: two or more moves, one of them a capture, and every player with pawns out on
     * the track, so the board looks lived in.
     */
    fun midGame(): StartedGame = search(GameMode.CLASSIC, Cast.TABLE, maxActions = 600) { state ->
        val phase = state.phase as? TurnPhase.AwaitingMove ?: return@search false
        state.current == 0 && phase.moves.size >= 2 && phase.moves.any { it.captures.isNotEmpty() } && state.livedIn(pawns = 2)
    }

    /** A Portals game, Sam to move with a choice, one of the moves through a portal, forwards. */
    fun portals(): StartedGame = search(GameMode.PORTALS, Cast.TABLE, maxActions = 600) { state ->
        val phase = state.phase as? TurnPhase.AwaitingMove ?: return@search false
        state.current == 0 && phase.moves.size >= 2 && phase.moves.any { move -> move.via?.let { move.to > it } == true } && state.livedIn(pawns = 2)
    }

    /**
     * A six-player Haunted House game, with its three ghosts, Sam to move with a choice. Their turn ends with the move,
     * and whichever they pick, a ghost catches a pawn as Ada's ends (a ghost moves after every second turn, so never
     * after Sam's).
     */
    fun hauntedHouse(): StartedGame = search(GameMode.HAUNTED_HOUSE, Cast.BIG_TABLE, maxActions = 900) { state ->
        val phase = state.phase as? TurnPhase.AwaitingMove ?: return@search false
        state.current == 0 && phase.moves.size >= 2 && state.livedIn(pawns = 1) && phase.moves.all { move ->
            val moved = LudoEngine.move(state, move.pawn, move.owner)
            moved.current == 1 && (1..ADA_TRIES).all { ghostCatchesAfterTurn(moved, Random(it)) }
        }
    }

    /** Whether a ghost catches a pawn as the turn now starting in [start] ends, played with [random]'s rolls. */
    private fun ghostCatchesAfterTurn(start: GameState, random: Random): Boolean {
        var state = start
        while (state.current == start.current && !state.isFinished) state = StoreState.step(state, random)
        return state.ghostMove?.caught.orEmpty().isNotEmpty()
    }

    /** How many ways Ada's turn is played out, to be sure of the ghost's catch after it. */
    private const val ADA_TRIES = 20

    /**
     * Sam about to win: three pawns home, and the move that brings the last one home waiting to be made. Stopping at the
     * first winner (Settings' setting, saved with the game), so that move ends the game and brings up its results.
     */
    fun lastMove(): StartedGame = search(GameMode.CLASSIC, Cast.TABLE, maxActions = 3_000) { state ->
        val phase = state.phase as? TurnPhase.AwaitingMove ?: return@search false
        val sam = state.players[0]
        state.current == 0 && state.finishingOrder.isEmpty() && sam.pawnsHome(state.board) == 3 &&
            phase.moves.any { it.to == state.board.homeProgress }
    }.let { it.copy(state = it.state.copy(rules = it.state.rules.copy(stopAtFirstWinner = true))) }

    /** Whether every player has [pawns] or more out on the track. */
    private fun GameState.livedIn(pawns: Int): Boolean = players.all { player -> player.pawns.count { it in 0..<board.trackSteps } >= pawns }

    /** The game up to the first state, over seeds from 1, that [wanted] says yes to within [maxActions] of a new game. */
    private fun search(mode: GameMode, cast: List<PlayerConfig>, maxActions: Int, wanted: (GameState) -> Boolean): StartedGame {
        for (seed in 1..20_000) {
            val random = Random(seed)
            val states = mutableListOf(GameState.new(mode, cast.size, rules = mode.rules.forPlayers(cast.size), seed = seed.toLong()))
            while (states.size <= maxActions && !states.last().isFinished) {
                if (wanted(states.last())) return started(cast, states)
                states += StoreState.step(states.last(), random)
            }
        }
        error("No seed stages this scene")
    }

    /** [states], a game from its first on, as one [cast] has been playing until now, its move log with it. */
    private fun started(cast: List<PlayerConfig>, states: List<GameState>): StartedGame {
        val now = System.currentTimeMillis()
        val log = states.indices.drop(1).fold(emptyList<LogEntry>()) { log, index ->
            MoveLog.next(log, states[index - 1], states[index], now - (states.size - index) * STAGED_ACTION_MILLIS)
        }
        return StartedGame(
            states.first().mode, cast, states.last(), first = 0, startedAtEpochMillis = now - states.size * STAGED_ACTION_MILLIS,
            playMillis = StoreState.STAGED_PLAY_MILLIS, log = log,
        )
    }

    /** How long each roll and move of a staged game took, as its log has it. */
    private const val STAGED_ACTION_MILLIS = 4_000L
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

/**
 * Deals [deal]t values to the next draws of a die, in order, then draws at random again. A roll takes one
 * `nextInt(from, until)` per die, so a value a die deals a roll. Installed as [AppContainer.diceRandom] so the video's
 * rolls land on what it means to show; a CPU's choices that draw from it first take the dealt values, so deal only
 * what the next roll uses.
 */
internal class DealtRandom(private val fallback: Random = Random(5)) : Random() {
    private val dealt = ArrayDeque<Int>()

    fun deal(vararg values: Int) {
        dealt.addAll(values.toList())
    }

    override fun nextBits(bitCount: Int): Int = fallback.nextBits(bitCount)

    override fun nextInt(from: Int, until: Int): Int {
        val next = dealt.removeFirstOrNull() ?: return fallback.nextInt(from, until)
        require(next in from until until) { "Dealt $next, but this draw is from $from until $until" }
        return next
    }
}

/**
 * Records the screen one frame at a time on the test's own clocks (Compose's and the main looper's, where the game's
 * own waits run), so every frame is exactly one frame on from the last however long it took to draw: the video plays
 * smoothly at 60fps whatever the machine. A tap draws a fading touch ring, like Android's "Show taps", so a viewer can
 * see what was pressed. Each sound the app plays is logged with its frame ([SoundLog]): the test needs
 * [RecordingSoundPool] as a shadow for that.
 */
internal class FrameRecorder(private val compose: ComposeTestRule, private val dir: File) {
    private var frame = 0
    private val taps = mutableListOf<Pair<Offset, Int>>()

    init {
        dir.deleteRecursively()
        dir.mkdirs()
        SoundLog.file = File(dir, "sounds.txt").apply { writeText("") }
        SoundLog.frame = 0
        compose.mainClock.autoAdvance = false
    }

    /** Records [millis] of whatever the screen is doing. */
    fun hold(millis: Long) {
        repeat(((millis + FRAME_MILLIS - 1) / FRAME_MILLIS).toInt()) { step() }
    }

    /** Records until [done], or [timeoutMillis]. */
    fun holdUntil(timeoutMillis: Long, done: () -> Boolean) {
        var waited = 0L
        while (!done()) {
            check(waited < timeoutMillis) { "Still waiting after ${timeoutMillis}ms" }
            step()
            waited += FRAME_MILLIS
        }
    }

    /** Hands the clock back to the test, to move on to the next scene. */
    fun finish() {
        SoundLog.file = null
        compose.mainClock.autoAdvance = true
    }

    /** Taps [node] (its click action, at the centre of its bounds) and shows the touch. */
    fun tap(node: SemanticsNodeInteraction) {
        taps += node.fetchSemanticsNode().boundsInRoot.center to frame
        node.performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun step() {
        SoundLog.frame = frame
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MILLIS))
        val screen = compose.captureScreen()
        drawTaps(screen)
        StoreAssets.save(screen, File(dir, "%05d.png".format(frame)))
        frame++
    }

    private fun drawTaps(screen: Bitmap) {
        val canvas = Canvas(screen)
        val density = screen.width / 412f
        for ((at, start) in taps) {
            val age = frame - start
            if (age !in 0 until TAP_FRAMES) continue
            val fade = 1f - age / TAP_FRAMES.toFloat()
            val radius = (22f + age * 1.2f) * density
            canvas.drawCircle(at.x, at.y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((90 * fade).toInt(), 255, 255, 255) })
            canvas.drawCircle(
                at.x, at.y, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * density
                    color = android.graphics.Color.argb((200 * fade).toInt(), 255, 255, 255)
                },
            )
        }
    }

    private companion object {
        /** The Compose test clock's frame. The video is encoded at 60fps, so it plays 4% slower than the app: unnoticeable. */
        const val FRAME_MILLIS = 16L
        const val TAP_FRAMES = 18
    }
}

/**
 * Where [RecordingSoundPool] writes each sound the app plays while a [FrameRecorder] is running: one line per sound,
 * `<frame> <clip> <volume>`, the clip being its res/raw name. The script mixes those clips in at those frames, so the
 * video's sound is the app's own, in step with what's on screen.
 */
internal object SoundLog {
    var file: File? = null
    var frame: Int = 0

    fun played(clip: String, volume: Float) {
        file?.appendText("$frame $clip $volume\n")
    }
}

/**
 * Robolectric's SoundPool, but every clip loads at once and every play is written to [SoundLog]. Its own shadow never
 * reports a clip loaded, so the app (which waits for that before playing: see AndroidSoundPlayer) would play nothing.
 * A clip is reported loaded as it's loaded, to a listener already registered (AndroidSoundPlayer registers first), and
 * to one registered later for those loaded before it.
 */
@Implements(SoundPool::class)
class RecordingSoundPool : ShadowSoundPool() {
    private val clipById = mutableMapOf<Int, Int>()

    @Implementation
    override fun load(context: Context, resId: Int, priority: Int): Int =
        super.load(context, resId, priority).also {
            clipById[it] = resId
            notifyResourceLoaded(resId, true)
        }

    @Implementation
    override fun setOnLoadCompleteListener(listener: SoundPool.OnLoadCompleteListener?) {
        super.setOnLoadCompleteListener(listener)
        clipById.values.forEach { notifyResourceLoaded(it, true) }
    }

    /** Set while a play is being passed on: one variant of `_play` calls the other, and a play is logged once. */
    private var playing = false

    @Implementation
    override fun _play(soundId: Int, leftVolume: Float, rightVolume: Float, priority: Int, loop: Int, rate: Float): Int =
        recording(soundId, leftVolume) { super._play(soundId, leftVolume, rightVolume, priority, loop, rate) }

    @Implementation
    override fun _play(soundId: Int, leftVolume: Float, rightVolume: Float, priority: Int, loop: Int, rate: Float, playerIId: Int): Int =
        recording(soundId, leftVolume) { super._play(soundId, leftVolume, rightVolume, priority, loop, rate, playerIId) }

    private fun recording(soundId: Int, volume: Float, play: () -> Int): Int {
        if (playing) return play()
        record(soundId, volume)
        playing = true
        try {
            return play()
        } finally {
            playing = false
        }
    }

    private fun record(soundId: Int, volume: Float) {
        val resId = clipById[soundId] ?: return
        SoundLog.played(ApplicationProvider.getApplicationContext<Context>().resources.getResourceEntryName(resId), volume)
    }
}
