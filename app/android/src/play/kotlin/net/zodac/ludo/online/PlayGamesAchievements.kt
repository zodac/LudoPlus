package net.zodac.ludo.online

import net.zodac.ludo.model.Achievement
import net.zodac.ludo.model.AchievementVisibility
import net.zodac.ludo.model.ProgressStyle

/** How an [Achievement] is set up on Play Games. */
internal sealed interface PlayAchievementType {

    /** Unlocked in one go. Includes [ProgressStyle.STREAK] ones, as Play's steps can't go back. */
    data object Standard : PlayAchievementType

    /** [steps] to unlock, each worth [perStep] of the achievement's own target. */
    data class Incremental(val steps: Int, val perStep: Int) : PlayAchievementType
}

/** The most steps Play allows an incremental achievement. */
internal const val PLAY_MAX_STEPS = 10_000

/**
 * A climbing total ([ProgressStyle.CUMULATIVE]) is incremental, at its own target; one over [PLAY_MAX_STEPS] is scaled
 * down to fit, each Play step a share of the target. Everything else is standard. Play never lets a published
 * achievement change type or steps, so once published, one whose rule here changes is kept as it was published.
 */
internal val Achievement.playType: PlayAchievementType
    get() = when {
        progressStyle == ProgressStyle.CUMULATIVE -> {
            val perStep = (target + PLAY_MAX_STEPS - 1) / PLAY_MAX_STEPS
            PlayAchievementType.Incremental(steps = (target + perStep - 1) / perStep, perStep = perStep)
        }
        else -> PlayAchievementType.Standard
    }

/** Whether Play starts this achievement hidden: details unseen until earned, as [AchievementVisibility.HIDDEN] is here. */
internal val Achievement.playHidden: Boolean
    get() = visibility == AchievementVisibility.HIDDEN
