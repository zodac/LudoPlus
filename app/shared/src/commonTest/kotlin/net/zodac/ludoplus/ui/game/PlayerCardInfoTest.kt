package net.zodac.ludoplus.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType

/** What each player card shows ([playerCardInfo]): the same, so the card isn't drawn again, until something on it changes. */
class PlayerCardInfoTest {

    private val players = listOf("Alex", "Lucky", "Ace", "Fluke").mapIndexed { i, name ->
        PlayerConfig(i + 1, if (i == 0) PlayerType.HUMAN else PlayerType.AI, name)
    }

    private val game = StartedGame(GameMode.CLASSIC, players, GameState.new(GameMode.CLASSIC, players.size, first = 0), first = 0)

    private fun cards(game: StartedGame): List<PlayerCardInfo> {
        val ranks = game.state.ranks()
        return game.players.indices.map { playerCardInfo(game, it, game.state.current, ranks) }
    }

    @Test
    fun `a roll that keeps the turn leaves every card as it was`() {
        val rolled = game.copy(state = LudoEngine.applyRoll(game.state, 6))
        assertIs<TurnPhase.AwaitingMove>(rolled.state.phase)
        assertEquals(cards(game), cards(rolled))
    }

    @Test
    fun `the play time going on leaves every card as it was`() {
        assertEquals(cards(game), cards(game.copy(playMillis = 60_000, steps = 3)))
    }

    @Test
    fun `a move changes the card of the player who made it`() {
        val rolled = LudoEngine.applyRoll(game.state, 6)
        val moved = game.copy(state = LudoEngine.move(rolled, (rolled.phase as TurnPhase.AwaitingMove).moves.first()))
        val before = cards(game)
        val after = cards(moved)
        assertNotEquals(before[0], after[0])
        assertEquals(1, after[0].rank)
    }

    @Test
    fun `the turn passing changes the cards of the players it passes between and no other`() {
        val passed = game.copy(state = LudoEngine.applyRoll(game.state, 3))
        assertEquals(1, passed.state.current)
        val before = cards(game)
        val after = cards(passed)
        assertEquals(listOf(true, false, false, false), before.map { it.current })
        assertEquals(listOf(false, true, false, false), after.map { it.current })
        assertEquals(before.drop(2), after.drop(2))
    }
}
