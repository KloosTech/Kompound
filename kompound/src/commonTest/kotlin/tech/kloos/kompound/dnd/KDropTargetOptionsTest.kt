package tech.kloos.kompound.dnd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.dnd.reorder.KReorderableItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KDropTargetOptionsTest {
    private val entered = mutableListOf<String>()

    private fun ComposeUiTest.show(ignoreSelf: Boolean) = setContent {
        MaterialTheme(lightColorScheme()) {
            val state = rememberKDragAndDropState<String>()
            KDragAndDropContainer(state, Modifier.size(200.dp, 300.dp)) {
                Column {
                    listOf("A", "B").forEach { id ->
                        KReorderableItem(
                            key = id, data = id, state = state, ignoreSelf = ignoreSelf, onDragEnter = { entered += "$id<-${it.data}" },
                            draggableContent = { Box(Modifier.size(200.dp, 60.dp)) },
                            modifier = Modifier.testTag(id).fillMaxWidth().height(60.dp),
                        ) { Box(Modifier.fillMaxWidth().height(60.dp)) }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.dragA(dy: Float) {
        mainClock.autoAdvance = false
        onNodeWithTag("A").performTouchInput { down(center) }
        mainClock.advanceTimeBy(50)
        repeat(10) {
            onNodeWithTag("A").performTouchInput { moveBy(Offset(0f, dy / 10)) }
            mainClock.advanceTimeBy(16)
        }
        repeat(20) { mainClock.advanceTimeBy(16) }
        onNodeWithTag("A").performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
    }

    @Test
    fun anItemIsNotToldAboutItselfByDefault() = runComposeUiTest {
        show(ignoreSelf = true)
        waitForIdle()
        dragA(dy = 40f)
        assertTrue(entered.none { it == "A<-A" }, "entered=$entered")
    }

    @Test
    fun ignoreSelfCanBeSwitchedOff() = runComposeUiTest {
        show(ignoreSelf = false)
        waitForIdle()
        dragA(dy = 40f)
        assertTrue("A<-A" in entered, "entered=$entered")
    }

    @Test
    fun movingOverTheNextItemStillReportsIt() = runComposeUiTest {
        show(ignoreSelf = true)
        waitForIdle()
        dragA(dy = 60f)
        assertEquals(listOf("B<-A"), entered.filter { it.startsWith("B") }.distinct())
    }
}
