package net.zodac.ludoplus.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PawnPosition

/** Hundreds of seeded games, each move picked at random from the legal ones, checked after every action. */
class LudoEnginePropertyTest {

    @Test
    fun `random games keep every pawn on the board - opponents apart off safe squares - and always end with every place decided`() {
        repeat(GAMES) { seed -> play(seed) }
    }

    private fun play(seed: Int) {
        val random = Random(seed)
        val playerCount = 2 + seed % 3
        var state = GameState.new(GameMode.CLASSIC, playerCount = playerCount, first = seed % playerCount)
        var actions = 0
        var rolled = 0
        while (!state.isFinished) {
            if (++actions > MAX_ACTIONS) fail("Seed $seed: no end after $MAX_ACTIONS actions")
            state = when (val phase = state.phase) {
                TurnPhase.AwaitingRoll -> LudoEngine.roll(state, random).also { rolled++ }
                is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random).pawn)
                TurnPhase.Finished -> error("unreachable")
            }
            checkInvariants(state, seed)
            assertEquals(rolled, state.players.sumOf { it.rolls.values.sum() }, "Seed $seed: rolls by face")
        }
        assertEquals((0..<playerCount).toSet(), state.finishingOrder.toSet(), "Seed $seed: every player placed once")
        assertEquals(playerCount, state.finishingOrder.size)
        // All but the last placed have every pawn home.
        for (player in state.finishingOrder.dropLast(1)) assertTrue(state.players[player].isFinished(state.board))
    }

    private fun checkInvariants(state: GameState, seed: Int) {
        val board = state.board
        for (player in state.players) {
            assertEquals(state.rules.pawnsPerPlayer, player.pawns.size, "Seed $seed: pawn count")
            assertTrue(player.pawns.all { it in PawnPosition.YARD_PROGRESS..board.homeProgress }, "Seed $seed: ${player.pawns}")
        }
        assertEquals(state.players.sumOf { it.captures }, state.players.sumOf { it.timesCaptured }, "Seed $seed: capture tallies")
        state.players.forEachIndexed { index, player ->
            assertEquals(player.captures, player.capturesOf.values.sum(), "Seed $seed: captures by opponent")
            assertEquals(player.timesCaptured, state.players.sumOf { it.capturesOf[index] ?: 0 }, "Seed $seed: times captured by opponent")
            assertTrue(index !in player.capturesOf, "Seed $seed: a player captured their own pawn")
        }
        // Off a safe square, a track square never holds two colours.
        val owners = mutableMapOf<Int, Int>()
        state.players.forEachIndexed { index, player ->
            for (progress in player.pawns.filter { it in 0..<board.trackSteps }) {
                val square = board.trackSquare(player.seat, progress)
                if (!board.isSafe(square)) {
                    val owner = owners.getOrPut(square) { index }
                    assertEquals(owner, index, "Seed $seed: square $square shared by two colours")
                }
            }
        }
        val phase = state.phase
        if (phase is TurnPhase.AwaitingMove) assertTrue(phase.moves.isNotEmpty(), "Seed $seed: a move phase with no move")
        assertTrue(state.current !in state.finishingOrder || state.isFinished, "Seed $seed: a finished player's turn")
    }

    private companion object {
        const val GAMES = 500
        const val MAX_ACTIONS = 20_000
    }
}
