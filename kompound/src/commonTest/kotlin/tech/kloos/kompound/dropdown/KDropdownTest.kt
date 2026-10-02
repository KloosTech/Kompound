package tech.kloos.kompound.dropdown

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.containsColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KDropdownTest {
    private val s = ButtonTestScheme
    private val options = listOf("Alpha", "Beta", "Gamma")
    private val trigger = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)

    @Test
    fun showsPlaceholderThenTheSelectedLabel() = runComposeUiTest {
        var selected by mutableStateOf<String?>(null)
        setContent { MaterialTheme(s) { KDropdown(options, selected, { selected = it }, placeholder = "Pick one") } }
        onNodeWithText("Pick one", useUnmergedTree = true).assertExists()
        selected = "Beta"
        waitForIdle()
        onNodeWithText("Pick one", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Beta", useUnmergedTree = true).assertExists()
    }

    @Test
    fun triggerIsADropdownListThatOpensTheMenu() = runComposeUiTest {
        setContent { MaterialTheme(s) { KDropdown(options, null, {}, placeholder = "Pick") } }
        onNodeWithText("Alpha").assertDoesNotExist()
        onNode(trigger).performClick()
        waitForIdle()
        options.forEach { onNodeWithText(it).assertExists() }
    }

    @Test
    fun choosingAnOptionReportsItAndClosesTheMenu() = runComposeUiTest {
        var chosen: String? = null
        setContent { MaterialTheme(s) { KDropdown(options, "Alpha", { chosen = it }) } }
        onNode(trigger).performClick()
        waitForIdle()
        onNodeWithText("Gamma").performClick()
        waitForIdle()
        assertEquals("Gamma", chosen)
        onNodeWithText("Beta").assertDoesNotExist()
    }

    @Test
    fun currentChoiceIsMarkedSelectedInTheMenu() = runComposeUiTest {
        setContent { MaterialTheme(s) { KDropdown(options, "Beta", {}) } }
        onNode(trigger).performClick()
        waitForIdle()
        // the trigger also shows "Beta", so find the menu items by their radio role
        val items = onAllNodesWithRole(Role.RadioButton)
        assertEquals(3, items.size)
        assertEquals(listOf(false, true, false), items.map { it.config.getOrNull(SemanticsProperties.Selected) })
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithRole(role: Role) =
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, role)).fetchSemanticsNodes()

    @Test
    fun disabledDropdownDoesNotOpen() = runComposeUiTest {
        setContent { MaterialTheme(s) { KDropdown(options, null, {}, enabled = false, placeholder = "Pick") } }
        onNode(trigger).performClick()
        waitForIdle()
        onNodeWithText("Alpha").assertDoesNotExist()
    }

    @Test
    fun labelSupportingTextAndErrorColours() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KDropdown(options, null, {}, Modifier.testTag("bad"), label = "Size", supportingText = "Required", isError = true, placeholder = "Pick")
                    KDropdown(options, null, {}, Modifier.testTag("good"), placeholder = "Pick")
                }
            }
        }
        onNodeWithText("Size", useUnmergedTree = true).assertExists()
        onNodeWithText("Required", useUnmergedTree = true).assertExists()
        assertTrue(onNodeWithTag("bad").captureToImage().containsColor(s.error), "error colours")
        assertTrue(!onNodeWithTag("good").captureToImage().containsColor(s.error), "no error colours when valid")
        assertEquals(listOf("Size"), onAllNodesWithRole(Role.DropdownList)[0].config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun multiSelectKeepsTheMenuOpenAndToggles() = runComposeUiTest {
        var selected by mutableStateOf(setOf("Alpha"))
        setContent { MaterialTheme(s) { KMultiDropdown(options, selected, { selected = it }, placeholder = "None") } }
        onNode(trigger).performClick()
        waitForIdle()
        val items = onAllNodesWithRole(Role.Checkbox)
        assertEquals(3, items.size)
        onNodeWithText("Gamma").performClick()
        waitForIdle()
        assertEquals(setOf("Alpha", "Gamma"), selected)
        onNodeWithText("Beta").assertExists()                       // still open
        onNodeWithText("Alpha", useUnmergedTree = true).performClick()   // untoggle (menu item or summary, both clickable region)
    }

    @Test
    fun multiSelectSummaryJoinsTheChoicesInOptionOrder() = runComposeUiTest {
        setContent { MaterialTheme(s) { KMultiDropdown(options, setOf("Gamma", "Alpha"), {}, placeholder = "None") } }
        onNodeWithText("Alpha, Gamma", useUnmergedTree = true).assertExists()
    }
}
