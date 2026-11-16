package net.zodac.ludo.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludo.model.GameMode

class GameModeColourTest {

    @Test
    fun `Classic is the brand violet - every mode's colour is its own - and none is the red of a loss`() {
        assertEquals(Primary, GameMode.CLASSIC.color)
        val colours = GameMode.entries.map { it.color }
        assertEquals(colours.size, colours.toSet().size)
        assertTrue(Error !in colours)
    }
}
