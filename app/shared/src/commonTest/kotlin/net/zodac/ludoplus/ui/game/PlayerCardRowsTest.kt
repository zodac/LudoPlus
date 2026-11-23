package net.zodac.ludoplus.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.BoardGeometry

/** The player cards over the board, laid out as the yards are: rows of player indices, from 0, left to right. */
class PlayerCardRowsTest {

    private fun rows(players: Int): List<List<Int?>> {
        val board = BoardGeometry.forPlayers(players)
        return playerCardRows(BoardLayout.of(board), board.seatsFor(players))
    }

    @Test
    fun `the first player's yard is at the board's bottom left on every board`() {
        for (players in BoardGeometry.MIN_PLAYERS..BoardGeometry.MAX_PLAYERS) {
            val layout = BoardLayout.of(BoardGeometry.forPlayers(players))
            val yard = layout.yard(0).let { outline -> outline.reduce { a, b -> a + b } / outline.size.toFloat() }
            assertTrue(yard.x < layout.size / 2 && yard.y > layout.size / 2, "$players players: $yard")
        }
    }

    @Test
    fun `four players' cards sit as their yards do - the first bottom left - and two players' side by side`() {
        assertEquals(listOf(listOf(1, 2), listOf(0, 3)), rows(4))
        assertEquals(listOf(listOf<Int?>(0, 1)), rows(2))
    }

    @Test
    fun `a yard straight above the middle has a row to itself over the pairs`() {
        assertEquals(listOf(listOf(1), listOf(0, 2)), rows(3))
        assertEquals(listOf(listOf(2), listOf(1, 3), listOf(0, 4)), rows(5))
        assertEquals(listOf(listOf(3), listOf(2, 4), listOf(1, 5), listOf(0, 6)), rows(7))
    }

    @Test
    fun `six and eight players' cards pair down two columns - the first at the bottom of the left`() {
        assertEquals(listOf(listOf(2, 3), listOf(1, 4), listOf(0, 5)), rows(6))
        assertEquals(listOf(listOf(3, 4), listOf(2, 5), listOf(1, 6), listOf(0, 7)), rows(8))
    }
}
