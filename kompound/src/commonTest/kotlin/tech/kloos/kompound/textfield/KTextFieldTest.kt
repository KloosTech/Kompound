package tech.kloos.kompound.textfield

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.icon.KIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KTextFieldTest {
    private val s = ButtonTestScheme

    @Test
    fun typingReportsTheNewText() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent { MaterialTheme(s) { KTextField(text, { text = it }) } }
        onNode(hasSetTextAction()).performTextInput("hello")
        assertEquals("hello", text)
    }

    @Test
    fun placeholderShowsOnlyWhileEmpty() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent { MaterialTheme(s) { KTextField(text, { text = it }, placeholder = "Your name") } }
        onNodeWithText("Your name", useUnmergedTree = true).assertExists()
        onNode(hasSetTextAction()).performTextInput("A")
        onNodeWithText("Your name", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun labelSupportingTextAndAccessibleName() = runComposeUiTest {
        setContent { MaterialTheme(s) { KTextField("", {}, label = "Email", supportingText = "We never share it") } }
        onNodeWithText("Email", useUnmergedTree = true).assertExists()
        onNodeWithText("We never share it", useUnmergedTree = true).assertExists()
        val description = onNode(hasSetTextAction()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("Email"), description)
    }

    @Test
    fun maxLengthCutsInputAndShowsACounter() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent { MaterialTheme(s) { KTextField(text, { text = it }, maxLength = 5) } }
        onNodeWithText("0/5", useUnmergedTree = true).assertExists()
        onNode(hasSetTextAction()).performTextInput("abcdefgh")
        assertEquals("abcde", text)
        onNodeWithText("5/5", useUnmergedTree = true).assertExists()
    }

    @Test
    fun errorIsAnnouncedAndColoured() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KTextField("", {}, Modifier.testTag("bad"), supportingText = "Required", isError = true)
                    KTextField("", {}, Modifier.testTag("good"), supportingText = "Optional")
                }
            }
        }
        val node = onNode(hasSetTextAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        assertEquals("Required", node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Error))
        assertTrue(onNodeWithTag("bad").captureToImage().containsColor(s.error), "error colours")
        assertFalse(onNodeWithTag("good").captureToImage().containsColor(s.error), "no error colours when valid")
    }

    @Test
    fun focusThickensTheOutlineInPrimary() = runComposeUiTest {
        setContent { MaterialTheme(s) { KTextField("", {}, Modifier.testTag("f")) } }
        assertFalse(onNodeWithTag("f").captureToImage().containsColor(s.primary), "primary outline only when focused")
        assertTrue(onNodeWithTag("f").captureToImage().containsColor(s.outline), "idle outline")
        onNode(hasSetTextAction()).requestFocus()
        waitForIdle()
        assertTrue(onNodeWithTag("f").captureToImage().containsColor(s.primary), "focused outline")
    }

    @Test
    fun hoverDarkensTheOutline() = runComposeUiTest {
        val source = MutableInteractionSource()
        setContent { MaterialTheme(s) { KTextField("", {}, Modifier.testTag("f"), interactionSource = source) } }
        assertFalse(onNodeWithTag("f").captureToImage().containsColor(Color.Black), "no black before hover")
        runOnIdle { source.tryEmit(HoverInteraction.Enter()) }
        waitForIdle()
        assertTrue(onNodeWithTag("f").captureToImage().containsColor(s.onSurface), "hovered outline is onSurface")
    }

    @Test
    fun clickingTheContainerPaddingFocusesTheField() = runComposeUiTest {
        setContent { MaterialTheme(s) { KTextField("", {}, Modifier.testTag("f")) } }
        onNodeWithTag("f").performTouchInput { click(Offset(4f, height / 2f)) }
        waitForIdle()
        onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun disabledFieldCannotBeEdited() = runComposeUiTest {
        setContent { MaterialTheme(s) { KTextField("", {}, enabled = false) } }
        onNode(hasSetTextAction()).assertDoesNotExist()   // a disabled field offers no text editing action
        onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertIsNotEnabled()
    }

    @Test
    fun leadingAndTrailingSlotsGetTheFieldContentColour() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KTextField("", {}, Modifier.testTag("ok"), leading = { KIcon(SquareIcon, null) })
                    KTextField("", {}, Modifier.testTag("err"), isError = true, trailing = { KIcon(SquareIcon, null) })
                }
            }
        }
        assertTrue(onNodeWithTag("ok").captureToImage().containsColor(s.onSurfaceVariant), "leading icon")
        assertTrue(onNodeWithTag("err").captureToImage().containsColor(s.error), "trailing icon in error")
    }

    @Test
    fun textAreaIsTallerThanASingleLineField() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KTextField("", {}, Modifier.testTag("line"))
                    KTextArea("", {}, Modifier.testTag("area"))
                }
            }
        }
        val line = onNodeWithTag("line").fetchSemanticsNode().size.height
        val area = onNodeWithTag("area").fetchSemanticsNode().size.height
        assertTrue(area > line, "area $area should be taller than line $line")
    }

    @Test
    fun numberInputRulesAcceptOnlyNumbers() {
        assertTrue(isNumberInput("", false, false))
        assertTrue(isNumberInput("123", false, false))
        assertFalse(isNumberInput("12a", false, false))
        assertFalse(isNumberInput("1.5", false, false), "decimal not allowed")
        assertTrue(isNumberInput("1.5", true, false))
        assertTrue(isNumberInput(".5", true, false))
        assertTrue(isNumberInput("3.", true, false), "partial decimal while typing")
        assertFalse(isNumberInput("1.5.2", true, false))
        assertFalse(isNumberInput("-5", false, false), "negative not allowed")
        assertTrue(isNumberInput("-", false, true))
        assertTrue(isNumberInput("-5.5", true, true))
        assertFalse(isNumberInput("5-", true, true), "minus only at the start")
    }

    @Test
    fun numberFieldRejectsLettersAndKeepsTheLastValidValue() = runComposeUiTest {
        var text by mutableStateOf("12")
        setContent { MaterialTheme(s) { KNumberField(text, { text = it }) } }
        onNode(hasSetTextAction()).performTextInput("x")
        assertEquals("12", text)
        onNode(hasSetTextAction()).performTextInput("3")   // inserted at the cursor, wherever that is
        assertEquals(3, text.length)
        assertTrue(text.all { it.isDigit() } && '3' in text, "was $text")
    }
}
