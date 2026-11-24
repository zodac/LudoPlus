package net.zodac.ludoplus.ui.game

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.ludoplus.app.AchievementUnlocker
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.achievements.InMemoryAchievementStore
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.game.TutorialScript
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCounter
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule

/** The game screen's view model earns achievements from the game it plays, each step once - and the tutorial only its own. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameAchievementsTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        store: InMemoryAchievementStore,
        saved: InProgressGameRepository = InProgressGameRepository(InMemoryPreferences(), backgroundScope),
        hour: Int = 14,
        tutorial: TutorialScript? = null,
    ) = GameViewModel(
        SettingsRepository(InMemoryPreferences()),
        saved,
        GameRecordRepository(InMemoryGameResultDao(finishedGame = true)),
        Random(7),
        elapsedMillis = { 0L },
        tutorial = tutorial,
        achievements = AchievementUnlocker(store, now = { 1L }),
        localHour = { hour },
    ).also {
        it.setPacing(GamePacing(thinkMillis = 700, pawnsAnimated = false, dieAnimated = false))
        runCurrent()
    }

    @Test
    fun `a game played out stores what player 1 did - each roll, capture, and pawn home counted once`() = runTest(dispatcher) {
        val store = InMemoryAchievementStore()
        val viewModel = viewModel(store)
        viewModel.startGame { "Player $it" }
        runCurrent()
        assertTrue(Achievement.THE_JOURNEY_BEGINS in store.unlocked)
        assertTrue(Achievement.MIDNIGHT_RUN !in store.unlocked)

        // Player 1 plays its first move each time; the CPUs play themselves.
        var rolls = 0
        val state = { assertNotNull(viewModel.game.value).state }
        while (!state().isFinished) {
            if (state().current != 0) {
                advanceTimeBy(1_000)
                runCurrent()
                continue
            }
            when (val phase = state().phase) {
                is TurnPhase.AwaitingMove -> viewModel.move(phase.moves.first().pawn)
                else -> {
                    viewModel.roll()
                    rolls++
                }
            }
            runCurrent()
        }
        runCurrent()
        val me = state().players[0]
        val counters = store.mutableState.value.counters
        assertEquals(1, counters[AchievementCounter.GAMES_PLAYED])
        assertEquals(rolls, counters[AchievementCounter.DIE_ROLLS])
        assertEquals(4, counters[AchievementCounter.PAWNS_HOME])
        assertEquals(me.captures, counters[AchievementCounter.CAPTURES] ?: 0)
        assertEquals(if (state().winner == 0) 1 else 0, counters[AchievementCounter.GAMES_WON] ?: 0)
        assertEquals(state().winner == 0, Achievement.FIRST_VICTORY in store.unlocked)
        assertTrue(Achievement.HOME_SWEET_HOME in store.unlocked)
    }

    @Test
    fun `a game started after midnight is a Midnight Run - and one continued from its save is Let's Finish This`() = runTest(dispatcher) {
        val store = InMemoryAchievementStore()
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        viewModel(store, saved, hour = 2).startGame { "Player $it" }
        runCurrent()
        assertTrue(Achievement.MIDNIGHT_RUN in store.unlocked)
        assertTrue(Achievement.LETS_FINISH_THIS !in store.unlocked)

        assertTrue(viewModel(store, saved).resumeGame())
        runCurrent()
        assertTrue(Achievement.LETS_FINISH_THIS in store.unlocked)
    }

    @Test
    fun `a Classic game with a house rule that has no achievement earns nothing - started, played out, or continued`() = runTest(dispatcher) {
        val store = InMemoryAchievementStore()
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val viewModel = viewModel(store, saved, hour = 2)
        viewModel.setupForm.setHouseRule(HouseRule.ENDLESS_SIXES, on = true)
        viewModel.startGame { "Player $it" }
        runCurrent()
        val state = { assertNotNull(viewModel.game.value).state }
        assertEquals(setOf(HouseRule.ENDLESS_SIXES), state().rules.houseRules)
        assertEquals(null, state().rules.rollAgainLimit)
        assertTrue(viewModel(store, saved).resumeGame())
        runCurrent()
        while (!state().isFinished) {
            if (state().current != 0) {
                advanceTimeBy(1_000)
                runCurrent()
                continue
            }
            when (val phase = state().phase) {
                is TurnPhase.AwaitingMove -> viewModel.move(phase.moves.first().pawn)
                else -> viewModel.roll()
            }
            runCurrent()
        }
        runCurrent()
        assertEquals(emptySet(), store.unlocked)
        assertEquals(emptyMap(), store.mutableState.value.counters)
    }

    @Test
    fun `a game of any other mode earns - from its start to its end - but only what that mode can`() = runTest(dispatcher) {
        for (mode in GameMode.entries - GameMode.CLASSIC) {
            val store = InMemoryAchievementStore()
            val viewModel = viewModel(store, hour = 2)
            viewModel.setupForm.setGameMode(mode)
            viewModel.startGame { "Player $it" }
            runCurrent()
            val state = { assertNotNull(viewModel.game.value).state }
            assertEquals(mode.rules.pawnsPerPlayer, state().rules.pawnsPerPlayer)
            assertEquals(mode.rules.dice, state().rules.dice)
            while (!state().isFinished) {
                if (state().current != 0) {
                    advanceTimeBy(1_000)
                    runCurrent()
                    continue
                }
                when (val phase = state().phase) {
                    is TurnPhase.AwaitingMove -> viewModel.move(phase.moves.first())
                    else -> viewModel.roll()
                }
                runCurrent()
            }
            runCurrent()
            assertTrue(store.unlocked.containsAll(setOf(Achievement.THE_JOURNEY_BEGINS, Achievement.WHERE_WERE_GOING_WE_DONT_NEED_RULES, Achievement.MIDNIGHT_RUN)), "$mode")
            assertEquals(emptyList(), store.unlocked.filterNot { it.isEarnableIn(mode) }, "$mode")
            val counters = store.mutableState.value.counters
            assertEquals(1, counters[AchievementCounter.GAMES_PLAYED], "$mode")
            // This mode, towards Grand Tour, with the Classic game recorded before.
            assertEquals(2, counters[AchievementCounter.MODES_FINISHED], "$mode")
        }
    }

    @Test
    fun `the tutorial earns nothing - played to its end`() = runTest(dispatcher) {
        val store = InMemoryAchievementStore()
        val viewModel = viewModel(store, tutorial = TutorialScript.CLASSIC)
        viewModel.startTutorial(defaultName = "Player 1", cpuName = "Green")
        runCurrent()
        // Through every step the script asks of the player, the CPU playing its own as they tap Next.
        repeat(200) {
            when (val phase = viewModel.game.value?.state?.phase) {
                is TurnPhase.AwaitingMove -> phase.moves.forEach { viewModel.move(it.pawn) }
                else -> viewModel.roll()
            }
            viewModel.tutorialNext()
            advanceTimeBy(2_000)
            runCurrent()
        }
        assertEquals(TurnPhase.Finished, viewModel.game.value?.state?.phase, "the tutorial played out")
        assertEquals(emptySet(), store.unlocked)
        assertEquals(emptyMap(), store.mutableState.value.counters)
    }
}
