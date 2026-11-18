package net.zodac.ludoplus.online

import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Play Games ids: none until LudoPlus's Play Console project exists, and from the first mapped, every achievement
 * and board once each, each a Play id.
 */
class PlayGamesIdsTest {

    private val playId = Regex("CgkI[A-Za-z0-9_-]+")

    @Test
    fun `every achievement is mapped once to a Play id - or none is yet`() {
        if (playAchievementIds.isEmpty()) return
        assertEquals(Achievement.entries.toSet(), playAchievementIds.keys)
        assertEquals(playAchievementIds.size, playAchievementIds.values.toSet().size)
        assertTrue(playAchievementIds.values.toString(), playAchievementIds.values.all { it.matches(playId) })
    }

    @Test
    fun `every board has an id of its own - not an achievement's - or none is yet`() {
        if (playLeaderboardIds.isEmpty()) return
        assertEquals(playLeaderboardKeys.toSet(), playLeaderboardIds.keys)
        assertEquals(playLeaderboardIds.size, playLeaderboardIds.values.toSet().size)
        assertTrue(playLeaderboardIds.values.toString(), playLeaderboardIds.values.all { it.matches(playId) })
        assertTrue(playLeaderboardIds.values.none { it in playAchievementIds.values })
    }

    @Test
    fun `there is a board for each mode and player count - 21 of Play's 70 - each key its own`() {
        assertEquals(GameMode.entries.size * 7, playLeaderboardKeys.size)
        assertTrue(playLeaderboardKeys.size <= 70)
        assertEquals(playLeaderboardKeys.size, playLeaderboardKeys.toSet().size)
        assertEquals("classic_4p", GameMode.CLASSIC.onlineLeaderboardKey(4))
    }

    @Test
    fun `an incremental achievement fits Play's steps`() {
        for (achievement in Achievement.entries) {
            val type = achievement.playType as? PlayAchievementType.Incremental ?: continue
            assertTrue(achievement.name, type.steps in 2..PLAY_MAX_STEPS)
            assertTrue(achievement.name, type.steps * type.perStep >= achievement.target)
        }
    }
}
