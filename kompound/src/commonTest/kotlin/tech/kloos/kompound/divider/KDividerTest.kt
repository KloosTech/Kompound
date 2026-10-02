package tech.kloos.kompound.divider

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.style.Style
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.containsColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KDividerTest {
    private val scheme = lightColorScheme(outlineVariant = Color(0xFFFF00FF))

    @Test
    fun horizontalFillsWidthAndIsOneDpThick() = runComposeUiTest {
        setContent {
            MaterialTheme(scheme) { Box(Modifier.width(100.dp)) { KDivider(Modifier.testTag("d")) } }
        }
        val size = onNodeWithTag("d").fetchSemanticsNode().size
        assertEquals(100, size.width)
        assertEquals(1, size.height)
    }

    @Test
    fun verticalFillsHeightAndIsOneDpWide() = runComposeUiTest {
        setContent {
            MaterialTheme(scheme) { Box(Modifier.height(80.dp)) { KDivider(Modifier.testTag("d"), orientation = Orientation.Vertical) } }
        }
        val size = onNodeWithTag("d").fetchSemanticsNode().size
        assertEquals(80, size.height)
        assertEquals(1, size.width)
    }

    @Test
    fun usesOutlineVariantByDefaultAndStyleOverridesColourAndThickness() = runComposeUiTest {
        setContent {
            MaterialTheme(scheme) {
                Column(Modifier.width(50.dp)) {
                    KDivider(Modifier.testTag("default"))
                    KDivider(Modifier.testTag("custom"), style = Style { background(Color.Blue); height(4.dp) })
                }
            }
        }
        assertTrue(onNodeWithTag("default").captureToImage().containsColor(scheme.outlineVariant))
        assertTrue(onNodeWithTag("custom").captureToImage().containsColor(Color.Blue))
        assertEquals(4, onNodeWithTag("custom").fetchSemanticsNode().size.height)
    }
}
