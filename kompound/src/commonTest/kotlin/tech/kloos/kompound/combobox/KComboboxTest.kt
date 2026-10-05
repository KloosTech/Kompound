package tech.kloos.kompound.combobox

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KComboboxTest {
    private val fruit = listOf("Apple", "Apricot", "Banana", "Blueberry", "Cherry")

    @Test
    fun typingNarrowsTheListAndClickPicks() = runComposeUiTest {
        var text by mutableStateOf("")
        var picked: String? = null
        setContent {
            MaterialTheme(lightColorScheme()) {
                KCombobox(text, { text = it }, fruit, { picked = it }, { it }, Modifier, label = "Fruit")
            }
        }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("b")
        waitForIdle()
        onNodeWithText("Banana").assertIsDisplayed()
        onNodeWithText("Blueberry").assertIsDisplayed()
        onAllNodesWithText("Cherry").assertCountEquals(0)
        onNodeWithText("Banana").performClick()
        waitForIdle()
        assertEquals("Banana", picked)
        assertEquals("Banana", text, "the label goes into the field")
        onAllNodesWithText("Blueberry").assertCountEquals(0)
    }

    @Test
    fun nothingMatchingShowsTheNoResultsText() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent { MaterialTheme(lightColorScheme()) { KCombobox(text, { text = it }, fruit, {}, { it }) } }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("zzz")
        waitForIdle()
        onNodeWithText("No results").assertIsDisplayed()
    }

    @Test
    fun withFilteringOffTheOptionsAreShownAsGiven() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent { MaterialTheme(lightColorScheme()) { KCombobox(text, { text = it }, fruit, {}, { it }, filterOptions = false) } }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("q")
        waitForIdle()
        onNodeWithText("Cherry").assertIsDisplayed()
        onNodeWithText("Apple").assertIsDisplayed()
    }

    @Test
    fun aDisabledComboboxShowsNoList() = runComposeUiTest {
        setContent { MaterialTheme(lightColorScheme()) { KCombobox("a", {}, fruit, {}, { it }, enabled = false) } }
        onAllNodesWithText("Apple").assertCountEquals(0)
    }

    @Test
    fun arrowKeysMoveAndEnterPicks() = runComposeUiTest {
        var text by mutableStateOf("")
        var picked: String? = null
        setContent { MaterialTheme(lightColorScheme()) { KCombobox(text, { text = it }, fruit, { picked = it }, { it }) } }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("b")
        waitForIdle()
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("Blueberry", picked)
    }
}
