package net.zodac.ludo.data.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.ludo.data.JsonParseException
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.LudoEngine
import net.zodac.ludo.game.RollOff
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.HouseRule
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.model.TeamMode
import net.zodac.ludo.model.withHouseRules
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
        id = "0123456789abcdef0123456789abcdef",
        steps = 17,
    )

    @Test
    fun `a game saved without an id or steps - by an older build - reads as none`() {
        val saved = game(GameState.new(GameMode.CLASSIC, 4, first = 1))
        val older = SavedGameJson.encode(saved).replace(",\"id\":\"0123456789abcdef0123456789abcdef\"", "").replace(",\"steps\":17", "")
        assertEquals(saved.copy(id = null, steps = 0), SavedGameJson.decode(older))
    }

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
    fun `every state of random team games comes back as it was saved - partners rolled for and part-home teams too`() {
        val random = Random(9)
        var rolledFor = 0
        for ((index, teams) in listOf(TeamMode.TWO_TEAMS, TeamMode.PAIRS).withIndex()) {
            repeat(15) { game ->
                // Each option both ways across the games.
                val rules = GameMode.CLASSIC.rules.copy(teams = teams.teamsFor(4), partnerCaptures = game % 2 == 0, rollForPartners = (game + index) % 3 != 0)
                var state = GameState.new(GameMode.CLASSIC, 4, first = 1, rules = rules)
                while (!state.isFinished) {
                    val saved = game(state)
                    assertEquals(saved, SavedGameJson.decode(SavedGameJson.encode(saved)))
                    state = when (val phase = state.phase) {
                        is TurnPhase.AwaitingMove -> phase.moves.random(random).let {
                            if (it.owner != null) rolledFor++
                            LudoEngine.move(state, it.pawn, it.owner)
                        }
                        else -> LudoEngine.roll(state, random)
                    }
                }
            }
        }
        assert(rolledFor > 0) { "No partner was rolled for" }
    }

    @Test
    fun `every state of random games with house rules comes back as it was saved - tries to leave the yard too`() {
        val random = Random(11)
        var tries = 0
        repeat(30) {
            // A random mix each game, every house rule on in some.
            val houseRules = HouseRule.entries.filter { random.nextBoolean() }.toSet()
            var state = GameState.new(GameMode.CLASSIC, 4, first = 1, rules = GameMode.CLASSIC.rules.withHouseRules(houseRules))
            while (!state.isFinished) {
                val saved = game(state)
                assertEquals(saved, SavedGameJson.decode(SavedGameJson.encode(saved)))
                if (state.yardTriesUsed > 0) tries++
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random).pawn)
                    else -> LudoEngine.roll(state, random)
                }
            }
        }
        assert(tries > 0) { "No try to leave the yard was saved" }
    }

    @Test
    fun `every state of random Quick games comes back as it was saved - two pawns each`() {
        val random = Random(13)
        repeat(20) {
            var state = GameState.new(GameMode.QUICK, 4, first = 2)
            assertTrue(state.players.all { it.pawns.size == 2 })
            while (!state.isFinished) {
                val saved = game(state).copy(mode = GameMode.QUICK)
                assertEquals(saved, SavedGameJson.decode(SavedGameJson.encode(saved)))
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random).pawn)
                    else -> LudoEngine.roll(state, random)
                }
            }
        }
    }

    @Test
    fun `every state of random Two Dice games comes back as it was saved - a die left to move too - with house rules mixed in`() {
        val random = Random(17)
        var dieLeft = 0
        repeat(20) { index ->
            val houseRules = if (index % 2 == 0) emptySet() else HouseRule.entries.filter { random.nextBoolean() }.toSet()
            var state = GameState.new(GameMode.TWO_DICE, 4, first = 1, rules = GameMode.TWO_DICE.rules.withHouseRules(houseRules))
            while (!state.isFinished) {
                val saved = game(state).copy(mode = GameMode.TWO_DICE)
                assertEquals(saved, SavedGameJson.decode(SavedGameJson.encode(saved)))
                if ((state.phase as? TurnPhase.AwaitingMove)?.dice?.size == 1) dieLeft++
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random))
                    else -> LudoEngine.roll(state, random)
                }
            }
        }
        assert(dieLeft > 0) { "No die left to move was saved" }
    }

    @Test
    fun `a custom mode's game comes back under its name - and a built-in mode's has none written`() {
        val rules = GameMode.CLASSIC.rules.withHouseRules(setOf(HouseRule.BLOCKADES))
        val saved = game(GameState.new(GameMode.CLASSIC, 4, rules = rules)).copy(customName = "Cut-Throat")
        val json = SavedGameJson.encode(saved)
        assertTrue("\"customName\":\"Cut-Throat\"" in json)
        assertEquals(saved, SavedGameJson.decode(json))
        assertFalse("customName" in SavedGameJson.encode(game(GameState.new(GameMode.CLASSIC, 4))))
    }

    @Test
    fun `house rules are written by id - and one this app doesn't know fails the read`() {
        val rules = GameMode.CLASSIC.rules.withHouseRules(setOf(HouseRule.GUARDED_STARTS, HouseRule.NO_CAPTURE_ROLL))
        val json = SavedGameJson.encode(game(GameState.new(GameMode.CLASSIC, 4, rules = rules)))
        assertTrue("\"houseRules\":[\"no_capture_roll\",\"guarded_starts\"]" in json)
        assertEquals(rules, SavedGameJson.decode(json).state.rules)
        assertFailsWith<JsonParseException> { SavedGameJson.decode(json.replace("guarded_starts", "nope")) }
        // None on, none written.
        assertFalse("houseRules" in SavedGameJson.encode(game(GameState.new(GameMode.CLASSIC, 4))))
    }

    @Test
    fun `a game stopping at its first winner comes back stopping at it`() {
        val rules = GameMode.CLASSIC.rules.copy(stopAtFirstWinner = true)
        val saved = game(GameState.new(GameMode.CLASSIC, 4, rules = rules))
        val json = SavedGameJson.encode(saved)
        assertTrue("\"stopAtFirstWinner\":true" in json)
        assertEquals(saved, SavedGameJson.decode(json))
        // Off, it isn't written: the format test's game shows that.
        assertFalse("stopAtFirstWinner" in SavedGameJson.encode(game(GameState.new(GameMode.CLASSIC, 4))))
    }

    @Test
    fun `a roll earned mid-turn comes back as earned`() {
        // Red brings a pawn out with a 6: the next roll carries on the turn.
        val sixed = GameState.new(GameMode.CLASSIC, 4).let { LudoEngine.move(LudoEngine.applyRoll(it, 6), 0) }
        assertTrue(sixed.rollingAgain)
        val json = SavedGameJson.encode(game(sixed))
        assertTrue("\"rollingAgain\":true" in json)
        assertTrue("\"rollsAgain\":true" in json)
        assertEquals(sixed, SavedGameJson.decode(json).state)
    }

    @Test
    fun `a game saved by a newer app isn't read`() {
        val json = SavedGameJson.encode(game(GameState.new(GameMode.CLASSIC, 4))).replace("\"version\":2", "\"version\":3")
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
            // A try to leave the yard, with no house rule giving one.
            json.replace("\"rollAgainRun\":0", "\"rollAgainRun\":0,\"yardTriesUsed\":1"),
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
            "{\"version\":2,\"mode\":\"classic\",\"startedAt\":1763000000123,\"playMillis\":98765,\"finishMillis\":{},\"players\":[" +
                "{\"slot\":1,\"type\":\"HUMAN\",\"name\":\"Ada \\\"Quote\\\" é\",\"difficulty\":\"MEDIUM\",\"colour\":\"BLUE\"}," +
                "{\"slot\":2,\"type\":\"AI\",\"name\":\"Bot\",\"difficulty\":\"HARD\",\"colour\":\"RED\"}," +
                "{\"slot\":3,\"type\":\"AI\",\"name\":\"Cog\",\"difficulty\":\"EASY\",\"colour\":\"GREEN\"}," +
                "{\"slot\":4,\"type\":\"HUMAN\",\"name\":\"Dee\",\"difficulty\":\"MEDIUM\",\"colour\":\"YELLOW\"}]," +
                "\"rollOff\":{\"rounds\":[{\"0\":6,\"1\":6,\"2\":3,\"3\":1},{\"0\":2,\"1\":5}],\"first\":1}," +
                "\"id\":\"0123456789abcdef0123456789abcdef\",\"steps\":17," +
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
