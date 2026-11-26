package net.zodac.ludoplus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.ui.rules.RulesBoardKind

/** The boards the Game Modes pages show: one for every mode beyond Classic, each a position its rules allow. */
class RulesBoardTest {

    @Test
    fun everyModeButClassicHasOneBoardOfItsOwn() {
        assertEquals(GameMode.entries - GameMode.CLASSIC, RulesBoardKind.entries.map { it.state.mode })
        RulesBoardKind.entries.forEach { assertEquals(it.state.mode.rules, it.state.rules, "${it.name}'s rules") }
    }

    @Test
    fun eachBoardStandsAsItsModeCouldHaveIt() {
        for (kind in RulesBoardKind.entries) {
            val state = kind.state
            val board = state.board
            val pawnSquares = state.players.flatMap { player ->
                assertEquals(state.rules.pawnsPerPlayer, player.pawns.size, "${kind.name}: a player's pawns")
                player.pawns.filter { it >= 0 && it < board.trackSteps }.map { board.trackSquare(player.seat, it) }
            }.toSet()
            // Nothing a mode puts on the track is on a pawn, or a safe square, where the game never puts it.
            for ((what, squares) in listOf("power-up" to state.powerUps.keys, "ghost" to state.ghosts.toSet(), "lost square" to state.lostSquares)) {
                squares.forEach { square ->
                    assertTrue(square !in pawnSquares, "${kind.name}: a $what under a pawn, on $square")
                    assertTrue(!board.isSafe(square), "${kind.name}: a $what on safe square $square")
                }
            }
            assertTrue(state.claimed.keys.none(board::isStart), "${kind.name}: a start square claimed")
            // A pawn on a claimed square is its owner's: a rival landing there would have captured it, or claimed it.
            state.players.forEachIndexed { index, player ->
                player.pawns.filter { it in 0..<board.trackSteps }.forEach { progress ->
                    val claim = state.claimed[board.trackSquare(player.seat, progress)]
                    assertTrue(claim == null || claim == index, "${kind.name}: player $index's pawn on another's claimed square")
                }
                if (!state.rules.lastStanding) assertTrue(PawnPosition.OUT_PROGRESS !in player.pawns, "${kind.name}: a pawn out of the game")
            }
        }
    }

    @Test
    fun survivalsBoardIsInTheCataclysm() {
        assertTrue(RulesBoardKind.SURVIVAL.state.cataclysm)
    }
}
