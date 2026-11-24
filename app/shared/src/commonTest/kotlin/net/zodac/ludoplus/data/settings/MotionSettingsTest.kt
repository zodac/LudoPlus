package net.zodac.ludoplus.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.game.style.StyleCategory

/** Each level's preset, the window's frame rate from the groups', and what counts as a level rather than none. */
class MotionSettingsTest {

    @Test
    fun `each level's preset - the styles' decoration - the menus, banners and celebrations - the die's roll and the pawns' moves`() {
        assertEquals(
            listOf(
                List(7) { FrameRate.MAX },
                List(2) { FrameRate.OFF } + List(5) { FrameRate.FPS_60 },
                List(2) { FrameRate.OFF } + List(5) { FrameRate.FPS_30 },
                List(7) { FrameRate.OFF },
            ),
            AnimationLevel.entries.map { level -> MotionGroup.entries.map(level.motion::rate) },
        )
        // The window's frames: uncapped at High, then 60, 30, and 30.
        assertEquals(listOf(null, 60, 30, 30), AnimationLevel.entries.map { it.motion.maxFramesPerSecond })
    }

    @Test
    fun `the window runs at the fastest moving group's rate - uncapped if one is at max - and at 30 with everything still`() {
        val off = AnimationLevel.OFF.motion
        assertEquals(30, off.maxFramesPerSecond)
        assertEquals(60, off.with(MotionGroup.PAWN_MOVE, FrameRate.FPS_60).maxFramesPerSecond)
        assertEquals(60, off.with(MotionGroup.DICE, FrameRate.FPS_30).with(MotionGroup.DIE_ROLL, FrameRate.FPS_60).maxFramesPerSecond)
        assertNull(off.with(MotionGroup.BACKGROUND, FrameRate.MAX).maxFramesPerSecond)
    }

    @Test
    fun `the decoration groups are the styles with moving art - the die and the background - each moving on its own`() {
        assertEquals(listOf(StyleCategory.DICE, StyleCategory.BACKGROUND), StyleCategory.entries - setOf(StyleCategory.BOARD, StyleCategory.PAWNS))
        assertEquals(listOf(MotionGroup.DICE, MotionGroup.BACKGROUND), MotionGroup.decoration)
        val diceStill = AnimationLevel.HIGH.motion.with(MotionGroup.DICE, FrameRate.OFF)
        assertEquals(listOf(MotionGroup.BACKGROUND), MotionGroup.decoration.filter(diceStill::moves))
    }

    @Test
    fun `the die and the pawns move on their own`() {
        val dieStill = AnimationLevel.HIGH.motion.with(MotionGroup.DIE_ROLL, FrameRate.OFF)
        assertFalse(dieStill.rollMotion)
        assertTrue(dieStill.pawnMotion)
        assertTrue(dieStill.menusMotion)
        val pawnsStill = AnimationLevel.HIGH.motion.with(MotionGroup.PAWN_MOVE, FrameRate.OFF)
        assertFalse(pawnsStill.pawnMotion)
        assertTrue(pawnsStill.rollMotion)
    }

    @Test
    fun `settings match a level only if every group does`() {
        AnimationLevel.entries.forEach { assertEquals(it, AnimationLevel.matching(it.motion)) }
        assertNull(AnimationLevel.matching(AnimationLevel.HIGH.motion.with(MotionGroup.BANNERS, FrameRate.FPS_60)))
        assertNull(AnimationLevel.matching(AnimationLevel.HIGH.motion.copy(idleTimeout = IdleTimeout.SECONDS_90)))
    }

    @Test
    fun `settings read back from their ids - and anything else is null`() {
        val custom = AnimationLevel.LOW.motion.with(MotionGroup.DIE_ROLL, FrameRate.MAX).with(MotionGroup.DICE, FrameRate.FPS_60)
        assertEquals(listOf("60", "OFF", "30", "30", "30", "MAX", "30", "30"), custom.ids())
        assertEquals(custom, MotionSettings.fromIds(custom.ids()))
        assertNull(MotionSettings.fromIds(custom.ids().drop(1)))
        assertNull(MotionSettings.fromIds(custom.ids().dropLast(1) + "45"))
        assertNull(MotionSettings.fromIds(listOf("FAST") + custom.ids().drop(1)))
    }
}
