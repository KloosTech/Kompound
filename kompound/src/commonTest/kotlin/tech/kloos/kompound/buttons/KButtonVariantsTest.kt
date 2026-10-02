package tech.kloos.kompound.buttons

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
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
class KButtonVariantsTest {
    private val s = ButtonTestScheme

    private fun background(variant: KButtonVariant): Color {
        var out = Color.Unspecified
        runComposeUiTest {
            setContent { MaterialTheme(s) { KButton(onClick = {}, Modifier.testTag("b"), variant = variant) { KText("MMMM") } } }
            out = onNodeWithTag("b").captureToImage().topCentre()
        }
        return out
    }

    @Test
    fun filledUsesPrimary() = assertTrue(background(KButtonVariant.Filled).near(s.primary))

    @Test
    fun tonalUsesSecondaryContainer() = assertTrue(background(KButtonVariant.Tonal).near(s.secondaryContainer))

    @Test
    fun outlinedAndTextHaveNoContainer() {
        assertEquals(0f, background(KButtonVariant.Outlined).alpha, 0.01f)
        assertEquals(0f, background(KButtonVariant.Text).alpha, 0.01f)
    }

    @Test
    fun outlinedDrawsOutlineColourAndOthersDoNot() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KButton(onClick = {}, Modifier.testTag("outlined"), variant = KButtonVariant.Outlined) { KText("MMMM") }
                    KButton(onClick = {}, Modifier.testTag("text"), variant = KButtonVariant.Text) { KText("MMMM") }
                }
            }
        }
        assertTrue(onNodeWithTag("outlined").captureToImage().containsColor(s.outline), "outlined needs a border")
        assertFalse(onNodeWithTag("text").captureToImage().containsColor(s.outline), "text variant has no border")
    }

    @Test
    fun labelAndIconUseTheVariantsContentColour() = runComposeUiTest {
        val expected = mapOf(
            KButtonVariant.Filled to s.onPrimary,
            KButtonVariant.Tonal to s.onSecondaryContainer,
            KButtonVariant.Outlined to s.primary,
            KButtonVariant.Text to s.primary,
        )
        setContent {
            MaterialTheme(s) {
                Column {
                    for (v in KButtonVariant.entries) {
                        KButton(onClick = {}, Modifier.testTag("label-$v"), variant = v) { KText("MMMM") }
                        KButton(onClick = {}, Modifier.testTag("icon-$v"), variant = v) { KIcon(SquareIcon, null) }
                    }
                }
            }
        }
        for ((v, colour) in expected) {
            assertTrue(onNodeWithTag("label-$v").captureToImage().containsColor(colour), "$v label colour")
            assertTrue(onNodeWithTag("icon-$v").captureToImage().containsColor(colour), "$v icon colour")
        }
    }

    @Test
    fun pressedAndHoveredShowAStateLayerOnTransparentVariants() = runComposeUiTest {
        val pressSource = MutableInteractionSource()
        val hoverSource = MutableInteractionSource()
        setContent {
            MaterialTheme(s) {
                Column {
                    KButton(onClick = {}, Modifier.testTag("p"), variant = KButtonVariant.Text, interactionSource = pressSource) { KText("MMMM") }
                    KButton(onClick = {}, Modifier.testTag("h"), variant = KButtonVariant.Outlined, interactionSource = hoverSource) { KText("MMMM") }
                }
            }
        }
        runOnIdle {
            pressSource.tryEmit(PressInteraction.Press(Offset.Zero))
            hoverSource.tryEmit(HoverInteraction.Enter())
        }
        waitForIdle()
        mainClock.advanceTimeBy(500)
        assertTrue(onNodeWithTag("p").captureToImage().topCentre().alpha > 0.05f, "pressed layer")
        assertTrue(onNodeWithTag("h").captureToImage().topCentre().alpha > 0.05f, "hovered layer")
    }

    @Test
    fun hoverChangesFilledBackground() = runComposeUiTest {
        val source = MutableInteractionSource()
        setContent { MaterialTheme(s) { KButton(onClick = {}, Modifier.testTag("b"), interactionSource = source) { KText("MMMM") } } }
        val before = onNodeWithTag("b").captureToImage().topCentre()
        runOnIdle { source.tryEmit(HoverInteraction.Enter()) }
        waitForIdle()
        mainClock.advanceTimeBy(500)
        assertFalse(before.near(onNodeWithTag("b").captureToImage().topCentre(), 0.01f), "hover layer missing")
    }

    @Test
    fun disabledFilledUsesDisabledContainer() = runComposeUiTest {
        setContent { MaterialTheme(s) { KButton(onClick = {}, Modifier.testTag("b"), enabled = false) { KText("MMMM") } } }
        val bg = onNodeWithTag("b").captureToImage().topCentre()
        assertTrue(bg.near(s.onSurface.copy(alpha = 0.12f), 0.03f), "disabled container was $bg")
    }
}
