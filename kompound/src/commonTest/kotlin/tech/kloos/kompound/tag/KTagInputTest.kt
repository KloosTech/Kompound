package tech.kloos.kompound.tag

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KTagInputTest {
    @Test
    fun aSeparatorEndsATagAndPastingMakesSeveral() = runComposeUiTest {
        var tags by mutableStateOf(emptyList<String>())
        setContent { MaterialTheme(lightColorScheme()) { KTagInput(tags, { tags = it }, label = "Tags") } }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("alpha,")
        waitForIdle()
        assertEquals(listOf("alpha"), tags)
        onNode(hasSetTextAction()).performTextInput("beta, gamma,del")
        waitForIdle()
        assertEquals(listOf("alpha", "beta", "gamma"), tags)
        onNodeWithText("alpha").assertIsDisplayed()
    }

    @Test
    fun theImeActionCommitsAndDuplicatesAreIgnored() = runComposeUiTest {
        var tags by mutableStateOf(listOf("one"))
        setContent { MaterialTheme(lightColorScheme()) { KTagInput(tags, { tags = it }) } }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("ONE")
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertEquals(listOf("one"), tags, "same tag ignoring case")
        onNode(hasSetTextAction()).performTextInput("two")
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertEquals(listOf("one", "two"), tags)
    }

    @Test
    fun clickingATagRemovesItAndItIsAnnounced() = runComposeUiTest {
        var tags by mutableStateOf(listOf("red", "blue"))
        setContent { MaterialTheme(lightColorScheme()) { KTagInput(tags, { tags = it }) } }
        onNodeWithContentDescription("Remove red").performClick()
        waitForIdle()
        assertEquals(listOf("blue"), tags)
    }

    @Test
    fun validateRefusesATagWithAMessage() = runComposeUiTest {
        var tags by mutableStateOf(emptyList<String>())
        setContent {
            MaterialTheme(lightColorScheme()) { KTagInput(tags, { tags = it }, validate = { if ('@' !in it) "Not an e-mail address" else null }) }
        }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("nope,")
        waitForIdle()
        assertEquals(emptyList(), tags)
        onNodeWithText("Not an e-mail address").assertIsDisplayed()
        onNode(hasSetTextAction()).performTextInput("a@b.c,")
        waitForIdle()
        assertEquals(listOf("a@b.c"), tags)
    }

    @Test
    fun maxTagsStopsAdding() = runComposeUiTest {
        var tags by mutableStateOf(listOf("a"))
        setContent { MaterialTheme(lightColorScheme()) { KTagInput(tags, { tags = it }, maxTags = 1) } }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("b,")
        waitForIdle()
        assertEquals(listOf("a"), tags)
    }
}
