package tech.kloos.kompound.dnd

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
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KDropZoneTest {
    private val scheme = lightColorScheme()
    private val dropped = mutableListOf<String>()
    private var hoveredSeen = false

    private fun ComposeUiTest.show(accepts: (String) -> Boolean = { true }, enabled: Boolean = true) = setContent {
        MaterialTheme(scheme) {
            val state = rememberKDragAndDropState<String>()
            KDragAndDropContainer(state, Modifier.size(240.dp, 400.dp)) {
                Column {
                    KDraggable(key = "card", data = "payload", state = state, modifier = Modifier.testTag("card").fillMaxWidth()) {
                        KText("Card", Modifier.height(48.dp))
                    }
                    KDropZone(
                        key = "zone", state = state, onDrop = { dropped += it }, modifier = Modifier.testTag("zone").fillMaxWidth().height(120.dp),
                        enabled = enabled, accepts = accepts,
                    ) { hovered -> if (hovered) hoveredSeen = true; KText("Zone") }
                }
            }
        }
    }

    private fun ComposeUiTest.dragCardOntoZone() {
        mainClock.autoAdvance = false
        onNodeWithTag("card").performTouchInput { down(center) }
        mainClock.advanceTimeBy(50)
        repeat(20) {
            onNodeWithTag("card").performTouchInput { moveBy(Offset(0f, 5f)) }
            mainClock.advanceTimeBy(16)
        }
        repeat(20) { mainClock.advanceTimeBy(16) }
        onNodeWithTag("card").performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
    }

    @Test
    fun droppingACardOnTheZoneDeliversItsDataOnce() = runComposeUiTest {
        show()
        waitForIdle()
        dragCardOntoZone()
        assertEquals(listOf("payload"), dropped)
        assertTrue(hoveredSeen, "the zone never reported a hovering item")
    }

    @Test
    fun aZoneThatDoesNotAcceptTheItemGetsNoDrop() = runComposeUiTest {
        show(accepts = { false })
        waitForIdle()
        dragCardOntoZone()
        assertTrue(dropped.isEmpty())
        assertTrue(!hoveredSeen)
    }

    @Test
    fun aDisabledZoneGetsNoDrop() = runComposeUiTest {
        show(enabled = false)
        waitForIdle()
        dragCardOntoZone()
        assertTrue(dropped.isEmpty())
    }
}
