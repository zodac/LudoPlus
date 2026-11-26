package net.zodac.ludoplus.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.GameRules
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS
import net.zodac.ludoplus.model.withHouseRules

/** Red, green, yellow, and blue: players 0 to 3 at seats 0 to 3, so their start squares are 0, 13, 26, and 39. */
private const val RED = 0
private const val GREEN = 1
private const val YELLOW = 2
private const val BLUE = 3

private const val Y = YARD_PROGRESS

/** A classic game of four, every pawn in its yard, red to roll, under [houseRules]. */
private fun game(houseRules: Set<HouseRule> = emptySet(), mode: GameMode = GameMode.CLASSIC): GameState =
    GameState.new(mode, playerCount = 4, rules = mode.rules.withHouseRules(houseRules))

private val GameState.moves: List<Move> get() = (phase as TurnPhase.AwaitingMove).moves

/** The current player rolls [roll], and makes the first of its moves, if it has any: not the next player's pawn out free. */
private fun GameState.play(roll: Int): GameState {
    val rolled = LudoEngine.applyRoll(this, roll)
    return if ((rolled.phase as? TurnPhase.AwaitingMove)?.free == false) LudoEngine.move(rolled, rolled.moves.first()) else rolled
}

/** Red brings the game's first pawn out with a 6, then moves it 1: green to play. */
private fun GameState.redOut(): GameState = play(6).play(1)

/** A round of 1s, green, yellow, then blue, each with no move (red's pawn moving 1), back round to green. */
private fun GameState.round(): GameState = play(1).play(1).play(1).play(1)

private val GameState.awaitsFree: Boolean get() = (phase as? TurnPhase.AwaitingMove)?.free == true

/**
 * The free pawn: 5 turns of their own after the game's first pawn comes out, a player who has never had one out gets one
 * out free as their 6th starts, before they roll ([GameRules.pityRelease]).
 */
class PityReleaseEngineTest {

    @Test
    fun `on in every mode by default - after 5 turns - and off with No Free Pawn`() {
        assertEquals(5, GameRules.PITY_RELEASE_ROLLS)
        for (mode in GameMode.entries) assertEquals(5, mode.rules.pityRelease)
        assertNull(GameRules().withHouseRules(setOf(HouseRule.NO_PITY_RELEASE)).pityRelease)
    }

    @Test
    fun `the first pawn out starts every player's count - from the turns they had then`() {
        val before = game().play(1).play(1)
        assertNull(before.pityFrom)
        // Red has started 2 turns, green 1, and the others none.
        val out = before.play(1).play(1).redOut()
        assertEquals(listOf(2, 1, 1, 1), out.pityFrom)
        // A later pawn out changes nothing.
        assertEquals(listOf(2, 1, 1, 1), out.play(6).play(1).pityFrom)
    }

    @Test
    fun `a player with no pawn out after 5 turns gets one out free as their 6th starts - before they roll - and once`() {
        var state = game().redOut()
        repeat(4) {
            state = state.round()
            assertEquals(GREEN, state.current)
            assertFalse(state.awaitsFree)
        }
        state = state.round()
        assertEquals(GREEN, state.current)
        assertEquals(5, state.players[GREEN].turns)
        val free = Move(0, Y, 0, free = true)
        assertEquals(listOf(free, free.copy(pawn = 1), free.copy(pawn = 2), free.copy(pawn = 3)), state.moves)
        assertEquals(free, LudoEngine.automaticMove(state))

        val out = LudoEngine.move(state, free)
        assertEquals(listOf(0, Y, Y, Y), out.players[GREEN].pawns)
        assertEquals(GameEvent.Moved(GREEN, 0, free, finished = false), out.lastEvent)
        // Then their turn: their roll starts it.
        assertEquals(GREEN, out.current)
        assertEquals(TurnPhase.AwaitingRoll, out.phase)
        assertFalse(out.rollingAgain)
        val rolled = out.play(2)
        assertEquals(6, rolled.players[GREEN].turns)
        assertEquals(listOf(2, Y, Y, Y), rolled.players[GREEN].pawns)

        // Yellow and blue, no pawn out either, get theirs as their turns start; red, who brought the first out, none.
        assertTrue(rolled.awaitsFree)
        assertEquals(YELLOW, rolled.current)
        val yellow = LudoEngine.move(rolled, rolled.moves.first()).play(1)
        assertEquals(BLUE, yellow.current)
        val blue = LudoEngine.move(yellow, yellow.moves.first()).play(1)
        assertEquals(RED, blue.current)
        assertFalse(blue.awaitsFree)
        // And no second one: green's pawn captured or not, they've had one out.
        val green = blue.play(1)
        assertEquals(GREEN, green.current)
        assertFalse(green.awaitsFree)
    }

    @Test
    fun `none for a player who has had a pawn out - even one captured since - nor for the one who brought the first out`() {
        var state = game().redOut()
        state = state.round()
        // Yellow rolls a 6 in its second turn.
        state = state.play(1).play(6).play(1).play(1).play(1)
        // Blue's pawn, out and then captured: back in its yard, but it has been out.
        state = state.copy(players = state.players.mapIndexed { i, it -> if (i == BLUE) it.copy(timesCaptured = 1) else it })
        // Red's own pawn, captured too.
        state = state.copy(players = state.players.mapIndexed { i, it -> if (i == RED) it.copy(pawns = listOf(Y, Y, Y, Y), timesCaptured = 1) else it })
        assertTrue(state.players[BLUE].haveLeftYard)
        assertTrue(state.players[RED].haveLeftYard)
        assertFalse(state.players[GREEN].haveLeftYard)
        repeat(3) { state = state.round() }
        // Green's 6th turn: theirs comes out.
        assertEquals(GREEN, state.current)
        assertTrue(state.awaitsFree)
        state = LudoEngine.move(state, state.moves.first()).play(1)
        // Yellow, blue, and red roll as ever.
        for (player in listOf(YELLOW, BLUE, RED)) {
            assertEquals(player, state.current)
            assertFalse(state.awaitsFree)
            state = state.play(1)
        }
    }

    @Test
    fun `out free it captures as a pawn brought out does`() {
        // Guarded Starts: red's pawn 13 on stands on green's start square, which isn't safe for it.
        var state = game(setOf(HouseRule.GUARDED_STARTS)).redOut()
        repeat(4) { state = state.round() }
        state = state.copy(players = state.players.mapIndexed { i, it -> if (i == RED) it.copy(pawns = listOf(12, Y, Y, Y)) else it })
        state = state.round()
        val free = state.moves.first()
        assertEquals(listOf(PawnRef(RED, 0)), free.captures)
        val out = LudoEngine.move(state, free)
        assertEquals(listOf(Y, Y, Y, Y), out.players[RED].pawns)
        // It earns no roll: the player's turn is still to start.
        assertEquals(GREEN, out.current)
        assertEquals(TurnPhase.AwaitingRoll, out.phase)
    }

    @Test
    fun `a blockade on the start square keeps the pawn in - until a turn starts with it gone`() {
        var state = game(setOf(HouseRule.BLOCKADES)).redOut()
        repeat(4) { state = state.round() }
        // Red's two pawns 13 on stand on green's start square.
        state = state.copy(players = state.players.mapIndexed { i, it -> if (i == RED) it.copy(pawns = listOf(13, 13, 20, Y)) else it })
        state = state.play(1).play(1).play(1)
        state = LudoEngine.move(LudoEngine.applyRoll(state, 1), 2)
        // Green's 6th turn starts with the blockade there: they roll as ever.
        assertEquals(GREEN, state.current)
        assertEquals(TurnPhase.AwaitingRoll, state.phase)
        state = state.play(1)
        // Yellow and blue get theirs.
        for (player in listOf(YELLOW, BLUE)) {
            assertEquals(player, state.current)
            assertTrue(state.awaitsFree)
            state = LudoEngine.move(state, state.moves.first()).play(1)
        }
        // Red's pawn 1 moves off: green's next turn starts with theirs.
        state = state.play(1)
        assertEquals(listOf(14, 13, 21, Y), state.players[RED].pawns)
        assertEquals(GREEN, state.current)
        assertTrue(state.awaitsFree)
    }

    @Test
    fun `No Free Pawn - nothing comes out free`() {
        var state = game(setOf(HouseRule.NO_PITY_RELEASE)).redOut()
        repeat(8) {
            state = state.round()
            assertFalse(state.awaitsFree)
        }
    }

    @Test
    fun `two dice - the pawn out free has no dice - and the roll after it rolls both`() {
        fun GameState.playDice(vararg dice: Int): GameState {
            var state = LudoEngine.applyDice(this, dice.toList())
            while (state.phase is TurnPhase.AwaitingMove && !state.awaitsFree) state = LudoEngine.move(state, state.moves.first())
            return state
        }
        // Red's 6 brings the first pawn out, and its 1 moves it.
        var state = GameState.new(GameMode.TWO_DICE, playerCount = 4).playDice(6, 1)
        assertEquals(GREEN, state.current)
        repeat(5) { state = state.playDice(1, 2).playDice(1, 2).playDice(1, 2).playDice(1, 2) }
        assertEquals(GREEN, state.current)
        assertTrue(state.awaitsFree)
        assertEquals(emptyList(), (state.phase as TurnPhase.AwaitingMove).dice)
        val out = LudoEngine.move(state, state.moves.first())
        assertEquals(TurnPhase.AwaitingRoll, out.phase)
        assertEquals(listOf(3, 4), (LudoEngine.applyDice(out, listOf(3, 4)).phase as TurnPhase.AwaitingMove).dice)
    }
}
