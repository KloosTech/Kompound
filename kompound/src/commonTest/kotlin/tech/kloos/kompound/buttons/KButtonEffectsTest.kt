package tech.kloos.kompound.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.text.KText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KButtonEffectsTest {
    private val scheme = lightColorScheme(
        primary = Color(0xFF0000FF), onPrimary = Color.White,
        tertiary = Color(0xFFFF0000), onTertiary = Color.White,
    )
    private val source = MutableInteractionSource()

    private fun ComposeUiTest.show(effects: KButtonEffects, onClick: () -> Unit = {}) = setContent {
        MaterialTheme(scheme) {
            Box(Modifier.testTag("host").size(240.dp, 140.dp).background(Color.White), contentAlignment = Alignment.Center) {
                KButton(onClick = onClick, modifier = Modifier.testTag("b"), interactionSource = source, effects = effects) {
                    KText("Go", Modifier.size(80.dp, 20.dp))
                }
            }
        }
    }

    private fun ComposeUiTest.press() {
        runOnIdle { source.tryEmit(PressInteraction.Press(Offset.Zero)) }
        waitForIdle()
        mainClock.advanceTimeBy(900)
    }

    private fun ComposeUiTest.host(): ImageBitmap = onNodeWithTag("host").captureToImage()

    private fun ComposeUiTest.bounds() = onNodeWithTag("b").fetchSemanticsNode().boundsInRoot

    private fun Color.near(o: Color, tol: Float = 0.06f) =
        abs(red - o.red) < tol && abs(green - o.green) < tol && abs(blue - o.blue) < tol

    private fun ImageBitmap.at(x: Float, y: Float) = toPixelMap()[x.toInt(), y.toInt()]

    @Test
    fun clickShadowAppearsBelowThePressedButtonOnly() = runComposeUiTest {
        show(KButtonEffects.Default)
        val below = bounds().let { Offset(it.center.x, it.bottom + 6f) }
        assertTrue(host().at(below.x, below.y).near(Color.White), "shadow visible before the press")
        press()
        assertFalse(host().at(below.x, below.y).near(Color.White, 0.02f), "no shadow while pressed")
    }

    @Test
    fun noEffectsMeansNoShadow() = runComposeUiTest {
        show(KButtonEffects.None)
        val below = bounds().let { Offset(it.center.x, it.bottom + 6f) }
        press()
        assertTrue(host().at(below.x, below.y).near(Color.White, 0.01f), "shadow drawn with effects off")
    }

    @Test
    fun bounceShrinksThePressedButton() = runComposeUiTest {
        show(KButtonEffects.None.copy(bounce = true))
        val b = bounds()
        val edge = Offset(b.left + 2f, b.center.y)
        assertTrue(host().at(edge.x, edge.y).near(scheme.primary, 0.1f), "button edge missing before press")
        press()
        assertTrue(host().at(edge.x, edge.y).near(Color.White, 0.1f), "button did not shrink")
        assertEquals(b, bounds(), "layout bounds must not change")
    }

    @Test
    fun fadeDimsThePressedButton() = runComposeUiTest {
        show(KButtonEffects.None.copy(fade = true))
        val c = bounds().center
        press()
        val pressed = host().at(c.x, c.y - 14f)
        // Opaque pressed primary would be darker than the same colour at 60 % over white.
        assertTrue(pressed.red > 0.2f && pressed.green > 0.2f, "button not dimmed: $pressed")
    }

    @Test
    fun colorMorphEndsOnTheTertiaryAccent() = runComposeUiTest {
        show(KButtonEffects.None.copy(colorMorph = true))
        val b = bounds()
        press()
        assertTrue(host().at(b.left + 14f, b.center.y).near(scheme.tertiary, 0.1f), "pressed colour is not tertiary")
    }

    @Test
    fun shapeMorphSquaresTheCornersWhilePressed() = runComposeUiTest {
        show(KButtonEffects.None.copy(shapeMorph = true))
        val b = bounds()
        val corner = Offset(b.left + 4f, b.top + 4f)
        assertTrue(host().at(corner.x, corner.y).near(Color.White), "corner already filled before the press")
        press()
        assertTrue(host().at(corner.x, corner.y).near(scheme.primary, 0.15f), "corner still round while pressed")
    }

    @Test
    fun sparklesFlyOutOfTheButtonOnClickAndStillClick() = runComposeUiTest {
        var clicks = 0
        show(KButtonEffects.None.copy(sparkles = true)) { clicks++ }
        val b = bounds()
        fun outside(img: ImageBitmap): Int {
            var n = 0
            val px = img.toPixelMap()
            for (y in 0 until px.height) for (x in 0 until px.width) {
                val inside = x >= b.left && x < b.right && y >= b.top && y < b.bottom
                if (!inside && !px[x, y].near(Color.White, 0.02f)) n++
            }
            return n
        }
        assertEquals(0, outside(host()), "particles before the click")
        mainClock.autoAdvance = false
        onNodeWithTag("b").performClick()
        mainClock.advanceTimeBy(300)
        assertEquals(1, clicks)
        assertTrue(outside(host()) > 0, "no particles after the click")
        mainClock.advanceTimeBy(1500)
        assertEquals(0, outside(host()), "particles did not fade out")
    }
}
