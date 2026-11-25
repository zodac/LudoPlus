package net.zodac.ludoplus.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.Achievement.*
import net.zodac.ludoplus.model.AchievementCounter
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PawnPosition.Companion.OUT_PROGRESS
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.model.withHouseRules

private const val RED = 0
private const val GREEN = 1
private const val Y = YARD_PROGRESS
private const val X = OUT_PROGRESS
private const val HOME = 56

private val table = List(4) { PlayerConfig(it + 1, if (it == 0) PlayerType.HUMAN else PlayerType.AI, if (it == 0) "Me" else "P$it") }

/** A game of [mode] with each player's pawns at the progress given (as they start for the rest), red to roll. */
private fun game(mode: GameMode, vararg pawns: List<Int>, current: Int = RED, houseRules: Set<HouseRule> = emptySet()): GameState {
    val new = GameState.new(mode, 4, first = current, rules = mode.rules.withHouseRules(houseRules), seed = 5)
    return new.copy(players = new.players.mapIndexed { i, side -> side.copy(pawns = pawns.getOrElse(i) { side.pawns }) }, powerUps = emptyMap())
}

private fun GameState.red(change: (PlayerState) -> PlayerState) = copy(players = players.mapIndexed { i, side -> if (i == RED) change(side) else side })

/** Plays a game step by step, each step shown to one [AchievementTracker]. */
private class Steps(var state: GameState) {
    val tracker = AchievementTracker(table)
    val earned = mutableSetOf<Achievement>()
    val counts = mutableMapOf<AchievementCounter, Int>()

    fun roll(vararg dice: Int) = step(LudoEngine.applyDice(state, dice.toList()))
    fun move(pawn: Int, die: Int? = null) = step(LudoEngine.move(state, pawn, die = die))
    fun play(roll: Int, pawn: Int = 0) = roll(roll).move(pawn)

    /** On to [next], unseen. */
    fun skip(next: GameState.() -> GameState) = apply { state = state.next() }

    private fun step(next: GameState) = apply {
        val feats = tracker.observe(state, next)
        earned += feats.achievements
        for ((counter, count) in feats.counts) counts[counter] = (counts[counter] ?: 0) + count
        state = next
    }
}

/** The game modes' and house rules' own achievements, and what the tracker makes of each mode's own rules. */
class ModeAchievementsTest {

    /** Red's winning move in [mode], with a race to win, after [turns] turns. */
    private fun win(mode: GameMode, turns: Int = 60, houseRules: Set<HouseRule> = emptySet(), change: (PlayerState) -> PlayerState = { it }): Steps {
        val pawns = List(mode.rules.pawnsPerPlayer) { if (it == 0) HOME - 2 else HOME }
        return Steps(game(mode, pawns, houseRules = houseRules).red { change(it.copy(turns = turns)) }).also { steps ->
            if (mode.rules.dice == 1) steps.play(2) else steps.roll(2, 1).move(0, die = 2)
        }
    }

    @Test
    fun `starting any mode but Classic - with house rules or without - and Classic never`() {
        fun start(mode: GameMode, houseRules: Set<HouseRule> = emptySet()) =
            AchievementEngine.atGameStart(table, previousGame = null, localHour = 14, game = game(mode, houseRules = houseRules)).achievements
        for (mode in GameMode.entries - GameMode.CLASSIC) assertEquals(setOf(THE_JOURNEY_BEGINS, WHERE_WERE_GOING_WE_DONT_NEED_RULES), start(mode), "$mode")
        assertEquals(setOf(THE_JOURNEY_BEGINS), start(GameMode.CLASSIC))
        assertEquals(setOf(WHERE_WERE_GOING_WE_DONT_NEED_RULES), start(GameMode.PORTALS, setOf(HouseRule.BLOCKADES)))
        assertEquals(emptySet(), start(GameMode.CLASSIC, setOf(HouseRule.BLOCKADES)))
    }

    @Test
    fun `each mode's win is its own`() {
        val wins = Achievement.entries.filter { it.isModeWin }.associateBy { it.mode }
        for (mode in GameMode.entries.filterNot { it.rules.lastStanding }) {
            val earned = win(mode).earned
            assertTrue(FIRST_VICTORY in earned, "$mode")
            assertEquals(setOfNotNull(wins[mode]), earned.filter { it.isModeWin }.toSet(), "$mode")
        }
        // A rival's win is no one's here.
        val lost = Steps(game(GameMode.PORTALS, listOf(1, Y, Y, Y), listOf(HOME - 2, HOME, HOME, HOME), current = GREEN)).play(2)
        assertFalse(THROUGH_THE_LOOKING_GLASS in lost.earned)
    }

    @Test
    fun `Colour Coordinated holds eight squares in a row - stars counted, and not over a start - as the game goes on`() {
        // Red lands on 5, or wherever its roll from 2 takes it.
        fun landing(claimed: Set<Int>, roll: Int = 3, from: Int = 2): Set<Achievement> =
            Steps(game(GameMode.COLOUR_RUSH, listOf(from, Y, Y, Y)).copy(claimed = claimed.associateWith { RED })).play(roll).earned
        assertTrue(COLOUR_COORDINATED in landing(setOf(1, 2, 3, 4, 6, 7, 8)), "the star on 8")
        assertTrue(COLOUR_COORDINATED in landing(setOf(1, 2, 3, 4, 5, 6, 7), roll = 6), "landing on the star")
        assertTrue(COLOUR_COORDINATED in landing(setOf(5, 6, 7, 9, 10, 11, 12), roll = 6), "up to green's start")
        assertFalse(COLOUR_COORDINATED in landing(setOf(1, 2, 3, 4, 6, 7, 9)), "the star on 8 is nobody's")
        assertFalse(COLOUR_COORDINATED in landing(setOf(1, 2, 3, 4, 6, 7)))
        assertFalse(COLOUR_COORDINATED in landing(setOf(48, 49, 50, 51, 1, 2, 3), roll = 2), "over red's start, at the end of the numbering")
        assertFalse(COLOUR_COORDINATED in landing(setOf(8, 9, 10, 11, 12, 14, 15, 16), roll = 5), "over green's start")
        // Nobody else's run is player 1's, nor one with another's square in it.
        val green = game(GameMode.COLOUR_RUSH, listOf(2, Y, Y, Y)).copy(claimed = setOf(1, 2, 3, 4, 6, 7, 8, 9).associateWith { GREEN })
        assertFalse(COLOUR_COORDINATED in Steps(green).play(3).earned)
        assertFalse(COLOUR_COORDINATED in Steps(green.copy(claimed = setOf(1, 2, 3, 4, 7, 8, 9).associateWith { RED } + (6 to GREEN))).play(3).earned)
    }

    @Test
    fun `second by squares isn't a Colour Rush win - home or not`() {
        fun home(mine: Int, theirs: Int): Set<Achievement> {
            val start = game(GameMode.COLOUR_RUSH, listOf(HOME - 2, HOME, HOME, HOME))
            val squares = (0..<start.board.trackLength).filterNot(start.board::isSafe)
            return Steps(start.copy(claimed = squares.take(mine).associateWith { RED } + squares.drop(mine).take(theirs).associateWith { GREEN })).play(2).earned
        }
        assertFalse(PAINT_THE_TOWN_RED in home(4, 5))
        assertTrue(PAINT_THE_TOWN_RED in home(5, 4))
    }

    @Test
    fun `Two Birds, Two Stones captures with each die of one roll`() {
        // Green on squares 4 and 6 (43 and 45): a 3 from 1 and a 2 from 4.
        fun state() = game(GameMode.TWO_DICE, listOf(1, 4, Y, Y), listOf(43, 45, Y, Y))
        val both = Steps(state()).roll(3, 2).move(0, die = 3).move(1, die = 2)
        assertTrue(TWO_BIRDS_TWO_STONES in both.earned)
        val one = Steps(state()).roll(3, 1).move(0, die = 3).move(1, die = 1)
        assertFalse(TWO_BIRDS_TWO_STONES in one.earned)
        // A capture a roll, two rolls running, isn't it.
        val apart = Steps(state()).roll(3, 1).move(0, die = 3).move(0, die = 1).roll(1, 2).move(1, die = 2).move(0, die = 1)
        assertEquals(2, apart.counts[AchievementCounter.CAPTURES])
        assertFalse(TWO_BIRDS_TWO_STONES in apart.earned)
    }

    @Test
    fun `There And Back Again takes one pawn through a portal then back through its pair - not twice the same way, or captured between`() {
        // Red's short pair is 3 and 10.
        fun there() = Steps(game(GameMode.PORTALS, listOf(1, Y, Y, Y))).play(2)
        assertEquals(10, there().state.players[RED].pawns[0])
        fun GameState.at(progress: Int) = red { it.copy(pawns = it.pawns.mapIndexed { i, p -> if (i == 0) progress else p }) }.copy(current = RED)
        assertTrue(THERE_AND_BACK_AGAIN in there().skip { at(8) }.play(2).earned)
        assertFalse(THERE_AND_BACK_AGAIN in there().skip { at(1) }.play(2).earned)
        // Green comes out of the portal onto it (42 is square 3, where it goes in), so it starts again.
        val captured = there()
        captured.skip { copy(players = players.mapIndexed { i, side -> if (i == GREEN) side.copy(pawns = listOf(41, Y, Y, Y)) else side }, current = GREEN) }.play(1)
        assertEquals(Y, captured.state.players[RED].pawns[0])
        assertFalse(THERE_AND_BACK_AGAIN in captured.skip { at(8) }.play(2).earned)
    }

    @Test
    fun `Last Pawn Standing is Survival's win - Barely A Scratch with four pawns left`() {
        fun win(mine: List<Int>): Set<Achievement> {
            val gone = List(6) { X }
            val state = game(GameMode.SURVIVAL, mine, listOf(42, X, X, X, X, X), gone, gone).copy(knockedOut = listOf(3, 2))
            return Steps(state).play(2).earned
        }
        assertEquals(setOf(LAST_PAWN_STANDING, BARELY_A_SCRATCH), win(listOf(1, 20, 22, 24, X, X)).filter { it.mode == GameMode.SURVIVAL }.toSet())
        assertEquals(setOf(LAST_PAWN_STANDING), win(listOf(1, 20, 22, X, X, X)).filter { it.mode == GameMode.SURVIVAL }.toSet())
    }

    @Test
    fun `One Of Everything takes all six power-ups in a game`() {
        val steps = Steps(game(GameMode.CHAOS, listOf(1, Y, Y, Y)))
        for ((taken, powerUp) in PowerUp.entries.withIndex()) {
            assertFalse(ONE_OF_EVERYTHING in steps.earned, "after $taken")
            // Back to square 1, with that power-up 2 on.
            steps.skip { red { it.copy(pawns = listOf(1, Y, Y, Y), doubledRolls = 0) }.copy(current = RED, rollingAgain = false, rollAgainRun = 0, powerUps = mapOf(3 to powerUp)) }.play(2)
        }
        assertTrue(ONE_OF_EVERYTHING in steps.earned)
    }

    @Test
    fun `Not A Ghost Of A Chance is a Haunted House win with no pawn caught by a ghost`() {
        assertTrue(NOT_A_GHOST_OF_A_CHANCE in win(GameMode.HAUNTED_HOUSE).earned)
        assertFalse(NOT_A_GHOST_OF_A_CHANCE in win(GameMode.HAUNTED_HOUSE) { it.copy(timesHaunted = 1) }.earned)
        assertFalse(NOT_A_GHOST_OF_A_CHANCE in win(GameMode.CLASSIC).earned)
    }

    @Test
    fun `Everything Everywhere All At Once is a win of any mode but Classic with every house rule that changes it`() {
        val all = HouseRule.entries.toSet()
        assertEquals(setOf(EVERYTHING_EVERYWHERE_ALL_AT_ONCE), win(GameMode.PORTALS, houseRules = all).earned)
        assertEquals(emptySet(), win(GameMode.CLASSIC, houseRules = all).earned)
        assertEquals(setOf(EVERYTHING_EVERYWHERE_ALL_AT_ONCE), win(GameMode.CHAOS, houseRules = all).earned)
        assertEquals(emptySet(), win(GameMode.PORTALS, houseRules = all - HouseRule.FREE_START).earned)
        val survival = HouseRule.entries.filter { it.appliesTo(GameMode.SURVIVAL.rules) }.toSet()
        val gone = List(6) { X }
        val state = game(GameMode.SURVIVAL, listOf(1, 20, X, X, X, X), listOf(42, X, X, X, X, X), gone, gone, houseRules = survival).copy(knockedOut = listOf(3, 2))
        assertEquals(setOf(EVERYTHING_EVERYWHERE_ALL_AT_ONCE), Steps(state).play(2).earned)
    }

    @Test
    fun `One By One brings every pawn home with no other ever out - a capture starting one again - seen from the start`() {
        /** From the first roll: [others] on the board as the second pawn comes out, the last then 2 from home. */
        fun game(others: Int, mode: GameMode = GameMode.CLASSIC, seen: Boolean = true): Set<Achievement> {
            val steps = Steps(game(mode))
            if (seen) steps.roll(1)
            steps.skip { red { it.copy(pawns = listOf(HOME, others, Y, Y), turns = 20) }.copy(current = RED, rollingAgain = false) }.play(6, pawn = 2)
            return steps.skip { red { it.copy(pawns = listOf(HOME, HOME, HOME, HOME - 2), turns = 60) }.copy(current = RED, rollingAgain = false, rollAgainRun = 0) }.play(2, pawn = 3).earned
        }
        assertTrue(ONE_BY_ONE in game(others = HOME))
        assertTrue(ONE_BY_ONE in game(others = Y))
        // Even one in its home column is out.
        assertFalse(ONE_BY_ONE in game(others = 30))
        assertFalse(ONE_BY_ONE in game(others = 53))
        assertFalse(ONE_BY_ONE in game(others = HOME, seen = false))
        assertTrue(ONE_BY_ONE in game(others = HOME, mode = GameMode.CHAOS))
    }

    @Test
    fun `One By One isn't a game where Call Up brought one out`() {
        val steps = Steps(game(GameMode.CHAOS)).roll(1)
        steps.skip { red { it.copy(pawns = listOf(1, Y, Y, Y)) }.copy(current = RED, rollingAgain = false, powerUps = mapOf(3 to PowerUp.CALL_UP)) }.play(2)
        steps.skip { red { it.copy(pawns = listOf(HOME, HOME, HOME, HOME - 2)) }.copy(current = RED, rollingAgain = false, rollAgainRun = 0, powerUps = emptyMap()) }.play(2, pawn = 3)
        assertFalse(ONE_BY_ONE in steps.earned)
    }

    @Test
    fun `One By One isn't a game where a banked Call Up brought one out with another on the board`() {
        /** Green, two from red's pawn on square 4, takes it, with red's other pawns at [others]. */
        fun game(others: Int): Set<Achievement> {
            val steps = Steps(game(GameMode.CHAOS)).roll(1)
            val taking = steps.skip { red { it.copy(pawns = listOf(4, others, others, others), callUps = 1) }.copy(current = GREEN, rollingAgain = false, powerUps = emptyMap()) }
            taking.skip { copy(players = players.mapIndexed { i, side -> if (i == GREEN) side.copy(pawns = listOf(41, Y, Y, Y)) else side }) }.play(2)
            assertEquals(0, steps.state.players[RED].pawns[0])
            steps.skip { red { it.copy(pawns = listOf(HOME, HOME, HOME, HOME - 2)) }.copy(current = RED, rollingAgain = false, rollAgainRun = 0, powerUps = emptyMap()) }.play(2, pawn = 3)
            return steps.earned
        }
        assertFalse(ONE_BY_ONE in game(others = 30))
        assertTrue(ONE_BY_ONE in game(others = HOME))
    }

    @Test
    fun `The Great Escape has all four pawns out within four turns - moving some between - and not in five`() {
        /** A pawn out on each of [turns], the first moved on after it. */
        fun escape(vararg turns: Int, mode: GameMode = GameMode.CLASSIC): Set<Achievement> {
            val steps = Steps(game(mode))
            for ((pawn, turn) in turns.withIndex()) {
                steps.skip { red { it.copy(turns = turn - 1) }.copy(current = RED, rollingAgain = false, rollAgainRun = 0) }.play(6, pawn)
                if (pawn == 0) steps.play(3, pawn = 0)
            }
            return steps.earned
        }
        assertTrue(THE_GREAT_ESCAPE in escape(1, 2, 3, 4))
        assertTrue(THE_GREAT_ESCAPE in escape(7, 7, 9, 10))
        assertFalse(THE_GREAT_ESCAPE in escape(1, 2, 3, 5))
        // One captured on the way has to come out again.
        val captured = Steps(game(GameMode.CLASSIC)).play(6, 0).play(3, 0)
        captured.skip { copy(players = players.mapIndexed { i, side -> if (i == GREEN) side.copy(pawns = listOf(41, Y, Y, Y)) else side }, current = GREEN, rollingAgain = false) }.play(1)
        for (pawn in 1..3) captured.skip { red { it.copy(turns = pawn) }.copy(current = RED, rollingAgain = false, rollAgainRun = 0) }.play(6, pawn)
        assertFalse(THE_GREAT_ESCAPE in captured.earned)
        assertTrue(THE_GREAT_ESCAPE in captured.skip { copy(current = RED, rollingAgain = false, rollAgainRun = 0) }.play(6, 0).earned)
    }

    @Test
    fun `Grand Tour counts the modes finished - raised to the figure, never added to it`() {
        fun finished(modes: Int, before: Int) = AchievementEngine.evaluate(
            GameFeats(levels = mapOf(AchievementCounter.MODES_FINISHED to modes)),
            net.zodac.ludoplus.data.achievements.AchievementsState(counters = mapOf(AchievementCounter.MODES_FINISHED to before)), now = 1,
        )
        assertEquals(mapOf(AchievementCounter.MODES_FINISHED to 3), finished(3, before = 2).counters)
        assertEquals(listOf(AchievementProgress(GRAND_TOUR, 2, 3)), finished(3, before = 2).progressed)
        assertEquals(emptyMap(), finished(3, before = 3).counters)
        assertEquals(emptyMap(), finished(2, before = 3).counters)
        assertEquals(listOf(GRAND_TOUR), finished(GameMode.entries.size, before = 6).newlyUnlocked)
    }

    @Test
    fun `two dice are two rolls of the die - and either may be the 6`() {
        val steps = Steps(game(GameMode.TWO_DICE, listOf(10, Y, Y, Y))).roll(3, 6)
        assertTrue(OFF_THE_MARK in steps.earned)
        assertEquals(mapOf(AchievementCounter.DIE_ROLLS to 2, AchievementCounter.SIXES_ROLLED to 1), steps.counts)
        // Three doubles aren't three 6s.
        val doubles = Steps(game(GameMode.TWO_DICE, listOf(10, Y, Y, Y)).copy(rollAgainRun = 2, rollingAgain = true)).roll(6, 6)
        assertTrue(doubles.state.lastEvent is GameEvent.RollAgainLimit)
        assertFalse(TOO_MUCH_OF_A_GOOD_THING in doubles.earned)
    }

    @Test
    fun `squares moved are those walked - not those a portal skips`() {
        val steps = Steps(game(GameMode.PORTALS, listOf(8, Y, Y, Y))).play(2)
        assertEquals(3, steps.state.players[RED].pawns[0])
        assertEquals(2, steps.tracker.flush().counts[AchievementCounter.SQUARES_MOVED])
    }

    @Test
    fun `a player knocked out of Survival has finished a game - last, if first out - and the win streak waits for its end`() {
        // Green takes red's last pawn, on square 4 (42 + 1).
        val state = game(GameMode.SURVIVAL, listOf(4, X, X, X, X, X), listOf(42, 0, 0, 0, 0, 0), current = GREEN)
        val out = Steps(state).play(1)
        assertEquals(setOf(WOODEN_SPOON), out.earned)
        assertEquals(1, out.counts[AchievementCounter.GAMES_PLAYED])
        assertEquals(1, out.counts[AchievementCounter.LAST_PLACES])
        val third = Steps(state.copy(players = state.players.mapIndexed { i, side -> if (i == 3) side.copy(pawns = List(6) { X }) else side }, knockedOut = listOf(3))).play(1)
        assertFalse(WOODEN_SPOON in third.earned)
        assertEquals(1, third.counts[AchievementCounter.GAMES_PLAYED])
    }

    @Test
    fun `the feats a mode makes free aren't earned in it`() {
        // Survival's pawns start out together, all on the board; and a stack is captured together.
        val stack = Steps(game(GameMode.SURVIVAL, listOf(1, 3, 0, 0, 0, 0), listOf(42, 42, 42, 0, 0, 0))).play(2)
        assertTrue(GOTCHA in stack.earned)
        for (free in listOf(ALL_HANDS_ON_DECK, TWO_FOR_ONE, HAT_TRICK)) assertFalse(free in stack.earned, "$free")
        assertTrue(FULL_HOUSE in Steps(game(GameMode.CLASSIC, listOf(50, 52, 53, 54))).play(1).earned)
        assertTrue(UNTOUCHABLE in win(GameMode.CHAOS).earned)
    }
}
