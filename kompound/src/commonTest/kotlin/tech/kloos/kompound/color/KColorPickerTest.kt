package tech.kloos.kompound.color

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KColorPickerTest {
    private fun near(a: Color, b: Color) = abs(a.red - b.red) < 0.01f && abs(a.green - b.green) < 0.01f && abs(a.blue - b.blue) < 0.01f && abs(a.alpha - b.alpha) < 0.01f

    @Test
    fun hexParsingAndFormatting() {
        assertEquals("#FF8000", KColors.toHex(Color(1f, 0.5f, 0f)))
        assertEquals("#FF800080", KColors.toHex(Color(1f, 0.5f, 0f, 0.5f), withAlpha = true))
        assertTrue(near(Color(0f, 1f, 0f), KColors.parseHex("#0f0")!!))
        assertTrue(near(Color(0.2f, 0.4f, 0.6f), KColors.parseHex("336699")!!))
        assertTrue(near(Color(1f, 1f, 1f, 0f), KColors.parseHex("#FFFFFF00")!!))
        assertNull(KColors.parseHex("#12"))
        assertNull(KColors.parseHex("#GGGGGG"))
    }

    @Test
    fun hsvRoundTripsAndKeepsPrimaries() {
        for (c in listOf(Color.Red, Color.Green, Color.Blue, Color(0.3f, 0.6f, 0.9f), Color(0.9f, 0.2f, 0.4f), Color.White, Color.Black)) {
            assertTrue(near(c, Hsv.of(c).toColor()), "$c -> ${Hsv.of(c).toColor()}")
        }
        assertEquals(120f, Hsv.of(Color.Green).h, 0.5f)
        assertEquals(240f, Hsv.of(Color.Blue).h, 0.5f)
    }

    @Test
    fun typingAHexValueChangesTheColour() = runComposeUiTest {
        var color by mutableStateOf(Color.Red)
        setContent { MaterialTheme(lightColorScheme()) { KColorPicker(color, { color = it }) } }
        onNode(hasSetTextAction()).performTextClearance()
        onNode(hasSetTextAction()).performTextInput("#00FF00")
        waitForIdle()
        assertTrue(near(Color.Green, color), "$color")
    }

    @Test
    fun aSwatchPicksItsColourAndTheHexIsAnnounced() = runComposeUiTest {
        var color by mutableStateOf(Color.Red)
        setContent { MaterialTheme(lightColorScheme()) { KColorPicker(color, { color = it }, swatches = listOf(Color(0xFF336699), Color.Black)) } }
        onNodeWithContentDescription("#336699").performClick()
        waitForIdle()
        assertTrue(near(Color(0xFF336699), color), "$color")
        onAllNodesWithContentDescription("#336699").assertCountEquals(2)   // the swatch and the preview
    }

    @Test
    fun theHueIsKeptForGreyColoursWhileTheAppOwnsTheState() = runComposeUiTest {
        var color by mutableStateOf(Color(0xFF3366CC))
        setContent { MaterialTheme(lightColorScheme()) { KColorPicker(color, { color = it }) } }
        onNodeWithContentDescription("Hue").assertIsDisplayed()
        onNodeWithContentDescription("Saturation, Brightness").assertIsDisplayed()
    }

    @Test
    fun theOpacitySliderOnlyExistsWithAlpha() = runComposeUiTest {
        var withAlpha by mutableStateOf(false)
        setContent { MaterialTheme(lightColorScheme()) { KColorPicker(Color.Red, {}, showAlpha = withAlpha) } }
        assertEquals(0, onAllOpacity())
        withAlpha = true
        waitForIdle()
        assertEquals(1, onAllOpacity())
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllOpacity() =
        onAllNodes(androidx.compose.ui.test.hasContentDescription("Opacity")).fetchSemanticsNodes().size
}
