package net.zodac.ludoplus.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import net.zodac.ludoplus.data.achievements.AchievementStore
import net.zodac.ludoplus.data.achievements.AchievementsState

/**
 * The four saved Styles picks, as ids, with the achievements and the purchase that decide which of them are unlocked -
 * everything the game screen and the Styles screen need to draw a player's picks. Ids rather than the styles
 * themselves, as in [SettingsRepository]: resolving them is the UI's job.
 */
data class SavedStyles(
    val boardStyleId: String,
    val pawnStyleId: String,
    val diceStyleId: String,
    val tableBackgroundId: String,
    val achievements: AchievementsState,
    /** Whether the "Unlock all styles" purchase unlocks every style (Phase 11; false until then). */
    val ownsAllStyles: Boolean = false,
)

/**
 * [SavedStyles] as it changes. The app holds one of these open for its whole life
 * ([net.zodac.ludoplus.app.AppContainer.savedStyles]), so a screen opened later has the picks the
 * moment it's composed rather than a frame or two after.
 */
fun savedStylesFlow(settings: SettingsRepository, achievements: AchievementStore?, ownsAllStyles: Flow<Boolean>): Flow<SavedStyles> =
    combine(
        settings.boardStyleId,
        settings.pawnStyleId,
        settings.diceStyleId,
        settings.tableBackgroundId,
        achievements?.state ?: flowOf(AchievementsState()),
    ) { boardId, pawnId, diceId, backgroundId, achievementsState ->
        SavedStyles(boardId, pawnId, diceId, backgroundId, achievementsState)
    }.combine(ownsAllStyles) { styles, owns -> styles.copy(ownsAllStyles = owns) }
