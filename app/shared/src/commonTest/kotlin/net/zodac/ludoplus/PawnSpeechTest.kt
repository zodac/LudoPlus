package net.zodac.ludoplus

import androidx.compose.material3.Text
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.ui.game.pawnSpoken

/** What TalkBack says for a pawn, in each place it can be, word for word. */
class PawnSpeechTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun assertSpoken(expected: String, colour: PlayerColour, number: Int, position: PawnPosition) {
        showcase.show { Text(pawnSpoken(colour, number, position, BoardGeometry.CLASSIC)) }
        compose.onNodeWithText(expected).assertExists()
    }

    @Test
    fun `a pawn's colour - its number - and where it is`() = uiTest {
        assertSpoken("Red pawn 1, in the yard", PlayerColour.RED, 1, PawnPosition.Yard)
        assertSpoken("Red pawn 2, 14 tiles from home", PlayerColour.RED, 2, PawnPosition.Track(42))
        assertSpoken("Green pawn 3, 56 tiles from home", PlayerColour.GREEN, 3, PawnPosition.Track(0))
        assertSpoken("Yellow pawn 4, in the home column, 5 tiles from home", PlayerColour.YELLOW, 4, PawnPosition.HomeColumn(0))
        assertSpoken("Blue pawn 1, in the home column, 1 tile from home", PlayerColour.BLUE, 1, PawnPosition.HomeColumn(4))
        assertSpoken("Blue pawn 2, home", PlayerColour.BLUE, 2, PawnPosition.Home)
    }
}
