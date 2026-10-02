package tech.kloos.kompound.buttons

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.near
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KToggleButtonTest {
    private val s = ButtonTestScheme

    @Test
    fun clickRequestsTheOppositeValueAndUpdatesSemantics() = runComposeUiTest {
        var checked by mutableStateOf(false)
        var requested: Boolean? = null
        setContent {
            MaterialTheme(s) {
                KToggleButton(checked = checked, onCheckedChange = { requested = it; checked = it }, Modifier.testTag("t")) { KIcon(SquareIcon, null) }
            }
        }
        assertEquals(ToggleableState.Off, onNodeWithTag("t").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState))
        onNodeWithTag("t").performClick()
        assertEquals(true, requested)
        assertEquals(ToggleableState.On, onNodeWithTag("t").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState))
    }

    @Test
    fun checkedIsFilledAndUncheckedIsOutlined() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KToggleButton(checked = true, onCheckedChange = {}, Modifier.testTag("on")) { KIcon(SquareIcon, null) }
                    KToggleButton(checked = false, onCheckedChange = {}, Modifier.testTag("off")) { KIcon(SquareIcon, null) }
                }
            }
        }
        val on = onNodeWithTag("on").captureToImage()
        val off = onNodeWithTag("off").captureToImage()
        assertTrue(on.topCentre().near(s.primary), "checked container")
        assertEquals(0f, off.topCentre().alpha, 0.01f)
        assertTrue(off.containsColor(s.outline), "unchecked outline")
        assertTrue(on.containsColor(s.onPrimary), "checked icon colour")
        assertTrue(off.containsColor(s.primary), "unchecked icon colour")
    }

    @Test
    fun disabledIgnoresInput() = runComposeUiTest {
        var calls = 0
        setContent { MaterialTheme(s) { KToggleButton(checked = false, onCheckedChange = { calls++ }, Modifier.testTag("t"), enabled = false) { KIcon(SquareIcon, null) } } }
        onNodeWithTag("t").assertIsNotEnabled().performClick()
        assertEquals(0, calls)
    }
}
