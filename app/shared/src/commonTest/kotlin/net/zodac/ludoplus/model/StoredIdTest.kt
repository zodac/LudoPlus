package net.zodac.ludoplus.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.zodac.ludoplus.data.settings.AnimationLevel
import net.zodac.ludoplus.data.settings.FrameRate
import net.zodac.ludoplus.data.settings.IdleTimeout
import net.zodac.ludoplus.data.settings.MotionGroup
import net.zodac.ludoplus.data.settings.ScreenOrientation

/**
 * Pins every [StoredId]: these strings are in players' settings, saved games, and records, so each one is permanent once
 * released. A constant can be renamed or moved; its id can't. A new constant adds its id to the list here.
 */
class StoredIdTest {

    private fun <E> ids(entries: List<E>): List<String> where E : Enum<E>, E : StoredId = entries.map { it.id }

    @Test
    fun `every stored id is the one released - in declaration order`() {
        assertEquals(listOf("HUMAN", "AI", "NEARBY"), ids(PlayerType.entries))
        assertEquals(listOf("EASY", "MEDIUM", "HARD"), ids(Difficulty.entries))
        assertEquals(
            listOf("RED", "GREEN", "YELLOW", "BLUE", "ORANGE", "CYAN", "PINK", "PURPLE", "LIME", "MAGENTA", "BERRY", "SCARLET"),
            ids(PlayerColour.entries),
        )
        assertEquals(listOf("classic", "colour_rush", "two_dice", "portals", "survival", "chaos", "haunted_house"), ids(GameMode.entries))
        // Each mode's online boards. Survival has none.
        assertEquals(listOf("classic", null, "two_dice", "portals", null, "chaos", "haunted_house"), GameMode.entries.map { it.onlineLeaderboardKey })
        assertEquals(
            listOf("no_capture_roll", "no_home_roll", "endless_sixes", "exit_on_one", "free_start", "three_tries", "blockades", "must_capture", "guarded_starts"),
            ids(HouseRule.entries),
        )
        assertEquals(listOf("off", "two_teams", "pairs"), ids(TeamMode.entries))
        assertEquals(listOf("double", "shield", "roll_again", "call_up", "swap"), ids(PowerUp.entries))
        assertEquals(listOf("HIGH", "MEDIUM", "LOW", "OFF"), ids(AnimationLevel.entries))
        assertEquals(listOf("DEVICE", "PORTRAIT", "LANDSCAPE"), ids(ScreenOrientation.entries))
        assertEquals(listOf("OFF", "30", "60", "MAX"), ids(FrameRate.entries))
        assertEquals(listOf("30", "60", "90", "OFF"), ids(IdleTimeout.entries))
        // Each group's saved setting is motion_<key>.
        assertEquals(listOf("dice", "background", "menus", "banners", "celebrations", "die_roll", "pawn_move"), MotionGroup.entries.map { it.key })
        assertEquals(
            listOf(
                "GAMES_PLAYED", "GAMES_WON", "WIN_STREAK", "CAPTURES", "PAWNS_HOME", "DIE_ROLLS", "SIXES_ROLLED", "SQUARES_MOVED", "LAST_PLACES",
                "MODES_FINISHED", "PLAY_SECONDS",
            ),
            ids(AchievementCounter.entries),
        )
    }

    @Test
    fun `an id reads back as its value - and one nothing recognises as nothing`() {
        assertEquals(PlayerColour.CYAN, storedIdOf<PlayerColour>("CYAN"))
        assertEquals(GameMode.HAUNTED_HOUSE, storedIdOf<GameMode>("haunted_house"))
        assertNull(storedIdOf<Difficulty>("IMPOSSIBLE"))
        assertNull(storedIdOf<Difficulty>("hard"))
    }
}
