package net.zodac.ludo.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class RollOffTest {

    private fun rollOff(vararg rolls: Int): RollOff {
        val die = rolls.iterator()
        return RollOff.decide(playerCount = 4) { die.next() }.also { assertEquals(false, die.hasNext(), "every scripted roll used") }
    }

    @Test
    fun `the highest roll goes first`() {
        val result = rollOff(3, 5, 2, 4)
        assertEquals(1, result.first)
        assertEquals(listOf(mapOf(0 to 3, 1 to 5, 2 to 2, 3 to 4)), result.rounds)
    }

    @Test
    fun `players tied on the highest roll roll again alone - until one is highest`() {
        // Red, yellow, and blue tie on 6; red and yellow tie again on 2; then yellow's 4 beats red's 1.
        val result = rollOff(6, 3, 6, 6, 2, 2, 1, 1, 4)
        assertEquals(2, result.first)
        assertEquals(listOf(mapOf(0 to 6, 1 to 3, 2 to 6, 3 to 6), mapOf(0 to 2, 2 to 2, 3 to 1), mapOf(0 to 1, 2 to 4)), result.rounds)
    }

    @Test
    fun `a tie lower down is no tie at all`() {
        assertEquals(3, rollOff(2, 2, 2, 6).first)
    }

    @Test
    fun `a seeded roll-off always decides - with every roll on the die`() {
        repeat(500) { seed ->
            val result = RollOff.roll(playerCount = 4, dieFaces = 6, random = Random(seed))
            assertEquals(true, result.first in 0..3)
            assertEquals(true, result.rounds.all { round -> round.values.all { it in 1..6 } })
            assertEquals(result.first, result.rounds.last().maxBy { it.value }.key)
        }
    }
}
