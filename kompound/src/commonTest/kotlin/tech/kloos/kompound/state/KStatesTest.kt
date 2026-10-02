package tech.kloos.kompound.state

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KStatesTest {
    private val s = ButtonTestScheme.copy(error = Color(0xFFFF00FF), onSurfaceVariant = Color(0xFF00FF80))

    @Test
    fun emptyStateShowsTitleDescriptionIllustrationAndAction() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(s) {
                KEmptyState(
                    "No messages", description = "Start a conversation",
                    illustration = { KText("ILLUSTRATION") },
                    action = { KButton(onClick = { clicks++ }) { KText("Write") } },
                )
            }
        }
        listOf("No messages", "Start a conversation", "ILLUSTRATION").forEach { onNodeWithText(it, useUnmergedTree = true).assertExists() }
        onNodeWithText("Write", useUnmergedTree = true).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun optionalPartsAreOmitted() = runComposeUiTest {
        setContent { MaterialTheme(s) { KEmptyState("Nothing here", Modifier.testTag("e")) } }
        onNodeWithText("Nothing here", useUnmergedTree = true).assertExists()
        assertNull(onNodeWithTag("e").fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun descriptionUsesTheQuietColour() = runComposeUiTest {
        setContent { MaterialTheme(s) { KEmptyState("MMMM", Modifier.testTag("e"), description = "MMMM") } }
        assertTrue(onNodeWithTag("e").captureToImage().containsColor(s.onSurfaceVariant), "description colour")
        assertTrue(onNodeWithTag("e").captureToImage().containsColor(s.onSurface), "title colour")
    }

    @Test
    fun errorStateAnnouncesItselfAndShowsADefaultRedIcon() = runComposeUiTest {
        setContent { MaterialTheme(s) { KErrorState("Could not load", Modifier.testTag("e")) } }
        assertEquals(LiveRegionMode.Polite, onNodeWithTag("e").fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
        assertTrue(onNodeWithTag("e").captureToImage().containsColor(s.error), "default error icon uses the error colour")
    }

    @Test
    fun retryButtonAppearsOnlyWithACallbackAndUsesTheGivenText() = runComposeUiTest {
        var retries = 0
        setContent {
            MaterialTheme(s) {
                Column {
                    KErrorState("With retry", onRetry = { retries++ }, retryText = "Nochmal")
                    KErrorState("Without retry")
                }
            }
        }
        onNodeWithText("Nochmal", useUnmergedTree = true).performClick()
        assertEquals(1, retries)
        onNodeWithText("Try again", useUnmergedTree = true).assertDoesNotExist()   // the default label is replaced
    }

    @Test
    fun customIllustrationAndActionReplaceTheDefaults() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KErrorState("Offline", Modifier.testTag("e"), onRetry = {}, illustration = { KIcon(SquareIcon, null) }, action = { KText("CUSTOM ACTION") })
            }
        }
        onNodeWithText("CUSTOM ACTION", useUnmergedTree = true).assertExists()
        onNodeWithText("Try again", useUnmergedTree = true).assertDoesNotExist()
        assertFalse(onNodeWithTag("e").captureToImage().containsColor(s.error), "the default red icon is gone")
    }

    @Test
    fun noIllustrationWhenPassedNull() = runComposeUiTest {
        setContent { MaterialTheme(s) { KErrorState("Plain", Modifier.testTag("e"), illustration = null) } }
        assertFalse(onNodeWithTag("e").captureToImage().containsColor(s.error))
    }
}
