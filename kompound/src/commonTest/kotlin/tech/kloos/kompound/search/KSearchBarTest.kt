package tech.kloos.kompound.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.buttons.topCentre
import tech.kloos.kompound.near
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KSearchBarTest {
    private val s = ButtonTestScheme

    @Test
    fun showsPlaceholderAndReportsTypedQuery() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent { MaterialTheme(s) { KSearchBar(query, { query = it }, placeholder = "Find components") } }
        onNodeWithText("Find components", useUnmergedTree = true).assertExists()
        onNode(hasSetTextAction()).performTextInput("button")
        assertEquals("button", query)
    }

    @Test
    fun clearButtonExistsOnlyWithTextAndClears() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent { MaterialTheme(s) { KSearchBar(query, { query = it }, clearContentDescription = "Clear") } }
        onNodeWithContentDescription("Clear").assertDoesNotExist()
        query = "abc"
        waitForIdle()
        onNodeWithContentDescription("Clear").assertExists().performClick()
        assertEquals("", query)
    }

    @Test
    fun imeSearchActionCallsOnSearchWithTheQuery() = runComposeUiTest {
        var searched: String? = null
        setContent { MaterialTheme(s) { KSearchBar("kompound", {}, onSearch = { searched = it }) } }
        onNode(hasSetTextAction()).performImeAction()
        assertEquals("kompound", searched)
    }

    @Test
    fun isAFilledPillWithoutOutline() = runComposeUiTest {
        setContent { MaterialTheme(s) { KSearchBar("", {}, Modifier.testTag("bar")) } }
        val img = onNodeWithTag("bar").captureToImage()
        assertTrue(img.topCentre(2).near(s.surfaceContainerHigh), "fill")
        assertEquals(0f, img.toPixelMap()[1, 1].alpha, 0.2f, "pill corner is cut away")
    }

    @Test
    fun trailingActionsAreShown() = runComposeUiTest {
        setContent { MaterialTheme(s) { KSearchBar("", {}, trailingActions = { KText("Filter") }) } }
        onNodeWithText("Filter", useUnmergedTree = true).assertExists()
    }

    @Test
    fun disabledBarCannotBeEdited() = runComposeUiTest {
        setContent { MaterialTheme(s) { KSearchBar("", {}, enabled = false) } }
        onNode(hasSetTextAction()).assertDoesNotExist()
    }
}
