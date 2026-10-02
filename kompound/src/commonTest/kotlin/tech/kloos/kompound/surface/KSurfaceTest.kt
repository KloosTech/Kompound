package tech.kloos.kompound.surface

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.near
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KSurfaceTest {
    private val scheme = lightColorScheme(surface = Color(0xFF0000FF), onSurface = Color(0xFFFFFFFF))

    @Test
    fun usesThemeSurfaceBackground() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KSurface(Modifier.testTag("s"), style = Style { contentPadding(20.dp) }) { KText("x") } } }
        val img = onNodeWithTag("s").captureToImage()
        val sample = img.toPixelMap()[img.width / 2, 3]   // top edge middle: clear of rounded corners and content
        assertTrue(sample.near(scheme.surface), "background should be surface, was $sample")
    }

    @Test
    fun contentColourReachesTextAndIcons() = runComposeUiTest {
        val red = Color(0xFFFF0000)
        setContent {
            MaterialTheme(scheme) {
                Column {
                    KSurface(Modifier.testTag("text"), contentColor = red) { KText("MMMM") }
                    KSurface(Modifier.testTag("icon"), contentColor = red) { KIcon(SquareIcon, null) }
                }
            }
        }
        assertTrue(onNodeWithTag("text").captureToImage().containsColor(red), "text colour")
        assertTrue(onNodeWithTag("icon").captureToImage().containsColor(red), "icon colour")
    }

    @Test
    fun defaultContentColourIsOnSurface() = runComposeUiTest {
        setContent {
            MaterialTheme(scheme) {
                Column {
                    KSurface(Modifier.testTag("icon")) { KIcon(SquareIcon, null) }
                    KSurface(Modifier.testTag("text")) { KText("MMMM") }
                }
            }
        }
        assertTrue(onNodeWithTag("icon").captureToImage().containsColor(scheme.onSurface), "icon should be onSurface")
        assertTrue(onNodeWithTag("text").captureToImage().containsColor(scheme.onSurface), "text should be onSurface")
    }

    @Test
    fun clickInvokesCallbackAndExposesRole() = runComposeUiTest {
        var clicks = 0
        setContent { MaterialTheme(scheme) { KSurface(onClick = { clicks++ }, Modifier.testTag("s"), role = Role.Button) { KText("x") } } }
        onNodeWithTag("s").performClick()
        assertEquals(1, clicks)
        assertEquals(Role.Button, onNodeWithTag("s").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun disabledIgnoresClicks() = runComposeUiTest {
        var clicks = 0
        setContent { MaterialTheme(scheme) { KSurface(onClick = { clicks++ }, Modifier.testTag("s"), enabled = false) { KText("x") } } }
        onNodeWithTag("s").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun pressedChangesBackground() = runComposeUiTest {
        val source = MutableInteractionSource()
        setContent {
            MaterialTheme(scheme) {
                KSurface(onClick = {}, Modifier.testTag("s"), interactionSource = source, style = Style { contentPadding(20.dp) }) { KText("x") }
            }
        }
        val before = onNodeWithTag("s").captureToImage().let { it.toPixelMap()[it.width / 2, 3] }
        runOnIdle { source.tryEmit(PressInteraction.Press(Offset.Zero)) }
        waitForIdle()
        mainClock.advanceTimeBy(500)
        val after = onNodeWithTag("s").captureToImage().let { it.toPixelMap()[it.width / 2, 3] }
        assertFalse(before.near(after, 0.005f), "pressed state layer missing")
    }

    @Test
    fun hitAreaIncludesStylePadding() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KSurface(onClick = {}, Modifier.testTag("s"), style = Style { contentPadding(30.dp) }) { KIcon(SquareIcon, null) } } }
        val size = onNodeWithTag("s").fetchSemanticsNode().size
        assertEquals(24 + 60, size.width)
        assertEquals(24 + 60, size.height)
    }
}
