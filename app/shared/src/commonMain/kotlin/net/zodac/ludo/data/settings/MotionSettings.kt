package net.zodac.ludo.data.settings

import net.zodac.ludo.model.StoredId
import net.zodac.ludo.model.storedIdOf

/**
 * How fast one [MotionGroup] is drawn: not at all ([OFF]), at most 30 or 60 frames a second, or at whatever the screen
 * does ([MAX]: 90 or 120Hz on many phones, 60 on others). Saved by [id].
 */
enum class FrameRate(override val id: String, val framesPerSecond: Int?) : StoredId {
    OFF("OFF", null),
    FPS_30("30", 30),
    FPS_60("60", 60),
    MAX("MAX", null),
    ;

    /** Whether anything in the group moves at all. */
    val moves: Boolean get() = this != OFF
}

/**
 * The groups of the app's motion, each with a [FrameRate] of its own in [MotionSettings]. [key] names its saved setting
 * (`motion_<key>`), so it's permanent once released.
 *
 * [decoration] is what moves on its own while nothing is happening ([DICE]: the die style's own sparkle or glow;
 * [BACKGROUND]: the menu's backdrop and the game's background style). Off, each holds the still pose it has under
 * reduced motion.
 */
enum class MotionGroup(val key: String) {
    DICE("dice"),
    BACKGROUND("background"),

    /**
     * Everything that moves without a group of its own: a page's content fading in, a list gliding to a spot, a picker
     * opening. Off is the app's reduced motion (`LocalReduceMotion`).
     */
    MENUS("menus"),

    /** The achievement banners: their slide and fade. */
    BANNERS("banners"),

    /** The Game Over fireworks. */
    CELEBRATIONS("celebrations"),

    /** The die's tumble. Off, it lands on its result straight away. */
    DIE_ROLL("die_roll"),

    /** A pawn's square-by-square move. Off, it jumps straight to where it lands. */
    PAWN_MOVE("pawn_move"),
    ;

    companion object {
        /** The decoration groups, one for each kind of art that moves on its own. */
        val decoration: List<MotionGroup> = listOf(DICE, BACKGROUND)

        /** The game's own motion beside the die and the pawns: what the "Animations" levels set together. */
        val gameplay: List<MotionGroup> = listOf(MENUS, BANNERS, CELEBRATIONS)
    }
}

/**
 * How the app moves: a [FrameRate] for each [MotionGroup] ([rates]), and how long an untouched screen waits before its
 * infinite animations freeze ([idleTimeout]). Each [AnimationLevel] is one of these; the player's own come from the
 * Graphics page's Advanced graphics settings.
 *
 * Each group runs at its own rate, and the window at the fastest of those that move ([maxFramesPerSecond]).
 */
data class MotionSettings(
    val rates: Map<MotionGroup, FrameRate>,
    val idleTimeout: IdleTimeout,
) {
    init {
        require(rates.keys == MotionGroup.entries.toSet()) { "a rate for every group" }
    }

    /** [group]'s rate. */
    fun rate(group: MotionGroup): FrameRate = rates.getValue(group)

    /** These settings with [group] at [rate]. */
    fun with(group: MotionGroup, rate: FrameRate): MotionSettings = copy(rates = rates + (group to rate))

    /** Whether [group] moves: its rate isn't off. */
    fun moves(group: MotionGroup): Boolean = rate(group).moves

    /** Whether menus and pages move - see [MotionGroup.MENUS]. Off is the app's reduced motion. */
    val menusMotion: Boolean get() = moves(MotionGroup.MENUS)

    /** Whether the die tumbles: off, it lands on its result straight away. */
    val rollMotion: Boolean get() = moves(MotionGroup.DIE_ROLL)

    /** Whether pawns step square by square: off, they jump straight to where they land. */
    val pawnMotion: Boolean get() = moves(MotionGroup.PAWN_MOVE)

    /**
     * The window's frame rate: that of the fastest group that moves, or null for whatever the screen does where one is
     * at [FrameRate.MAX]. With nothing moving, [STILL_FRAMES_PER_SECOND], for what's left (touch ripples, a switch's
     * thumb, a scroll's fling).
     */
    val maxFramesPerSecond: Int?
        get() {
            val moving = MotionGroup.entries.filter(::moves).map(::rate)
            return when {
                moving.isEmpty() -> STILL_FRAMES_PER_SECOND
                moving.any { it == FrameRate.MAX } -> null
                else -> moving.maxOf { checkNotNull(it.framesPerSecond) }
            }
        }

    /** Each group's rate's id, then the idle timeout's: what [fromIds] reads back. */
    fun ids(): List<String> = MotionGroup.entries.map { rate(it).id } + idleTimeout.id

    companion object {
        /** The window's frame rate with every group off. */
        const val STILL_FRAMES_PER_SECOND = 30

        /** Settings with every decoration group at [decoration], every [MotionGroup.gameplay] one at [gameplay], and the others as given. */
        fun of(
            decoration: FrameRate,
            gameplay: FrameRate,
            dieRoll: FrameRate,
            pawnMove: FrameRate,
            idleTimeout: IdleTimeout,
        ): MotionSettings = MotionSettings(
            MotionGroup.decoration.associateWith { decoration } + MotionGroup.gameplay.associateWith { gameplay } + mapOf(
                MotionGroup.DIE_ROLL to dieRoll,
                MotionGroup.PAWN_MOVE to pawnMove,
            ),
            idleTimeout,
        )

        /** The settings [ids] gave, or null for anything else. */
        fun fromIds(ids: List<String>): MotionSettings? {
            if (ids.size != MotionGroup.entries.size + 1) return null
            val rates = MotionGroup.entries.zip(ids).associate { (group, id) -> group to (storedIdOf<FrameRate>(id) ?: return null) }
            return MotionSettings(rates, storedIdOf<IdleTimeout>(ids.last()) ?: return null)
        }
    }
}
