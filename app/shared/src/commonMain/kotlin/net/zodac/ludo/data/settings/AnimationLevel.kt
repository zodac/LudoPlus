package net.zodac.ludo.data.settings

import net.zodac.ludo.model.StoredId

/**
 * The player's "Animations" setting: how much of the app moves, and how fast it's drawn. A step down trades
 * smoothness and decoration for less work a frame (an older phone, a longer battery), never what the game does.
 *
 * Each level is a preset of [MotionSettings] ([motion]), which the Graphics page's Advanced graphics settings show and the
 * player can change group by group; settings matching no level leave none picked ([matching] is null).
 *
 * Saved by [id]. [HIGH] is the default; a player who had the old "Remove animations" switch on starts at [OFF].
 */
enum class AnimationLevel(override val id: String, val motion: MotionSettings) : StoredId {
    HIGH("HIGH", MotionSettings.of(decoration = FrameRate.MAX, gameplay = FrameRate.MAX, dieRoll = FrameRate.MAX, pawnMove = FrameRate.MAX, idleTimeout = IdleTimeout.SECONDS_60)),
    MEDIUM("MEDIUM", MotionSettings.of(decoration = FrameRate.OFF, gameplay = FrameRate.FPS_60, dieRoll = FrameRate.FPS_60, pawnMove = FrameRate.FPS_60, idleTimeout = IdleTimeout.SECONDS_60)),
    LOW("LOW", MotionSettings.of(decoration = FrameRate.OFF, gameplay = FrameRate.FPS_30, dieRoll = FrameRate.FPS_30, pawnMove = FrameRate.FPS_30, idleTimeout = IdleTimeout.SECONDS_30)),
    OFF("OFF", MotionSettings.of(decoration = FrameRate.OFF, gameplay = FrameRate.OFF, dieRoll = FrameRate.OFF, pawnMove = FrameRate.OFF, idleTimeout = IdleTimeout.SECONDS_30)),
    ;

    companion object {
        val default = HIGH

        /** The level whose preset [motion] is, or null for none. */
        fun matching(motion: MotionSettings): AnimationLevel? = entries.firstOrNull { it.motion == motion }
    }
}
