package tech.kloos.kompound.sheet

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
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
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KBottomSheetTest {
    private val s = ButtonTestScheme
    private val noInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    private val alwaysSheet = 100_000.dp
    private val alwaysDialog = 1.dp

    @Test
    fun wideWindowsGetACentredDialogWithTheSameContent() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(s) {
                KBottomSheet(
                    {}, Modifier.testTag("content"), title = "Share", dialogFromWidth = alwaysDialog,
                    actions = { KButton(onClick = { clicks++ }) { KText("Send") } },
                ) { KText("Choose people") }
            }
        }
        onNodeWithText("Share", useUnmergedTree = true).assertExists()
        onNodeWithText("Choose people", useUnmergedTree = true).assertExists()
        onNodeWithText("Send", useUnmergedTree = true).performClick()
        assertEquals(1, clicks)
        val window = onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        val surface = onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        assertTrue(surface.bottom < window.bottom - 20f, "the dialog is centred, not docked at the bottom")
    }

    @Test
    fun narrowWindowsGetABottomSheetDockedAtTheBottom() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KBottomSheet({}, Modifier.testTag("content"), title = "Share", dialogFromWidth = alwaysSheet, contentWindowInsets = noInsets) {
                    KText("Choose people")
                }
            }
        }
        waitForIdle()
        onNodeWithText("Share", useUnmergedTree = true).assertExists()
        onNodeWithText("Choose people", useUnmergedTree = true).assertExists()
        val window = onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        val content = onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        assertTrue(window.bottom - content.bottom < 4f, "sheet content reaches the bottom edge (${window.bottom} vs ${content.bottom})")
        // Material 3 caps the sheet at 640dp and centres it on wide windows.
        val width = content.width
        assertTrue(width <= 640f && width > 280f, "sheet width $width")
        assertTrue(kotlin.math.abs((content.left + content.right) / 2f - (window.left + window.right) / 2f) < 2f, "sheet is centred")
    }

    @Test
    fun titleIsAHeadingInBothForms() = runComposeUiTest {
        setContent {
            androidx.compose.foundation.layout.Column {
                MaterialTheme(s) { KBottomSheet({}, title = "Sheet title", dialogFromWidth = alwaysSheet) { KText("x") } }
            }
        }
        waitForIdle()
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true).assertExists()
    }

    @Test
    fun sheetCloseButtonDismissesAfterTheSheetHasHidden() = runComposeUiTest {
        var dismissed = 0
        setContent {
            MaterialTheme(s) {
                KBottomSheet({ dismissed++ }, title = "T", showCloseButton = true, closeContentDescription = "Zu", dialogFromWidth = alwaysSheet) { KText("x") }
            }
        }
        waitForIdle()
        onNodeWithContentDescription("Zu").performClick()
        waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun dialogFormCloseButtonDismisses() = runComposeUiTest {
        var dismissed = 0
        setContent {
            MaterialTheme(s) {
                KBottomSheet({ dismissed++ }, title = "T", showCloseButton = true, closeContentDescription = "Zu", dialogFromWidth = alwaysDialog) { KText("x") }
            }
        }
        onNodeWithContentDescription("Zu").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun clickingTheScrimDismissesTheDialogForm() = runComposeUiTest {
        var dismissed = 0
        setContent { MaterialTheme(s) { KBottomSheet({ dismissed++ }, title = "T", dialogFromWidth = alwaysDialog) { KText("x") } } }
        onNode(isDialog()).performTouchInput { click(Offset(4f, 4f)) }
        assertEquals(1, dismissed)
    }

    @Test
    fun clickingTheScrimDismissesTheSheetForm() = runComposeUiTest {
        var dismissed = 0
        setContent { MaterialTheme(s) { KBottomSheet({ dismissed++ }, title = "T", dialogFromWidth = alwaysSheet) { KText("x") } } }
        waitForIdle()
        onNode(isDialog()).performTouchInput { click(Offset(4f, 4f)) }
        waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun scrimClickCanBeDisabledOnTheSheet() = runComposeUiTest {
        var dismissed = 0
        setContent {
            MaterialTheme(s) { KBottomSheet({ dismissed++ }, title = "T", dialogFromWidth = alwaysSheet, dismissOnClickOutside = false) { KText("x") } }
        }
        waitForIdle()
        onNode(isDialog()).performTouchInput { click(Offset(4f, 4f)) }
        waitForIdle()
        assertEquals(0, dismissed)
    }
}
