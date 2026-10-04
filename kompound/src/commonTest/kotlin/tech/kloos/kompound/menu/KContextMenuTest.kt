package tech.kloos.kompound.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.countText
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KContextMenuTest {
    private val scheme = lightColorScheme()

    @Test
    fun aRightClickOpensTheMenuAndAPickRunsTheActionAndCloses() = runComposeUiTest {
        val log = mutableListOf<String>()
        setContent {
            MaterialTheme(scheme) {
                KContextMenuArea({ listOf(KMenuActionItem("Copy", { log += "copy" }), KMenuActionDivider, KMenuActionItem("Delete", { log += "delete" })) }, Modifier.size(300.dp, 200.dp).testTag("area")) {
                    KText("Area content")
                }
            }
        }
        assertEquals(0, countText("Copy"))
        onNodeWithTag("area").performMouseInput { rightClick(Offset(40f, 40f)) }
        waitForIdle()
        onNodeWithText("Copy").assertExists()
        onNodeWithText("Delete").assertExists()
        onNodeWithText("Copy").performClick()
        waitForIdle()
        assertEquals(listOf("copy"), log)
        assertEquals(0, countText("Copy"), "closed after the pick")
    }

    @Test
    fun aLongPressOpensItOnTouchScreensAndTheContentStillGetsItsOwnClicks() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(scheme) {
                KContextMenuArea({ listOf(KMenuActionItem("Rename", {})) }, Modifier.size(300.dp, 200.dp).testTag("area")) {
                    Box(Modifier.size(100.dp).testTag("inner").androidx_clickable { clicks++ })
                }
            }
        }
        onNodeWithTag("inner").performClick()
        assertEquals(1, clicks, "a plain click passes through")
        assertEquals(0, countText("Rename"))
        onNodeWithTag("area").performTouchInput { longClick(Offset(150f, 150f)) }
        waitForIdle()
        onNodeWithText("Rename").assertExists()
    }

    @Test
    fun theMenuKeyOpensItAndEmptyActionsOpenNothing() = runComposeUiTest {
        var withItems = true
        setContent {
            MaterialTheme(scheme) {
                KContextMenuArea({ if (withItems) listOf(KMenuActionItem("Inspect", {}), null) else listOf(null, KMenuActionDivider) }, Modifier.size(200.dp)) {
                    Box(Modifier.size(100.dp).testTag("focusable").focusable())
                }
            }
        }
        onNodeWithTag("focusable").requestFocus()
        onNodeWithTag("focusable").performKeyInput { pressKey(Key.Menu) }
        waitForIdle()
        onNodeWithText("Inspect").assertExists()
        onNodeWithText("Inspect").performClick()
        waitForIdle()
        withItems = false
        onNodeWithTag("focusable").performMouseInput { rightClick(Offset(10f, 10f)) }
        waitForIdle()
        assertEquals(0, countText("Inspect"))
    }

    @Test
    fun disabledAreasIgnoreRightClicks() = runComposeUiTest {
        setContent {
            MaterialTheme(scheme) { KContextMenuArea({ listOf(KMenuActionItem("Nope", {})) }, Modifier.size(200.dp).testTag("area"), enabled = false) { KText("x") } }
        }
        onNodeWithTag("area").performMouseInput { rightClick(Offset(10f, 10f)) }
        waitForIdle()
        assertEquals(0, countText("Nope"))
    }
}

private fun Modifier.androidx_clickable(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
