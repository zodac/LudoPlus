package net.zodac.ludo.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** Records which part of a merge was asked for, in order. */
private class PartsCloudSave : CloudSaveStore {
    val parts = mutableListOf<String>()
    override suspend fun snapshot() = CloudSave()
    override suspend fun mergeSettings(save: CloudSave) {
        parts += "settings"
    }
    override suspend fun mergeGame(save: CloudSave) {
        parts += "game"
    }
    override suspend fun mergeAchievements(save: CloudSave) {
        parts += "achievements"
    }
    override suspend fun mergeRecords(save: CloudSave) {
        parts += "records"
    }
    override suspend fun wipe() = Unit
}

/**
 * Every merge (a progress file's import, and from Play Games a cloud save's sync) takes [MergeStep]'s parts in its
 * order and shows them by its names, so the flows can't drift apart.
 */
class MergeStepTest {

    @Test
    fun `a merge takes each part in order - telling each as it starts`() = runTest {
        val store = PartsCloudSave()
        val told = mutableListOf<MergeStep>()
        store.merge(CloudSave()) { told += it }
        assertEquals(listOf("settings", "game", "achievements", "records"), store.parts)
        assertEquals(MergeStep.entries.toList(), told)
    }

    @Test
    fun `an import's merge steps are every merge's - in its order - after opening the file`() {
        assertEquals(MergeStep.entries.toList(), ImportStep.entries.mapNotNull { it.merge })
        assertEquals(ImportStep.OPENING, ImportStep.entries.first())
    }
}
