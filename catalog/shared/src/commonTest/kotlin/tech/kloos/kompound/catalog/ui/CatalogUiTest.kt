package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.catalog.theme.ThemeMode
import tech.kloos.kompound.catalog.theme.ThemeSettings
import tech.kloos.kompound.catalog.theme.toKotlin
import kotlin.test.Test
import kotlin.test.assertTrue

/** Drives the real catalog (all generated demos) through the interactions a visitor uses. */
@OptIn(ExperimentalTestApi::class)
class CatalogUiTest {
    private val light = ThemeSettings(mode = ThemeMode.Light)

    private fun androidx.compose.ui.test.ComposeUiTest.wide() = setContent { Box(Modifier.fillMaxSize()) { KompoundCatalog(initialSettings = light) } }
    private fun androidx.compose.ui.test.ComposeUiTest.narrow() = setContent { Box(Modifier.width(400.dp)) { KompoundCatalog(initialSettings = light) } }

    @Test
    fun aWideWindowShowsTheSidebarAndSelectsTheFirstComponent() = runComposeUiTest {
        wide()
        waitForIdle()
        onNodeWithText("BUTTONS", useUnmergedTree = true).assertExists()
        onNodeWithText("Component catalog", useUnmergedTree = true).assertExists()
        // first component in sidebar order is selected, so its header and controls are visible
        onNodeWithText("Controls", useUnmergedTree = true).assertExists()
    }

    @Test
    fun searchNarrowsTheListAndNoResultsOffersToClear() = runComposeUiTest {
        wide()
        waitForIdle()
        onNode(hasSetTextAction() and hasImeAction(ImeAction.Search)).performTextInput("zzzz-no-such-component")
        waitForIdle()
        onNodeWithText("No components match", useUnmergedTree = true).assertExists()
        onNodeWithText("Clear search and filters", useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithText("No components match", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("BUTTONS", useUnmergedTree = true).assertExists()
    }

    @Test
    fun categoryMenuFiltersTheListAndShowsARemovableChip() = runComposeUiTest {
        wide()
        waitForIdle()
        onNodeWithContentDescription("Filter by category").performClick()
        waitForIdle()
        onNodeWithText("Overlays", substring = true).performClick()
        waitForIdle()
        onNodeWithText("OVERLAYS", useUnmergedTree = true).assertExists()
        onNodeWithText("BUTTONS", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithContentDescription("Remove Overlays", useUnmergedTree = true).assertExists()
    }

    @Test
    fun tagSheetFiltersByTag() = runComposeUiTest {
        wide()
        waitForIdle()
        onNodeWithContentDescription("Filter by tag").performClick()
        waitForIdle()
        onNodeWithText("Filter by tag").assertExists()
        onNodeWithText("tooltip", substring = true).performScrollTo().performClick()   // the chip is below the visible part of the list
        waitForIdle()
        onNodeWithText("Show 1 components").performClick()
        waitForIdle()
        // KTooltip is listed, selected (top bar and header) and nothing else is
        assertTrue(onAllNodesWithText("KTooltip", useUnmergedTree = true).fetchSemanticsNodes().size >= 3)
        onNodeWithText("KButton", useUnmergedTree = true).assertDoesNotExist()   // the selection followed the filter
    }

    @Test
    fun editingAControlChangesThePreview() = runComposeUiTest {
        wide()
        waitForIdle()
        // the first component (KButton) is selected on a wide window
        // the Label control is a text field whose value is "Button"; changing it re-labels the previewed button
        onNode(hasSetTextAction() and androidx.compose.ui.test.hasText("Button")).performTextReplacement("Launch")
        waitForIdle()
        // "Launch" is now in the text field itself and on the previewed button
        assertTrue(onAllNodesWithText("Launch", useUnmergedTree = true).fetchSemanticsNodes().size >= 2)
    }

    @Test
    fun themeDesignerRecoloursAndGeneratesCode() = runComposeUiTest {
        wide()
        waitForIdle()
        onNodeWithContentDescription("Theme designer").performClick()
        waitForIdle()
        onNodeWithContentDescription("Teal").performClick()
        waitForIdle()
        onNodeWithText("Get code").performClick()
        waitForIdle()
        val teal = ThemeSettings.Presets.first { it.name == "Teal" }
        val expected = ThemeSettings(hue = teal.hue, saturation = teal.saturation, mode = ThemeMode.Light).colorScheme(false).primary.toKotlin()
        onNodeWithText(expected, substring = true).assertExists()
    }

    @Test
    fun darkModeButtonFlipsItsOwnDescription() = runComposeUiTest {
        wide()
        waitForIdle()
        onNodeWithContentDescription("Switch to dark mode").performClick()
        waitForIdle()
        onNodeWithContentDescription("Switch to light mode").assertExists()
    }

    @Test
    fun aNarrowWindowShowsTheListThenDetailWithABackButton() = runComposeUiTest {
        narrow()
        waitForIdle()
        onNodeWithText("Search components", useUnmergedTree = true).assertExists()
        onNodeWithText("KButton", useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithContentDescription("Back to the list").assertExists()
        onNodeWithText("Controls", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Back to the list").performClick()
        waitForIdle()
        onNodeWithText("Search components", useUnmergedTree = true).assertExists()
    }
}
