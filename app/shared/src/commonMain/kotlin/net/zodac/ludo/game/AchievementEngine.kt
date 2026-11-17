package net.zodac.ludo.game

import net.zodac.ludo.data.achievements.AchievementsState
import net.zodac.ludo.model.Achievement
import net.zodac.ludo.model.AchievementCounter
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.model.ProgressStyle

/** One "getting closer" banner: [achievement] moved from [previous] to [current] out of its target. */
data class AchievementProgress(val achievement: Achievement, val previous: Int, val current: Int)

/** What one evaluation changed: empty on every count when nothing happened. */
data class AchievementUpdate(
    val newlyUnlocked: List<Achievement> = emptyList(),
    val counters: Map<AchievementCounter, Int> = emptyMap(),
    val progressed: List<AchievementProgress> = emptyList(),
    val unlockedAtMillis: Long = 0L,
) {
    val isEmpty: Boolean get() = newlyUnlocked.isEmpty() && counters.isEmpty() && progressed.isEmpty()

    fun unlockedAt(): Map<Achievement, Long> = newlyUnlocked.associateWith { unlockedAtMillis }
}

/**
 * What a moment of play earned player 1: [achievements] done, [counts] to add to their counters, and [streak] true for
 * a win (the win streak goes up, the losing one is broken), false for another player's (the other way round), null when
 * no one won.
 */
data class GameFeats(
    val achievements: Set<Achievement> = emptySet(),
    val counts: Map<AchievementCounter, Int> = emptyMap(),
    val streak: Boolean? = null,
) {
    val isEmpty: Boolean get() = achievements.isEmpty() && counts.isEmpty() && streak == null
}

/**
 * Decides what's earned. Pure: no persistence and no clock of its own. `GameViewModel` reads the stored state, calls
 * this with what [AchievementTracker] saw in play, and writes the result back.
 *
 * **Player 1 only.** `players[0]`, the person at this device, is the only seat whose play earns achievements; every
 * other seat, person or CPU, is an opponent. The tutorial earns nothing.
 */
object AchievementEngine {

    /** [Achievement.BIG_FAN]'s opponent's name, matched exactly. */
    private const val CREATOR_NAME = "zodac"

    /** [Achievement.WHOS_LUDO]'s player's name, in any case. */
    private const val LUDO_NAME = "Ludo"

    /** [Achievement.I_AM_NOT_A_ROBOT]'s name for player 1, in any case. */
    private const val CPU_NAME = "CPU"

    /** The hours a new game starts in for [Achievement.MIDNIGHT_RUN]: midnight up to 6am. */
    private val MIDNIGHT_HOURS = 0 until 6

    /** How finely a [ProgressStyle.CUMULATIVE] achievement announces itself by default: quarter by quarter. */
    private const val PROGRESS_MILESTONES = 4

    /**
     * What a new game for [players] earns as it starts, at [localHour] on the phone's clock. [previousGame] is
     * the finished game it replaced on the same screen ([Achievement.ONE_MORE_TIME] when player 1 lost it), or null.
     */
    fun atGameStart(players: List<PlayerConfig>, previousGame: GameState?, localHour: Int): GameFeats {
        if (players.firstOrNull()?.type != PlayerType.HUMAN) return GameFeats()
        val earned = buildSet {
            add(Achievement.THE_JOURNEY_BEGINS)
            if (previousGame != null && previousGame.isFinished && previousGame.winner != 0) add(Achievement.ONE_MORE_TIME)
            if (players.drop(1).any { it.type == PlayerType.HUMAN && it.name == CREATOR_NAME }) add(Achievement.BIG_FAN)
            if (localHour in MIDNIGHT_HOURS) add(Achievement.MIDNIGHT_RUN)
            if (players.any { it.name.equals(LUDO_NAME, ignoreCase = true) }) add(Achievement.WHOS_LUDO)
            if (players.first().name.equals(CPU_NAME, ignoreCase = true)) add(Achievement.I_AM_NOT_A_ROBOT)
            if (players.withIndex().all { (seat, player) -> player.colour != PlayerColour.defaultFor(seat) }) add(Achievement.MUSICAL_CHAIRS)
        }
        return GameFeats(earned)
    }

    /** What finishing the tutorial earns. */
    fun atTutorialEnd(): GameFeats = GameFeats(setOf(Achievement.BACK_TO_SCHOOL))

    /** What continuing a saved game earns. */
    fun atGameResume(): GameFeats = GameFeats(setOf(Achievement.LETS_FINISH_THIS))

    /** Unlocks whichever of [achievements] aren't yet, at [now]. */
    fun unlockNow(achievements: Set<Achievement>, before: AchievementsState, now: Long): AchievementUpdate =
        evaluate(GameFeats(achievements), before, now)

    /**
     * [feats] against what was stored [before]: the counters moved, the achievements newly earned (by feat or by
     * counter, and [Achievement.COMPLETIONIST] with the last of the rest), and the progress worth a banner.
     */
    fun evaluate(feats: GameFeats, before: AchievementsState, now: Long): AchievementUpdate {
        if (feats.isEmpty) return AchievementUpdate()
        val counters = buildMap {
            for ((counter, count) in feats.counts) if (count != 0) put(counter, before.counter(counter) + count)
            feats.streak?.let { won ->
                val (up, broken) = if (won) AchievementCounter.WIN_STREAK to AchievementCounter.LOSS_STREAK else AchievementCounter.LOSS_STREAK to AchievementCounter.WIN_STREAK
                put(up, before.counter(up) + 1)
                if (before.counter(broken) != 0) put(broken, 0)
            }
        }
        val earned = feats.achievements + Achievement.entries.filter { achievement ->
            val counter = achievement.counter
            counter != null && counter in counters && counters.getValue(counter) >= achievement.target
        }
        val newlyUnlocked = earned.filterNot(before::isUnlocked).sortedBy { it.ordinal }.toMutableList()
        val unlockedAfter = before.unlockedAt.keys + newlyUnlocked
        if (!before.isUnlocked(Achievement.COMPLETIONIST) && Achievement.COMPLETIONIST !in newlyUnlocked &&
            unlockedAfter.containsAll(Achievement.COMPLETION_REQUIREMENTS)
        ) {
            newlyUnlocked += Achievement.COMPLETIONIST
        }
        val progressed = Achievement.entries.mapNotNull { progressEvent(it, counters, before, newlyUnlocked) }
        return AchievementUpdate(newlyUnlocked, counters, progressed, now)
    }

    /** How far along [achievement] is with [counters], capped at its target. */
    fun progressOf(achievement: Achievement, counters: Map<AchievementCounter, Int>): Int =
        achievement.counter?.let { (counters[it] ?: 0).coerceAtMost(achievement.target) } ?: 0

    /**
     * The banner [achievement]'s progress is worth, if any: none once it's earned, or unlocking now (its own banner
     * says it all). A streak announces every step up; a running total each quarter (or [Achievement.progressStepSize]).
     */
    private fun progressEvent(
        achievement: Achievement,
        counters: Map<AchievementCounter, Int>,
        before: AchievementsState,
        newlyUnlocked: List<Achievement>,
    ): AchievementProgress? {
        val counter = achievement.counter ?: return null
        if (!achievement.hasProgressBar || counter !in counters) return null
        if (before.isUnlocked(achievement) || achievement in newlyUnlocked) return null
        val previous = before.progress(achievement)
        val current = progressOf(achievement, counters)
        val worthShowing = when (achievement.progressStyle) {
            ProgressStyle.STREAK -> current > previous
            ProgressStyle.CUMULATIVE -> milestone(previous, achievement) != milestone(current, achievement)
            ProgressStyle.NONE -> false
        }
        return if (worthShowing) AchievementProgress(achievement, previous, current) else null
    }

    private fun milestone(value: Int, achievement: Achievement): Int =
        achievement.progressStepSize?.let { value / it } ?: (value * PROGRESS_MILESTONES / achievement.target)
}
