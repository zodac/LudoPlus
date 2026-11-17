package net.zodac.ludo.data.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import net.zodac.ludo.data.JsonParseException
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.LudoEngine
import net.zodac.ludo.game.RollOff
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.ui.game.StartedGame

class SavedGameJsonTest {

    private val players = listOf(
        PlayerConfig(1, PlayerType.HUMAN, "Ada \"Quote\" é", colour = PlayerColour.BLUE),
        PlayerConfig(2, PlayerType.AI, "Bot", Difficulty.HARD, PlayerColour.RED),
        PlayerConfig(3, PlayerType.AI, "Cog", Difficulty.EASY, PlayerColour.GREEN),
        PlayerConfig(4, PlayerType.HUMAN, "Dee", colour = PlayerColour.YELLOW),
    )

    private fun game(state: GameState) = StartedGame(
        mode = GameMode.CLASSIC,
        players = players,
        state = state,
        rollOff = RollOff(listOf(mapOf(0 to 6, 1 to 6, 2 to 3, 3 to 1), mapOf(0 to 2, 1 to 5)), first = 1),
        startedAtEpochMillis = 1_763_000_000_123,
        playMillis = 98_765,
        // Every finished player's, as the game keeps them.
        finishMillis = state.finishingOrder.associateWith { 1_000L * (it + 1) },
    )

    @Test
    fun `every state of random games comes back as it was saved`() {
        val random = Random(4)
        var checked = 0
        repeat(40) {
            var state = GameState.new(GameMode.CLASSIC, 4, first = 1)
            while (!state.isFinished) {
                // A finished game is never saved, and neither is the last player finishing it.
                val saved = game(state)
                assertEquals(saved, SavedGameJson.decode(SavedGameJson.encode(saved)))
                checked++
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random).pawn)
                    else -> LudoEngine.roll(state, random)
                }
            }
        }
        // Enough states to have met captures, rolls again, and players finishing.
        assert(checked > 10_000) { "Only $checked states" }
    }

    @Test
    fun `a game saved by a newer app isn't read`() {
        val json = SavedGameJson.encode(game(GameState.new(GameMode.CLASSIC, 4))).replace("\"version\":1", "\"version\":2")
        assertFailsWith<IllegalArgumentException> { SavedGameJson.decode(json) }
    }

    @Test
    fun `a save that doesn't make a playable game isn't read`() {
        val json = SavedGameJson.encode(game(GameState.new(GameMode.CLASSIC, 4)))
        val broken = listOf(
            // Not JSON, and missing a field.
            "{",
            json.replace("\"playMillis\":98765,", ""),
            // An unknown mode and colour.
            json.replace("\"mode\":\"classic\"", "\"mode\":\"nope\""),
            json.replace("\"colour\":\"RED\"", "\"colour\":\"MAUVE\""),
            // A pawn off the board, and the turn with a player who isn't there.
            json.replace("\"pawns\":[-1,-1,-1,-1]", "\"pawns\":[-1,-1,-1,57]"),
            json.replace("\"current\":0", "\"current\":4"),
            // A roll waiting for a move when no pawn can make one (all in the yard on a 3).
            json.replace("\"roll\":null", "\"roll\":3"),
            // A player placed who hasn't finished, and a finish time for a player who hasn't.
            json.replace("\"finishingOrder\":[]", "\"finishingOrder\":[2]"),
            json.replace("\"finishMillis\":{}", "\"finishMillis\":{\"2\":5}"),
        )
        broken.forEachIndexed { index, text ->
            check(text != json || index == 0) { "Replacement $index didn't match" }
            assertFailsWith<IllegalArgumentException>("Replacement $index") { SavedGameJson.decode(text) }
        }
    }

    @Test
    fun `the format is stable`() {
        val json = SavedGameJson.encode(game(LudoEngine.applyRoll(GameState.new(GameMode.CLASSIC, 4, first = 1), 4)))
        assertEquals(
            "{\"version\":1,\"mode\":\"classic\",\"startedAt\":1763000000123,\"playMillis\":98765,\"finishMillis\":{},\"players\":[" +
                "{\"slot\":1,\"type\":\"HUMAN\",\"name\":\"Ada \\\"Quote\\\" é\",\"difficulty\":\"MEDIUM\",\"colour\":\"BLUE\"}," +
                "{\"slot\":2,\"type\":\"AI\",\"name\":\"Bot\",\"difficulty\":\"HARD\",\"colour\":\"RED\"}," +
                "{\"slot\":3,\"type\":\"AI\",\"name\":\"Cog\",\"difficulty\":\"EASY\",\"colour\":\"GREEN\"}," +
                "{\"slot\":4,\"type\":\"HUMAN\",\"name\":\"Dee\",\"difficulty\":\"MEDIUM\",\"colour\":\"YELLOW\"}]," +
                "\"rollOff\":{\"rounds\":[{\"0\":6,\"1\":6,\"2\":3,\"3\":1},{\"0\":2,\"1\":5}],\"first\":1}," +
                "\"state\":{\"players\":[" +
                "{\"seat\":0,\"pawns\":[-1,-1,-1,-1],\"turns\":0,\"captures\":0,\"timesCaptured\":0}," +
                "{\"seat\":1,\"pawns\":[-1,-1,-1,-1],\"turns\":1,\"captures\":0,\"timesCaptured\":0}," +
                "{\"seat\":2,\"pawns\":[-1,-1,-1,-1],\"turns\":0,\"captures\":0,\"timesCaptured\":0}," +
                "{\"seat\":3,\"pawns\":[-1,-1,-1,-1],\"turns\":0,\"captures\":0,\"timesCaptured\":0}]," +
                "\"current\":2,\"roll\":null,\"rollAgainRun\":0,\"finishingOrder\":[]," +
                "\"lastEvent\":{\"kind\":\"noMove\",\"player\":1,\"roll\":4}}}",
            json,
        )
    }

    @Test
    fun `an unreadable player in the roll-off fails the read`() {
        val json = SavedGameJson.encode(game(GameState.new(GameMode.CLASSIC, 4))).replace("{\"0\":2,\"1\":5}", "{\"0\":2,\"9\":5}")
        assertFailsWith<JsonParseException> { SavedGameJson.decode(json) }
    }
}
