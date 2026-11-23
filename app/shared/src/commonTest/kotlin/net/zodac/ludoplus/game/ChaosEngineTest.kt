package net.zodac.ludoplus.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.model.withHouseRules

private const val RED = 0
private const val GREEN = 1

private const val Y = YARD_PROGRESS
private const val HOME = 56

/** A Chaos game of four with each player's pawns at the progress given and only [powerUps] on the board, red to roll. */
private fun game(vararg pawns: List<Int>, powerUps: Map<Int, PowerUp> = emptyMap(), houseRules: Set<HouseRule> = emptySet(), seed: Long = 1): GameState {
    val new = GameState.new(GameMode.CHAOS, 4, rules = GameMode.CHAOS.rules.withHouseRules(houseRules), seed = seed)
    return new.copy(players = new.players.mapIndexed { i, player -> player.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }) }, powerUps = powerUps)
}

private fun GameState.shielding(player: Int, vararg pawns: Int) = copy(players = players.mapIndexed { i, side -> if (i == player) side.copy(shielded = pawns.toSet()) else side })
private fun GameState.doubled(player: Int, rolls: Int = 3) = copy(players = players.mapIndexed { i, side -> if (i == player) side.copy(doubledRolls = rolls) else side })
private fun GameState.movesFor(roll: Int): List<Move> = (LudoEngine.applyRoll(this, roll).phase as? TurnPhase.AwaitingMove)?.moves.orEmpty()
private fun GameState.play(roll: Int, pawn: Int = 0) = LudoEngine.move(LudoEngine.applyRoll(this, roll), pawn)
private val GameState.moved: GameEvent.Moved get() = lastEvent as GameEvent.Moved
private val GameState.effect: PowerUpEffect get() = assertNotNull(moved.effect)

/** The Chaos mode: power-ups on the track, taken by the pawn that ends its move on one. Red's progress is the square's own number. */
class ChaosEngineTest {

    @Test
    fun `a game starts with a power-up on each seat's stretch - never on a safe square - the same from the same seed`() {
        for (players in 2..8) {
            for (seed in 0L..40L) {
                val state = GameState.new(GameMode.CHAOS, players, rules = GameMode.CHAOS.rules.forPlayers(players), seed = seed)
                val board = state.board
                assertEquals((0..<board.seats).toList(), state.powerUps.keys.map { it / board.seatSpacing }.sorted())
                assertTrue(state.powerUps.keys.none(board::isSafe))
                assertEquals(state, GameState.new(GameMode.CHAOS, players, rules = GameMode.CHAOS.rules.forPlayers(players), seed = seed))
            }
        }
        val kinds = (0L..60L).flatMap { GameState.new(GameMode.CHAOS, 4, seed = it).powerUps.values }.toSet()
        assertEquals(PowerUp.entries.toSet(), kinds)
        assertEquals(emptyMap(), GameState.new(GameMode.CLASSIC, 4).powerUps)
    }

    @Test
    fun `one taken comes back eight rolls later - on a stretch with none - clear of pawns`() {
        var state = game(listOf(1, Y, Y, Y), powerUps = mapOf(3 to PowerUp.SHIELD, 16 to PowerUp.SWAP, 29 to PowerUp.SWAP, 42 to PowerUp.SWAP)).play(2)
        assertEquals(setOf(16, 29, 42), state.powerUps.keys)
        assertEquals(listOf(8), state.powerUpsDue)
        // Seven rolls by the others, none with a move, then red's, of 1.
        repeat(3) { state = LudoEngine.applyRoll(state, 1) }
        state = state.play(1)
        repeat(3) { state = LudoEngine.applyRoll(state, 1) }
        assertEquals(listOf(1), state.powerUpsDue)
        assertEquals(3, state.powerUps.size)
        state = LudoEngine.applyRoll(state, 1)
        assertEquals(emptyList(), state.powerUpsDue)
        val back = (state.powerUps.keys - setOf(16, 29, 42)).single()
        assertTrue(back in 1..12 && back != 8 && back != 4, "It came back on $back")
    }

    @Test
    fun `Double makes the next three rolls count twice - a 6 still leaving the yard - and past home they count once`() {
        val taken = game(listOf(1, Y, Y, Y), powerUps = mapOf(3 to PowerUp.DOUBLE)).play(2)
        assertEquals(3, taken.players[RED].doubledRolls)
        assertEquals(PowerUpEffect(PowerUp.DOUBLE), taken.effect)
        assertEquals(GREEN, taken.current)
        val doubled = taken.copy(current = RED)
        assertEquals(listOf(Move(0, 3, 13)), doubled.movesFor(5))
        assertEquals((1..3).map { Move(it, Y, 0) } + Move(0, 3, 15), doubled.movesFor(6).sortedBy { it.from })
        // Spent roll by roll: a roll with no move is one too.
        assertEquals(2, doubled.play(5).players[RED].doubledRolls)
        assertEquals(2, LudoEngine.applyRoll(game().doubled(RED), 2).players[RED].doubledRolls)
        assertEquals(2, LudoEngine.applyRoll(game(listOf(1, Y, Y, Y)).doubled(RED).copy(rollAgainRun = 2, rollingAgain = true), 6).players[RED].doubledRolls)
        assertEquals(listOf(Move(0, 3, 8)), doubled.doubled(RED, 0).movesFor(5))
        // 4 from home: a 2 is doubled to get there, and a 3 counts once, as doubled it would overshoot.
        val near = game(listOf(HOME - 4, Y, Y, Y)).doubled(RED)
        assertEquals(listOf(HOME, HOME - 1), listOf(2, 3).map { near.movesFor(it).single().to })
        // Another player's rolls are their own.
        assertEquals(listOf(Move(0, 10, 12)), game(listOf(1, Y, Y, Y), listOf(10, Y, Y, Y)).doubled(RED).copy(current = GREEN).movesFor(2))
    }

    @Test
    fun `a doubled 6 rolls again - as a 6 does`() {
        val state = game(listOf(1, Y, Y, Y)).doubled(RED).play(6)
        assertEquals(13, state.players[RED].pawns[0])
        assertTrue(state.rollingAgain)
        assertEquals(2, state.players[RED].doubledRolls)
    }

    @Test
    fun `Shield protects its pawn - a rival landing on it settles on the square before - the shield broken - and earns no roll`() {
        val shielded = game(listOf(1, Y, Y, Y), powerUps = mapOf(3 to PowerUp.SHIELD)).play(2)
        assertTrue(shielded.isShielded(RED, 0))
        // Green's progress 40 is square 1: a 2 would land on red's.
        val attacked = shielded.copy(players = shielded.players.mapIndexed { i, side -> if (i == GREEN) side.copy(pawns = listOf(40, Y, Y, Y)) else side })
        val move = Move(0, 40, 41, bouncedFrom = 42, brokenShields = listOf(PawnRef(RED, 0)))
        assertEquals(listOf(move), attacked.movesFor(2))
        assertEquals(42, move.rolledTo)
        val after = attacked.play(2)
        assertEquals(listOf(3, Y, Y, Y), after.players[RED].pawns)
        assertEquals(listOf(41, Y, Y, Y), after.players[GREEN].pawns)
        assertFalse(after.isShielded(RED, 0))
        assertFalse(after.rollingAgain)
        assertEquals(0, after.players[GREEN].captures)
        // Without it, the next is a capture.
        assertEquals(listOf(PawnRef(RED, 0)), after.copy(current = GREEN).movesFor(1).single().captures)
    }

    @Test
    fun `a pawn that bounces captures where it settles - earning a roll - and bounces again off another shield`() {
        // Yellow's progress 28 is square 2 (26 + 28 = 54), the one before red's shielded pawn on 3.
        val state = game(listOf(3, Y, Y, Y), listOf(38, Y, Y, Y), listOf(28, Y, Y, Y)).shielding(RED, 0).copy(current = GREEN)
        val move = state.movesFor(4).single()
        assertEquals(41, move.to)
        assertEquals(listOf(PawnRef(2, 0)), move.captures)
        assertTrue(state.play(4).rollingAgain)
        val both = state.shielding(2, 0).play(4)
        assertEquals(40, both.players[GREEN].pawns[0])
        assertEquals(listOf(PawnRef(RED, 0), PawnRef(2, 0)), both.moved.move.brokenShields)
        assertTrue(both.players.all { it.shielded.isEmpty() })
    }

    @Test
    fun `a pawn bounces no further back than where it came from`() {
        // Green on square 2, red's shielded pawn on 3: a 1 gets it nowhere, and breaks the shield.
        val state = game(listOf(3, Y, Y, Y), listOf(41, Y, Y, Y)).shielding(RED, 0).copy(current = GREEN).play(1)
        assertEquals(41, state.players[GREEN].pawns[0])
        assertFalse(state.isShielded(RED, 0))
        assertEquals(listOf(3, Y, Y, Y), state.players[RED].pawns)
    }

    @Test
    fun `a shield covers the pawns sharing its square - goes with a pawn off the track - and doesn't stop one leaving the yard`() {
        val pair = game(listOf(3, 3, Y, Y), listOf(40, Y, Y, Y)).shielding(RED, 1).copy(current = GREEN)
        assertEquals(emptyList(), pair.movesFor(2).single().captures)
        assertTrue(game(listOf(49, Y, Y, Y)).shielding(RED, 0).play(1).isShielded(RED, 0))
        assertFalse(game(listOf(49, Y, Y, Y)).shielding(RED, 0).play(2).isShielded(RED, 0))
        // Red's pawn on green's guarded start, square 13, as green comes out.
        val guarded = game(listOf(13, Y, Y, Y), houseRules = setOf(HouseRule.GUARDED_STARTS)).shielding(RED, 0).copy(current = GREEN)
        assertEquals(listOf(PawnRef(RED, 0)), guarded.movesFor(6).first().captures)
        val out = guarded.play(6)
        assertEquals(listOf(Y, Y, Y, Y), out.players[RED].pawns)
        assertFalse(out.isShielded(RED, 0))
    }

    @Test
    fun `Teleport takes the pawn 1 to 6 squares on - by the seed - capturing where it lands`() {
        val jumps = (0L..60L).map { seed -> game(listOf(1, Y, Y, Y), powerUps = mapOf(3 to PowerUp.TELEPORT), seed = seed).play(2) }
        assertEquals((4..9).toSet(), jumps.map { it.players[RED].pawns[0] }.toSet())
        for (state in jumps) {
            assertEquals(state.players[RED].pawns[0], state.effect.movedTo)
            assertEquals(Move(0, 1, 3), state.moved.move)
        }
        // Green's on square 5 (progress 44) is captured by a jump of 2, which earns a roll, and by no other.
        val captures = (0L..60L).map { seed -> game(listOf(1, Y, Y, Y), listOf(44, Y, Y, Y), powerUps = mapOf(3 to PowerUp.TELEPORT), seed = seed).play(2) }
        for (state in captures) {
            val landed = state.players[RED].pawns[0] == 5
            assertEquals(if (landed) listOf(PawnRef(GREEN, 0)) else emptyList(), state.effect.captures)
            assertEquals(landed, state.rollingAgain)
            assertEquals(if (landed) 1 else 0, state.players[RED].captures)
        }
        assertTrue(captures.any { it.rollingAgain })
    }

    @Test
    fun `Teleport from the last track square goes into the home column - or home - never past it`() {
        val states = (0L..60L).map { seed -> game(listOf(49, HOME, HOME, HOME), powerUps = mapOf(50 to PowerUp.TELEPORT), seed = seed).play(1) }
        assertEquals((51..HOME).toSet(), states.map { it.players[RED].pawns[0] }.toSet())
        // Home with the last pawn: finished, and placed.
        for (state in states) assertEquals(state.players[RED].pawns[0] == HOME, state.moved.finished && state.finishingOrder == listOf(RED))
    }

    @Test
    fun `Teleport bounces off a shield - and won't land on a blockade`() {
        val states = (0L..60L).map { seed -> game(listOf(1, Y, Y, Y), listOf(44, Y, Y, Y), powerUps = mapOf(3 to PowerUp.TELEPORT), seed = seed).shielding(GREEN, 0).play(2) }
        assertFalse(states.any { it.players[RED].pawns[0] == 5 })
        assertTrue(states.any { it.effect.brokenShields == listOf(PawnRef(GREEN, 0)) && it.players[RED].pawns[0] == 4 })
        val blocked = (0L..60L).map { seed ->
            game(listOf(1, Y, Y, Y), listOf(44, 44, Y, Y), powerUps = mapOf(3 to PowerUp.TELEPORT), houseRules = setOf(HouseRule.BLOCKADES), seed = seed).play(2)
        }
        assertFalse(blocked.any { it.players[RED].pawns[0] == 5 })
        assertTrue(blocked.any { it.players[RED].pawns[0] == 3 && it.effect.movedTo == null })
    }

    @Test
    fun `one power-up a move - a pawn teleported onto another leaves it there - to be taken by the next to land on it`() {
        val on = (4..9).filter { it != 8 }.associateWith { PowerUp.DOUBLE }
        val states = (0L..20L).map { seed -> game(listOf(1, Y, Y, Y), powerUps = on + (3 to PowerUp.TELEPORT), seed = seed).play(2) }
        for (state in states) {
            assertEquals(on, state.powerUps)
            assertEquals(0, state.players[RED].doubledRolls)
        }
    }

    @Test
    fun `Roll Again rolls again`() {
        val state = game(listOf(1, Y, Y, Y), powerUps = mapOf(3 to PowerUp.ROLL_AGAIN)).play(2)
        assertTrue(state.rollingAgain)
        assertEquals(RED, state.current)
        assertTrue(state.moved.rollsAgain)
    }

    @Test
    fun `Call Up brings out a pawn - and does nothing with none to bring`() {
        val out = game(listOf(1, 20, Y, Y), powerUps = mapOf(3 to PowerUp.CALL_UP)).play(2)
        assertEquals(listOf(3, 20, 0, Y), out.players[RED].pawns)
        assertEquals(PowerUpEffect(PowerUp.CALL_UP, calledUp = 2), out.effect)
        val none = game(listOf(1, 20, 30, HOME), powerUps = mapOf(3 to PowerUp.CALL_UP)).play(2)
        assertEquals(listOf(3, 20, 30, HOME), none.players[RED].pawns)
        assertNull(none.effect.calledUp)
        // Onto its guarded start, it captures there.
        val guarded = game(listOf(1, Y, Y, Y), listOf(39, Y, Y, Y), powerUps = mapOf(3 to PowerUp.CALL_UP), houseRules = setOf(HouseRule.GUARDED_STARTS)).play(2)
        assertEquals(listOf(PawnRef(GREEN, 0)), guarded.effect.captures)
        assertTrue(guarded.rollingAgain)
    }

    @Test
    fun `Swap trades places with the nearest rival ahead`() {
        // Green on square 30 (progress 17), yellow on 28 (progress 2).
        val state = game(listOf(1, Y, Y, Y), listOf(17, Y, Y, Y), listOf(2, Y, Y, Y), powerUps = mapOf(3 to PowerUp.SWAP))
        // Yellow's nearest, but 25 back would take it past its start: green's it is, 27 back, for which it's 10 too far.
        assertNull(state.play(2).effect.swapped)
        val swapped = game(listOf(1, Y, Y, Y), listOf(5, Y, Y, Y), listOf(40, Y, Y, Y), listOf(28, Y, Y, Y), powerUps = mapOf(3 to PowerUp.SWAP)).play(2)
        // Yellow on 14, blue on 15, and green on 18 (its start is between): yellow's is the nearest.
        assertEquals(PawnRef(2, 0), swapped.effect.swapped)
        assertEquals(14, swapped.players[RED].pawns[0])
        assertEquals(29, swapped.players[2].pawns[0])
        assertEquals(3, swapped.board.trackSquare(2, 29))
        assertEquals(0, swapped.players[RED].captures)
    }

    @Test
    fun `Swap passes over a rival who is safe - shielded - or not alone`() {
        fun swap(yellow: List<Int>, shield: Boolean = false) =
            game(listOf(1, Y, Y, Y), emptyYard(), yellow, powerUps = mapOf(3 to PowerUp.SWAP)).let { if (shield) it.shielding(2, 0) else it }.play(2).effect.swapped
        assertEquals(PawnRef(2, 0), swap(listOf(40, Y, Y, Y)))
        assertNull(swap(listOf(40, Y, Y, Y), shield = true))
        assertNull(swap(listOf(40, 40, Y, Y)))
        // Square 8's star.
        assertNull(swap(listOf(34, Y, Y, Y)))
        // Nobody ahead.
        assertNull(swap(emptyYard()))
    }

    private fun emptyYard() = listOf(Y, Y, Y, Y)

    @Test
    fun `Medium and Hard go for a power-up - and Hard doesn't see what its chances will be`() {
        for (difficulty in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val strategy = CpuStrategy.of(difficulty)
            val state = LudoEngine.applyRoll(game(listOf(20, 1, Y, Y), powerUps = mapOf(3 to PowerUp.ROLL_AGAIN)), 2)
            assertEquals(1, strategy.choose(state, (state.phase as TurnPhase.AwaitingMove).moves, Random(1)).pawn, "$difficulty")
        }
        val values = (0L..30L).map { seed ->
            val state = LudoEngine.applyRoll(game(listOf(1, Y, Y, Y), listOf(44, Y, Y, Y), powerUps = mapOf(3 to PowerUp.TELEPORT), seed = seed), 2)
            ExpectimaxStrategy().value(state, (state.phase as TurnPhase.AwaitingMove).moves.single())
        }
        assertEquals(1, values.distinct().size)
    }

    @Test
    fun `games of every size - with and without house rules - play out - and the same from the same seed`() {
        fun play(players: Int, houseRules: Set<HouseRule>): GameState {
            val random = Random(players)
            var state = GameState.new(GameMode.CHAOS, players, rules = GameMode.CHAOS.rules.forPlayers(players).withHouseRules(houseRules), seed = players.toLong())
            var steps = 0
            while (!state.isFinished) {
                check(++steps < 100_000) { "Still going after $steps steps" }
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random))
                    else -> LudoEngine.roll(state, random)
                }
                val board = state.board
                assertTrue(state.players.all { side -> side.pawns.all { it in Y..board.homeProgress } && side.shielded.all { side.pawns[it] in 0..<board.trackSteps } })
                assertTrue(state.powerUps.keys.none(board::isSafe) && state.powerUps.size + state.powerUpsDue.size <= board.seats)
                // Nobody's pawns share an unsafe square with a rival's.
                val squares = state.players.flatMapIndexed { i, side -> side.pawns.filter { it in 0..<board.trackSteps }.map { board.trackSquare(side.seat, it) to i }.distinct() }
                if (houseRules.isEmpty()) assertTrue(squares.filterNot { board.isSafe(it.first) }.groupBy { it.first }.values.all { it.size == 1 })
            }
            return state
        }
        for (players in 2..8) {
            assertEquals(play(players, emptySet()), play(players, emptySet()))
            play(players, HouseRule.entries.toSet())
        }
    }
}
