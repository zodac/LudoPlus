package net.zodac.ludoplus.data.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.ludoplus.data.JsonParseException
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LogEntry
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.MoveLog
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.model.TeamMode
import net.zodac.ludoplus.model.withHouseRules
import net.zodac.ludoplus.ui.game.StartedGame

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
        first = 1,
        startedAtEpochMillis = 1_763_000_000_123,
        playMillis = 98_765,
        // Every finished or knocked-out player's, as the game keeps them.
        finishMillis = (state.finishingOrder + state.knockedOut).distinct().associateWith { 1_000L * (it + 1) },
        id = "0123456789abcdef0123456789abcdef",
        steps = 17,
    )

    @Test
    fun `an ended game - a Nearby host's last word - comes back ended - every player placed - its last ones as they stood`() {
        val random = Random(9)
        var state = GameState.new(GameMode.CLASSIC, 4, first = 1).let { it.copy(rules = it.rules.copy(stopAtFirstWinner = true)) }
        while (!state.isFinished) {
            state = when (val phase = state.phase) {
                is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random).pawn)
                else -> LudoEngine.roll(state, random)
            }
        }
        val ended = game(state)
        assertEquals(ended, SavedGameJson.decode(SavedGameJson.encode(ended)))
        assertEquals(TurnPhase.Finished, SavedGameJson.decode(SavedGameJson.encode(ended)).state.phase)
    }

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
    fun `every state of random Portals games comes back as it was saved - moves through portals too`() {
        val random = Random(19)
        var portals = 0
        repeat(10) {
            var state = GameState.new(GameMode.PORTALS, 4)
            while (!state.isFinished) {
                val saved = game(state).copy(mode = GameMode.PORTALS)
                assertEquals(saved, SavedGameJson.decode(SavedGameJson.encode(saved)))
                if ((state.lastEvent as? GameEvent.Moved)?.move?.via != null) portals++
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random))
                    else -> LudoEngine.roll(state, random)
                }
            }
        }
        assert(portals > 0) { "No move through a portal was saved" }
    }

    @Test
    fun `every state of random Survival games comes back as it was saved - rounds - and players knocked out - to the end a host sends`() {
        val random = Random(23)
        var knockedOut = 0
        var suddenDeath = 0
        var lost = 0
        repeat(6) {
            var state = GameState.new(GameMode.SURVIVAL, 4)
            while (!state.isFinished) {
                val saved = game(state).copy(mode = GameMode.SURVIVAL)
                assertEquals(saved, SavedGameJson.decode(SavedGameJson.encode(saved)))
                if (state.knockedOut.isNotEmpty()) knockedOut++
                if (state.suddenDeathStage == 2) suddenDeath++
                if (state.justLost.isNotEmpty()) lost++
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random))
                    else -> LudoEngine.roll(state, random)
                }
            }
            val ended = game(state).copy(mode = GameMode.SURVIVAL)
            assertEquals(ended, SavedGameJson.decode(SavedGameJson.encode(ended)))
        }
        // A game that ends before the Cataclysm is most of them: one brought near it, with few enough pawns to last.
        val new = GameState.new(GameMode.SURVIVAL, 4, seed = 5)
        var state = new.copy(roundsPlayed = 74, players = new.players.map { it.copy(pawns = listOf(0) + List(5) { PawnPosition.OUT_PROGRESS }) })
        while (!state.isFinished) {
            val saved = game(state).copy(mode = GameMode.SURVIVAL)
            assertEquals(saved, SavedGameJson.decode(SavedGameJson.encode(saved)))
            if (state.justLost.isNotEmpty()) lost++
            state = when (val phase = state.phase) {
                is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random))
                else -> LudoEngine.roll(state, random)
            }
        }
        assert(knockedOut > 0 && suddenDeath > 0 && lost > 0) { "No player knocked out, or no sudden death or lost squares, were saved" }
    }

    @Test
    fun `every state of random Chaos games comes back as it was saved - power-ups - shields - and doubled rolls - and plays on the same`() {
        val random = Random(29)
        val effects = mutableSetOf<PowerUp>()
        repeat(6) { index ->
            var state = GameState.new(GameMode.CHAOS, 4, seed = index - 3L)
            while (!state.isFinished) {
                val saved = game(state).copy(mode = GameMode.CHAOS)
                val back = SavedGameJson.decode(SavedGameJson.encode(saved))
                assertEquals(saved, back)
                (state.lastEvent as? GameEvent.Moved)?.effect?.let { effects += it.powerUp }
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> phase.moves.random(random).let { move ->
                        LudoEngine.move(state, move).also { assertEquals(it, LudoEngine.move(back.state, move)) }
                    }
                    else -> LudoEngine.roll(state, random)
                }
            }
        }
        assertEquals(PowerUp.entries.toSet(), effects)
    }

    @Test
    fun `every state of random Haunted House games comes back as it was saved - ghosts and the moves they make - and plays on the same`() {
        val random = Random(31)
        var caught = 0
        repeat(4) { index ->
            var state = GameState.new(GameMode.HAUNTED_HOUSE, 4, seed = index.toLong())
            while (!state.isFinished) {
                val saved = game(state).copy(mode = GameMode.HAUNTED_HOUSE)
                val back = SavedGameJson.decode(SavedGameJson.encode(saved))
                assertEquals(saved, back)
                caught += state.ghostMove?.caught?.size ?: 0
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> phase.moves.random(random).let { move ->
                        LudoEngine.move(state, move).also { assertEquals(it, LudoEngine.move(back.state, move)) }
                    }
                    else -> LudoEngine.roll(state, random)
                }
            }
        }
        assert(caught > 0) { "No ghost caught a pawn" }
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
                "\"rollOff\":{\"rounds\":[],\"first\":1}," +
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
    fun `a save from before the roll-off went reads - keeping who went first`() {
        val json = SavedGameJson.encode(game(GameState.new(GameMode.CLASSIC, 4, first = 1)))
            .replace("\"rounds\":[]", "\"rounds\":[{\"0\":6,\"1\":6,\"2\":3,\"3\":1},{\"0\":2,\"1\":5}]")
        assertEquals(1, SavedGameJson.decode(json).first)
    }

    @Test
    fun `a first player who isn't in the game fails the read`() {
        val json = SavedGameJson.encode(game(GameState.new(GameMode.CLASSIC, 4))).replace("\"first\":1", "\"first\":4")
        assertFailsWith<IllegalArgumentException> { SavedGameJson.decode(json) }
    }
}

class SavedGameJsonLogTest {

    private val players = listOf(PlayerConfig(1, PlayerType.HUMAN, "Ada"), PlayerConfig(2, PlayerType.AI, "Bot"))

    /** A game a few dozen states in, with its log. */
    private fun played(): StartedGame {
        val random = Random(7)
        var state = GameState.new(GameMode.CLASSIC, 2)
        var log = emptyList<LogEntry>()
        repeat(60) { step ->
            val next = when (val phase = state.phase) {
                is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random))
                else -> LudoEngine.roll(state, random)
            }
            log = MoveLog.next(log, state, next, 1_763_000_000_000 + step * 1_500L)
            state = next
        }
        return StartedGame(GameMode.CLASSIC, players, state, first = 0, id = "a", log = log)
    }

    @Test
    fun `a game's move log comes back as it was saved - and a Nearby message leaves it out`() {
        val game = played()
        assertTrue(game.log.size > 10)
        assertEquals(game, SavedGameJson.decode(SavedGameJson.encode(game)))
        assertEquals(game.copy(log = emptyList()), SavedGameJson.decode(SavedGameJson.encode(game, withLog = false)))
        assertFalse("\"log\"" in SavedGameJson.encode(game, withLog = false))
    }

    @Test
    fun `a log that can't be read is dropped - the game still comes back`() {
        val game = played()
        val broken = SavedGameJson.encode(game).replace("\"log\":[", "\"log\":[{\"event\":7},")
        assertEquals(game.copy(log = emptyList()), SavedGameJson.decode(broken))
    }
}
