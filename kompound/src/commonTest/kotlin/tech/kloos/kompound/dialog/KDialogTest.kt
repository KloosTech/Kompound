package tech.kloos.kompound.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.topCentre
import tech.kloos.kompound.near
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KDialogTest {
    private val s = ButtonTestScheme.copy(surfaceContainerHigh = Color(0xFF223344))

    @Test
    fun showsTitleContentAndActions() = runComposeUiTest {
        var confirmed = 0
        setContent {
            MaterialTheme(s) {
                KDialog({}, title = "Delete file", actions = { KButton(onClick = { confirmed++ }) { KText("Delete") } }) { KText("This cannot be undone") }
            }
        }
        onNodeWithText("Delete file", useUnmergedTree = true).assertExists()
        onNodeWithText("This cannot be undone", useUnmergedTree = true).assertExists()
        onNodeWithText("Delete", useUnmergedTree = true).performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun titleIsAHeadingAndTheWindowIsADialog() = runComposeUiTest {
        setContent { MaterialTheme(s) { KDialog({}, title = "Heading") { KText("Body") } } }
        onNode(isDialog()).assertExists()
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true).assertExists()
    }

    @Test
    fun clickingTheScrimDismissesButClickingTheSurfaceDoesNot() = runComposeUiTest {
        var dismissed = 0
        setContent { MaterialTheme(s) { KDialog({ dismissed++ }, Modifier.testTag("surface"), title = "T") { KText("Body text") } } }
        onNodeWithText("Body text", useUnmergedTree = true).performClick()
        onNodeWithTag("surface").performTouchInput { click(center) }
        assertEquals(0, dismissed, "clicks on the surface must not dismiss")
        onNode(isDialog()).performTouchInput { click(Offset(4f, 4f)) }
        assertEquals(1, dismissed, "a click on the scrim dismisses")
    }

    @Test
    fun scrimClickCanBeDisabled() = runComposeUiTest {
        var dismissed = 0
        setContent { MaterialTheme(s) { KDialog({ dismissed++ }, title = "T", dismissOnClickOutside = false) { KText("Body") } } }
        onNode(isDialog()).performTouchInput { click(Offset(4f, 4f)) }
        assertEquals(0, dismissed)
    }

    @Test
    fun closeButtonDismissesAndUsesTheGivenDescription() = runComposeUiTest {
        var dismissed = 0
        setContent {
            MaterialTheme(s) { KDialog({ dismissed++ }, title = "T", showCloseButton = true, closeContentDescription = "Schließen") { KText("Body") } }
        }
        onNodeWithContentDescription("Schließen").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun surfaceUsesTheThemeContainerColour() = runComposeUiTest {
        setContent { MaterialTheme(s) { KDialog({}, Modifier.testTag("surface"), title = "T") { KText("Body") } } }
        assertTrue(onNodeWithTag("surface").captureToImage().topCentre(2).near(s.surfaceContainerHigh), "dialog surface colour")
    }

    @Test
    fun surfaceWidthIsBetween280And560dp() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                androidx.compose.foundation.layout.Column {
                    KDialog({}, Modifier.testTag("narrow")) { KText("x") }
                }
            }
        }
        val w = onNodeWithTag("narrow").fetchSemanticsNode().size.width
        assertTrue(w in 280..560, "width $w")
    }

    @Test
    fun tallContentScrollsInsteadOfGrowingPastTheWindow() = runComposeUiTest {
        setContent { MaterialTheme(s) { KDialog({}, Modifier.testTag("surface"), title = "T") { Box(Modifier.width(100.dp).height(5000.dp)) } } }
        val surface = onNodeWithTag("surface").fetchSemanticsNode().size.height
        val window = onNode(isDialog()).fetchSemanticsNode().size.height
        assertTrue(surface <= window * 0.9f + 1, "surface $surface should stay within 90% of the window $window")
    }

    @Test
    fun fullScreenFillsTheWindow() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KDialog({}, Modifier.testTag("surface"), title = "T", fullScreen = true, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) { KText("Body") }
            }
        }
        val surface = onNodeWithTag("surface").fetchSemanticsNode().size
        val window = onNode(isDialog()).fetchSemanticsNode().size
        assertEquals(window.width, surface.width)
        assertEquals(window.height, surface.height)
    }

    @Test
    fun alertDialogConfirmsAndDismisses() = runComposeUiTest {
        var confirmed = 0
        var dismissed = 0
        setContent {
            MaterialTheme(s) {
                KAlertDialog({ dismissed++ }, title = "Sure?", message = "Really delete?", confirmText = "Yes", onConfirm = { confirmed++ }, dismissText = "No")
            }
        }
        onNodeWithText("Really delete?", useUnmergedTree = true).assertExists()
        onNodeWithText("Yes", useUnmergedTree = true).performClick()
        onNodeWithText("No", useUnmergedTree = true).performClick()
        assertEquals(1, confirmed)
        assertEquals(1, dismissed)
    }

    @Test
    fun alertDialogWithoutDismissTextHasOnlyTheConfirmButton() = runComposeUiTest {
        setContent { MaterialTheme(s) { KAlertDialog({}, title = "Info", message = "Done", confirmText = "OK", onConfirm = {}) } }
        onNodeWithText("OK", useUnmergedTree = true).assertExists()
        onNodeWithText("Cancel", useUnmergedTree = true).assertDoesNotExist()
    }
}
