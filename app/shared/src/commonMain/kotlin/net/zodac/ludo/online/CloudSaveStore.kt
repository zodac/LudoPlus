package net.zodac.ludo.online

import net.zodac.ludo.data.achievements.AchievementStore
import net.zodac.ludo.data.achievements.AchievementsRepository
import net.zodac.ludo.data.game.InProgressGameRepository
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.AchievementEngine

/**
 * This device's side of a [CloudSave]: read it whole, or merge a save into it store by store. Each merge follows
 * [CloudSaveMerge]'s rules, so nothing here is lost.
 */
interface CloudSaveStore {

    /** Everything on this device that a [CloudSave] holds. */
    suspend fun snapshot(): CloudSave

    suspend fun mergeSettings(save: CloudSave)

    suspend fun mergeGame(save: CloudSave)

    suspend fun mergeAchievements(save: CloudSave)

    suspend fun mergeRecords(save: CloudSave)
}

/** [CloudSaveStore] over the app's own repositories. */
class LocalCloudSave(
    private val achievements: AchievementsRepository,
    private val records: GameRecordRepository,
    private val settings: SettingsRepository,
    private val inProgressGame: InProgressGameRepository,
) : CloudSaveStore {

    override suspend fun snapshot(): CloudSave = CloudSave(
        achievements = achievements.cloudAchievements(),
        records = records.cloudRecords(),
        settings = settings.cloudSettings(),
        inProgressGame = inProgressGame.cloudGame(),
    )

    override suspend fun mergeSettings(save: CloudSave) = settings.mergeCloud(save.settings)

    override suspend fun mergeGame(save: CloudSave) = inProgressGame.mergeCloud(save.inProgressGame)

    // Under the store's lock, so no game reads the counters before this and writes them after.
    override suspend fun mergeAchievements(save: CloudSave) = achievements.locked { achievements.mergeCloud(save.achievements) }

    override suspend fun mergeRecords(save: CloudSave) = records.mergeCloud(save.records)
}

/**
 * The parts of a merge, in the order every merge takes them: a progress file's ([ProgressTransfer], [ImportStep.merge]),
 * and from Play Games a cloud save's. Each flow's own steps name these, so both show the same.
 */
enum class MergeStep {
    SETTINGS,
    GAME,
    ACHIEVEMENTS,
    RECORDS,
}

/** All of [save] merged in, in [MergeStep]'s order, telling [onStep] each part as it starts. */
suspend fun CloudSaveStore.merge(save: CloudSave, onStep: (MergeStep) -> Unit = {}) {
    for (step in MergeStep.entries) {
        onStep(step)
        mergePart(step, save)
    }
}

/** The one [step] of [save] merged in. */
suspend fun CloudSaveStore.mergePart(step: MergeStep, save: CloudSave) = when (step) {
    MergeStep.SETTINGS -> mergeSettings(save)
    MergeStep.GAME -> mergeGame(save)
    MergeStep.ACHIEVEMENTS -> mergeAchievements(save)
    MergeStep.RECORDS -> mergeRecords(save)
}

/**
 * Records, quietly, every achievement the merged counters have reached ([AchievementEngine.evaluateProgress]): another
 * device's counts can carry one past a target neither reached alone. After every merge.
 */
suspend fun AchievementStore.unlockReachedTargets(now: Long) = locked {
    val update = AchievementEngine.evaluateProgress(current(), now)
    record(update.unlockedAt(), emptyMap())
}
