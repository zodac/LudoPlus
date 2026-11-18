package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.app.LocalAppContainer
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.achievements.InMemoryAchievementStore
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.online.CloudSave
import net.zodac.ludoplus.online.CloudSaveStore
import net.zodac.ludoplus.online.ImportStep
import net.zodac.ludoplus.online.ProgressFileCodec
import net.zodac.ludoplus.online.ProgressTransfer
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.PlatformServices
import net.zodac.ludoplus.platform.ProgressFileResult
import net.zodac.ludoplus.platform.ProgressFiles
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.settings.ProgressFilesCard
import net.zodac.ludoplus.ui.settings.ProgressFilesStatus
import net.zodac.ludoplus.ui.settings.SettingsScreen
import net.zodac.ludoplus.ui.settings.SettingsViewModel
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** A device with nothing on it, whose merges change nothing: the screen's part is what's tested here. */
private object EmptyCloudSave : CloudSaveStore {
    override suspend fun snapshot() = CloudSave()
    override suspend fun mergeSettings(save: CloudSave) = Unit
    override suspend fun mergeGame(save: CloudSave) = Unit
    override suspend fun mergeAchievements(save: CloudSave) = Unit
    override suspend fun mergeRecords(save: CloudSave) = Unit
    override suspend fun wipe() = Unit
}

/** A picker that saves wherever asked, and opens [opened]. */
private class PickerFiles(var opened: ProgressFileResult = ProgressFileResult.Cancelled) : ProgressFiles {
    override val key = ByteArray(32) { 4 }
    val saved = mutableListOf<String>()
    var opens = 0
    override suspend fun save(fileName: String, bytes: ByteArray): ProgressFileResult = ProgressFileResult.Done().also { saved += fileName }
    override suspend fun open(): ProgressFileResult = opened.also { opens++ }
}

/** A platform with [files] for progress files (or none), keeping each toast it was asked to show: there should be none. */
private class FilesPlatform(override val progressFiles: ProgressFiles?) : PlatformServices by SilentPlatformServices {
    val toasts = mutableListOf<String>()
    override fun showTransientMessage(message: String) {
        toasts += message
    }
}

/**
 * Settings in a build with progress files (the github APK): an Export and an Import row where the online card would be,
 * Import asking first, and a line under them saying how each is going and went - no toast. A build without them shows
 * none of it.
 */
@Config(qualifiers = "w360dp-h800dp")
class ProgressFilesSettingsTest : UiTest() {

    private fun show(platform: PlatformServices) {
        val viewModel = SettingsViewModel(
            SettingsRepository(InMemoryPreferences()),
            InMemoryAchievementStore(),
            progressTransfer = ProgressTransfer(EmptyCloudSave, InMemoryAchievementStore()),
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides platform,
            ) {
                LudoPlusTheme { SettingsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
    }

    private val statusLine = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    /**
     * What the card's status line says once the export or import has ended: TalkBack's announcement, as it's a polite
     * live region. The view model's work comes back to the main looper, which Robolectric holds paused until stepped.
     */
    private fun outcome(platform: FilesPlatform): String {
        var spoken: String? = null
        compose.waitUntil(timeoutMillis = 5_000) {
            ShadowLooper.idleMainLooper()
            spoken = compose.onAllNodes(statusLine).fetchSemanticsNodes().singleOrNull()
                ?.config?.get(SemanticsProperties.ContentDescription)?.single()?.takeIf { it != "Importing progress" }
            spoken != null
        }
        assertEquals(emptyList<String>(), platform.toasts)
        // What it says is what it shows.
        compose.onNodeWithText(spoken!!, useUnmergedTree = true).assertExists()
        return spoken!!
    }

    private fun spoken(text: String): List<String> =
        compose.onNode(hasText(text) and hasClickAction()).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }

    @Test
    fun `each row is one compact button - its label alone - naming its action`() = uiTest {
        show(FilesPlatform(PickerFiles()))
        // TalkBack: "Export progress, button, double-tap to export".
        assertEquals(listOf("Export progress"), spoken("Export progress"))
        assertEquals(listOf("Import progress"), spoken("Import progress"))
        // As tall as a switch row: the touch target's 48dp.
        compose.onNode(hasText("Export progress") and hasClickAction()).assertHeightIsEqualTo(48.dp)
        for ((row, action) in listOf("Export progress" to "Export", "Import progress" to "Import")) {
            val node = compose.onNode(hasText(row) and hasClickAction())
            node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            node.assert(SemanticsMatcher("double-tap to ${action.lowercase()}") { it.config[SemanticsActions.OnClick].label == action })
        }
    }

    @Test
    fun `the card comes first - where the online card would be`() = uiTest {
        show(FilesPlatform(PickerFiles()))
        val card = compose.onNodeWithText("Export progress").fetchSemanticsNode().boundsInRoot
        val sound = compose.onNodeWithText("Sound effects").fetchSemanticsNode().boundsInRoot
        assertTrue(card.bottom <= sound.top, "$card is below $sound")
    }

    @Test
    fun `Export saves a file named for today - and says so`() = uiTest {
        val files = PickerFiles()
        val platform = FilesPlatform(files)
        show(platform)
        compose.onNode(hasText("Export progress") and hasClickAction()).performClick()
        assertEquals("Progress exported", outcome(platform))
        assertTrue(Regex("""LudoPlus-\d{4}-\d{2}-\d{2}\.ludo""").matches(files.saved.single()), files.saved.single())
    }

    @Test
    fun `Import asks first - and Cancel opens no picker`() = uiTest {
        val files = PickerFiles()
        show(FilesPlatform(files))
        compose.onNode(hasText("Import progress") and hasClickAction()).performClick()
        compose.onNodeWithText("Import progress?").assertExists()
        val message = compose.onNodeWithText("Choose a file exported", substring = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
        assertEquals(
            "Choose a file exported from LudoPlus. Its achievements and game records are added to this device's, and where both have a setting " +
                "or a saved game, the newer one is kept. This can't be undone.",
            message,
        )
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Import progress?").assertDoesNotExist()
        compose.waitForIdle()
        assertEquals(0, files.opens)
    }

    @Test
    fun `Choose File opens the picker - and each outcome has its line`() = uiTest {
        val files = PickerFiles()
        val platform = FilesPlatform(files)
        show(platform)
        fun import(opened: ProgressFileResult): String {
            files.opened = opened
            compose.onNode(hasText("Import progress") and hasClickAction()).performClick()
            compose.onNodeWithText("Choose File").performClick()
            return outcome(platform)
        }
        assertEquals("Progress imported", import(ProgressFileResult.Done(ProgressFileCodec.encode(CloudSave(), files.key))))
        assertEquals("That isn't a LudoPlus progress file, or it has been changed", import(ProgressFileResult.Done(ByteArray(64))))
        assertEquals(
            "That file is from a newer version of LudoPlus. Update the app, then import it again.",
            import(ProgressFileResult.Done(ProgressFileCodec.encode(CloudSave(version = 99), files.key))),
        )
        assertEquals("Couldn't read the file", import(ProgressFileResult.Failed))
    }

    @Test
    fun `a quick import's bar still runs for a second - step by step - before it says so`() = uiTest {
        val files = PickerFiles(ProgressFileResult.Done(ProgressFileCodec.encode(CloudSave(), ByteArray(32) { 4 })))
        show(FilesPlatform(files))
        compose.onNode(hasText("Import progress") and hasClickAction()).performClick()
        compose.onNodeWithText("Choose File").performClick()
        compose.mainClock.autoAdvance = false
        // The import itself is over in a moment.
        var frames = 0
        while (compose.onAllNodes(statusLine).fetchSemanticsNodes().isEmpty()) {
            check(frames++ < 300) { "The import never started" }
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeByFrame()
        }
        val seen = mutableListOf<String>()
        repeat(12) {
            compose.mainClock.advanceTimeBy(100)
            ShadowLooper.idleMainLooper()
            seen += compose.onNode(statusLine).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        }
        // About a second of "Importing progress" (its steps underneath), then the outcome, which stays.
        assertTrue(seen.take(8).all { it == "Importing progress" }, seen.toString())
        assertEquals("Progress imported", seen.last())
    }

    @Test
    fun `while an import runs - its step and count show over a bar - and TalkBack hears Importing progress`() = uiTest {
        compose.setContent {
            LudoPlusTheme { ProgressFilesCard(onExport = {}, onImport = {}, status = ProgressFilesStatus.Importing(ImportStep.RECORDS)) }
        }
        compose.onNodeWithText("Merging game records (5/5)", useUnmergedTree = true).assertExists()
        val line = compose.onNode(statusLine).fetchSemanticsNode().config
        assertEquals(listOf("Importing progress"), line[SemanticsProperties.ContentDescription])
        // The step's text is under the cleared line, so TalkBack doesn't read each one out as it flashes by.
        compose.onNodeWithText("Merging game records (5/5)").assertDoesNotExist()
    }

    @Test
    fun `nothing shows under the rows before an export or import`() = uiTest {
        compose.setContent { LudoPlusTheme { ProgressFilesCard(onExport = {}, onImport = {}) } }
        assertEquals(0, compose.onAllNodes(statusLine).fetchSemanticsNodes().size)
    }

    @Test
    fun `a build without progress files shows none of it`() = uiTest {
        show(FilesPlatform(null))
        compose.onNodeWithText("Sound effects").assertExists()
        compose.onNodeWithText("Export progress").assertDoesNotExist()
        compose.onNodeWithText("Import progress").assertDoesNotExist()
    }
}
