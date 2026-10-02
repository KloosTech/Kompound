package tech.kloos.kompound.buttons

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.near
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KFabTest {
    private val s = ButtonTestScheme

    @Test
    fun isAtLeast56dpAndClickable() = runComposeUiTest {
        var clicks = 0
        setContent { MaterialTheme(s) { KFab(onClick = { clicks++ }, "Create", Modifier.testTag("f")) { KIcon(SquareIcon, null) } } }
        val size = onNodeWithTag("f").fetchSemanticsNode().size
        assertEquals(56, size.width)
        assertEquals(56, size.height)
        onNodeWithTag("f").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun exposesContentDescription() = runComposeUiTest {
        setContent { MaterialTheme(s) { KFab(onClick = {}, "Create", Modifier.testTag("f")) { KIcon(SquareIcon, null) } } }
        assertEquals(listOf("Create"), onNodeWithTag("f").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun usesPrimaryContainerAndOnPrimaryContainerIcon() = runComposeUiTest {
        setContent { MaterialTheme(s) { KFab(onClick = {}, "Create", Modifier.testTag("f")) { KIcon(SquareIcon, null) } } }
        val img = onNodeWithTag("f").captureToImage()
        assertTrue(img.topCentre(4).near(s.primaryContainer), "container")
        assertTrue(img.containsColor(s.onPrimaryContainer), "icon")
    }

    @Test
    fun extendedStyleGrowsWithItsLabel() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KFab(onClick = {}, "Create", Modifier.testTag("f"), style = KFabDefaults.extendedStyle()) {
                    KIcon(SquareIcon, null)
                    KText("Create item")
                }
            }
        }
        assertTrue(onNodeWithTag("f").fetchSemanticsNode().size.width > 56)
    }
}
