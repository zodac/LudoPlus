package net.zodac.ludo.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.zodac.ludo.game.TutorialScript.Companion.CPU
import net.zodac.ludo.game.TutorialScript.Companion.YOU

/** The classic tutorial's script, played through the real engine: every step is one the rules allow, and teaches its part. */
class TutorialScriptTest {

    private val script = TutorialScript.CLASSIC

    /** Each state of [part] after each of its steps, from its start (first) - failing on any step the rules don't allow. */
    private fun play(part: Int): List<GameState> {
        val states = mutableListOf(script.start(part))
        for (step in script.parts[part].steps) {
            val state = states.last()
            val player = if (step is TutorialStep.Roll || step is TutorialStep.Move) YOU else CPU
            assertEquals(player, state.current, "$step on ${state.current}'s turn")
            states += when (step) {
                is TutorialStep.Roll, is TutorialStep.CpuRoll -> {
                    assertEquals(TurnPhase.AwaitingRoll, state.phase, "$step")
                    LudoEngine.applyRoll(state, if (step is TutorialStep.Roll) step.value else (step as TutorialStep.CpuRoll).value)
                }
                is TutorialStep.Move, is TutorialStep.CpuMove -> {
                    val pawn = if (step is TutorialStep.Move) step.pawn else (step as TutorialStep.CpuMove).pawn
                    val phase = assertIs<TurnPhase.AwaitingMove>(state.phase, "$step")
                    assertTrue(phase.moves.any { it.pawn == pawn }, "$step isn't a legal move: ${phase.moves}")
                    LudoEngine.move(state, pawn)
                }
            }
        }
        return states
    }

    private val List<GameState>.events get() = drop(1).map { it.lastEvent }

    @Test
    fun `every part plays through the real rules - and each but the last ends with the turn passed on`() {
        script.parts.indices.forEach { part ->
            val end = play(part).last()
            if (part < script.parts.lastIndex) {
                assertEquals(CPU, end.current, "part $part ends with the player's turn over")
                assertEquals(TurnPhase.AwaitingRoll, end.phase)
            }
        }
    }

    @Test
    fun `getting out - a 3 can't leave the yard - Green's 6 can and rolls again - then the player's 6 does the same`() {
        val events = play(0).events
        assertEquals(GameEvent.NoMove(YOU, 3), events[0])
        val greenOut = assertIs<GameEvent.Moved>(events[2])
        assertEquals(-1, greenOut.move.from)
        assertTrue(greenOut.rollsAgain)
        val out = assertIs<GameEvent.Moved>(events[6])
        assertEquals(YOU, out.player)
        assertEquals(6, out.roll)
        assertEquals(-1, out.move.from)
        assertTrue(out.rollsAgain, "the 6 earns another roll")
        assertEquals(false, assertIs<GameEvent.Moved>(events[8]).rollsAgain)
    }

    @Test
    fun `captures - the player lands on Green's pawn and rolls again for it`() {
        val states = play(1)
        val capture = assertIs<GameEvent.Moved>(states.events[1])
        assertEquals(listOf(PawnRef(CPU, 0)), capture.move.captures)
        assertTrue(capture.rollsAgain)
        assertTrue(capture.roll != 6, "the roll again is the capture's, not a 6's")
        assertEquals(1, states.last().players[YOU].captures)
    }

    @Test
    fun `home - the player brings a pawn home with the exact roll and rolls again for it`() {
        val states = play(2)
        val home = assertIs<GameEvent.Moved>(states.events[1])
        assertEquals(script.start(2).board.homeProgress, home.move.to)
        assertTrue(home.rollsAgain)
        assertTrue(home.roll != 6, "the roll again is the pawn home's, not a 6's")
        assertEquals(1, states.last().players[YOU].pawnsHome(states.last().board))
    }

    @Test
    fun `winning - a roll too big passes the turn - then the exact roll brings the last pawn home and wins`() {
        val states = play(3)
        assertEquals(GameEvent.NoMove(YOU, 5), states.events[0])
        val win = assertIs<GameEvent.Moved>(states.events.last())
        assertTrue(win.finished)
        assertEquals(TurnPhase.Finished, states.last().phase)
        assertEquals(YOU, states.last().winner)
    }

    @Test
    fun `no step offers a capture the part didn't plan - only the capture part captures`() {
        script.parts.indices.forEach { part ->
            val captures = play(part).events.filterIsInstance<GameEvent.Moved>().sumOf { it.move.captures.size }
            assertEquals(if (part == 1) 1 else 0, captures, "part $part")
        }
    }
}
