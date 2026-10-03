package tech.kloos.kompound.inline

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.buttons.ButtonTestScheme
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KInlineEditTest {
    private val s = ButtonTestScheme
    private val viewButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun viewModeShowsTheValueAsAnEditButton() = runComposeUiTest {
        setContent { MaterialTheme(s) { KInlineEdit("Ada", {}) } }
        onNodeWithText("Ada", useUnmergedTree = true).assertExists()
        onNode(hasSetTextAction()).assertDoesNotExist()
        val d = onNode(viewButton).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("Edit Ada"), d)
    }

    @Test
    fun emptyValueShowsThePlaceholder() = runComposeUiTest {
        setContent { MaterialTheme(s) { KInlineEdit("", {}, placeholder = "Add a title") } }
        onNodeWithText("Add a title", useUnmergedTree = true).assertExists()
    }

    @Test
    fun clickSwitchesToEditingAndSaveReportsTheChange() = runComposeUiTest {
        var value by mutableStateOf("Ada")
        setContent { MaterialTheme(s) { KInlineEdit(value, { value = it }) } }
        onNode(viewButton).performClick()
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("Grace")
        onNodeWithContentDescription("Save").performClick()
        waitForIdle()
        assertEquals("Grace", value)
        onNode(hasSetTextAction()).assertDoesNotExist()
        onNodeWithText("Grace", useUnmergedTree = true).assertExists()
    }

    @Test
    fun cancelButtonDiscardsTheDraft() = runComposeUiTest {
        var calls = 0
        setContent { MaterialTheme(s) { KInlineEdit("Ada", { calls++ }) } }
        onNode(viewButton).performClick()
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("Changed")
        onNodeWithContentDescription("Cancel").performClick()
        waitForIdle()
        assertEquals(0, calls)
        onNodeWithText("Ada", useUnmergedTree = true).assertExists()
    }

    @Test
    fun imeDoneSavesAndEscapeCancels() = runComposeUiTest {
        var value by mutableStateOf("a")
        setContent { MaterialTheme(s) { KInlineEdit(value, { value = it }) } }
        onNode(viewButton).performClick()
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("b")
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertEquals("b", value)

        onNode(viewButton).performClick()
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("c")
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals("b", value)
        onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun unchangedValueIsNotReported() = runComposeUiTest {
        var calls = 0
        setContent { MaterialTheme(s) { KInlineEdit("same", { calls++ }) } }
        onNode(viewButton).performClick()
        waitForIdle()
        onNodeWithContentDescription("Save").performClick()
        waitForIdle()
        assertEquals(0, calls)
        onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun invalidDraftBlocksSavingUntilFixed() = runComposeUiTest {
        var value by mutableStateOf("ok")
        setContent {
            MaterialTheme(s) { KInlineEdit(value, { value = it }, validate = { if (it.isBlank()) "Must not be empty" else null }) }
        }
        onNode(viewButton).performClick()
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("")
        onNodeWithContentDescription("Save").performClick()
        waitForIdle()
        assertEquals("ok", value)
        onNodeWithText("Must not be empty", useUnmergedTree = true).assertExists()
        onNode(hasSetTextAction()).performTextReplacement("fixed")
        onNodeWithContentDescription("Save").performClick()
        waitForIdle()
        assertEquals("fixed", value)
    }

    @Test
    fun disabledCannotBeEdited() = runComposeUiTest {
        setContent { MaterialTheme(s) { KInlineEdit("Ada", {}, enabled = false) } }
        onNode(viewButton).assertIsNotEnabled().performClick()
        waitForIdle()
        onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun localisedDescriptionsAreUsed() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KInlineEdit("Ada", {}, editContentDescription = "Bearbeiten", saveContentDescription = "Speichern", cancelContentDescription = "Abbrechen")
            }
        }
        onNode(viewButton).performClick()
        waitForIdle()
        onNodeWithContentDescription("Speichern").assertExists()
        onNodeWithContentDescription("Abbrechen").assertExists()
    }

    @Test
    fun saveAndCancelButtonsAreCentredOnAOneLineField() = runComposeUiTest {
        setContent { MaterialTheme(s) { KInlineEdit("Ada", {}) } }
        onNode(viewButton).performClick()
        waitForIdle()
        val field = onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val save = onNodeWithContentDescription("Save").fetchSemanticsNode().boundsInRoot
        val cancel = onNodeWithContentDescription("Cancel").fetchSemanticsNode().boundsInRoot
        // the field's own box is a little smaller than its outline, so compare with the row: both buttons share one centre
        assertEquals(save.center.y, cancel.center.y, 0.5f)
        assertTrue(abs(save.center.y - field.center.y) <= 2f, "buttons centre ${save.center.y}, field centre ${field.center.y}")
    }
}
