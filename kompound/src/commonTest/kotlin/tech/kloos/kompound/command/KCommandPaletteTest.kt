package tech.kloos.kompound.command

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KCommandPaletteTest {
    private val log = mutableListOf<String>()
    private fun cmd(id: String, title: String, section: String? = null, keywords: List<String> = emptyList(), enabled: Boolean = true, subtitle: String? = null) =
        KCommand(id, title, { log += id }, section = section, keywords = keywords, enabled = enabled, subtitle = subtitle)

    private val commands = listOf(
        cmd("open", "Open file", "File"),
        cmd("save", "Save file", "File"),
        cmd("settings", "Go to Settings", "Navigate", keywords = listOf("preferences")),
        cmd("theme", "Toggle dark theme", "View", enabled = false),
    )

    @Test
    fun rankingPrefersTitleMatchesAndUsesKeywords() {
        assertEquals(listOf("open", "save", "settings", "theme"), rankCommands(commands, "").map { it.first.id })
        assertEquals(listOf("settings"), rankCommands(commands, "pref").map { it.first.id })
        assertEquals("settings", rankCommands(commands, "gts").first().first.id)
        assertEquals(emptyList(), rankCommands(commands, "qqq"))
    }

    @Test
    fun closedPaletteShowsNothingAndOpenOneListsSectionsAndCommands() = runComposeUiTest {
        var open by mutableStateOf(false)
        setContent { MaterialTheme(lightColorScheme()) { KCommandPalette(open, { open = false }, commands) } }
        onAllNodesWithText("Open file").assertCountEquals(0)
        open = true
        waitForIdle()
        onNodeWithText("Open file").assertIsDisplayed()
        onNodeWithText("File").assertIsDisplayed()
        onNodeWithText("Navigate").assertIsDisplayed()
    }

    @Test
    fun typingNarrowsAndClickRunsAfterClosing() = runComposeUiTest {
        log.clear()
        var open by mutableStateOf(true)
        setContent { MaterialTheme(lightColorScheme()) { KCommandPalette(open, { open = false }, commands) } }
        onNode(hasSetTextAction()).performTextInput("sav")
        waitForIdle()
        onAllNodesWithText("Open file").assertCountEquals(0)
        onNodeWithText("Save file", substring = true).performClick()
        waitForIdle()
        assertEquals(listOf("save"), log)
        assertTrue(!open, "the palette asked to close")
    }

    @Test
    fun nothingMatchingShowsTheMessage() = runComposeUiTest {
        setContent { MaterialTheme(lightColorScheme()) { KCommandPalette(true, {}, commands) } }
        onNode(hasSetTextAction()).performTextInput("qqq")
        waitForIdle()
        onNodeWithText("No matching commands").assertIsDisplayed()
    }

    @Test
    fun aDisabledCommandDoesNotRun() = runComposeUiTest {
        log.clear()
        setContent { MaterialTheme(lightColorScheme()) { KCommandPalette(true, {}, commands) } }
        onNodeWithText("Toggle dark theme").performClick()
        waitForIdle()
        assertEquals(emptyList(), log)
    }
}
