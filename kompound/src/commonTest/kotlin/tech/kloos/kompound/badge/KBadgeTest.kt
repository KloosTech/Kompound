package tech.kloos.kompound.badge

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.buttons.topCentre
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.near
import tech.kloos.kompound.theme.KompoundColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KBadgeTest {
    private val s = ButtonTestScheme
    private val k = KompoundColors.Light

    private fun container(tone: KBadgeTone, emphasis: KBadgeEmphasis): androidx.compose.ui.graphics.Color {
        var out = androidx.compose.ui.graphics.Color.Unspecified
        runComposeUiTest {
            setContent { MaterialTheme(s) { KBadge("MMMM", Modifier.testTag("b"), tone, emphasis) } }
            out = onNodeWithTag("b").captureToImage().topCentre(1)
        }
        return out
    }

    @Test
    fun strongTonesUseTheirSolidColours() {
        assertTrue(container(KBadgeTone.Error, KBadgeEmphasis.Strong).near(s.error))
        assertTrue(container(KBadgeTone.Primary, KBadgeEmphasis.Strong).near(s.primary))
        assertTrue(container(KBadgeTone.Success, KBadgeEmphasis.Strong).near(k.success))
        assertTrue(container(KBadgeTone.Warning, KBadgeEmphasis.Strong).near(k.warning))
        assertTrue(container(KBadgeTone.Info, KBadgeEmphasis.Strong).near(k.info))
    }

    @Test
    fun subtleTonesUseContainerColours() {
        assertTrue(container(KBadgeTone.Error, KBadgeEmphasis.Subtle).near(s.errorContainer))
        assertTrue(container(KBadgeTone.Success, KBadgeEmphasis.Subtle).near(k.successContainer))
        assertTrue(container(KBadgeTone.Info, KBadgeEmphasis.Subtle).near(k.infoContainer))
    }

    @Test
    fun textAndLeadingIconUseTheOnColour() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KBadge("MMMM", Modifier.testTag("text"), KBadgeTone.Success)
                    KBadge("", Modifier.testTag("icon"), KBadgeTone.Success, leading = { KIcon(SquareIcon, null) })
                }
            }
        }
        assertTrue(onNodeWithTag("text").captureToImage().containsColor(k.onSuccess), "text colour")
        assertTrue(onNodeWithTag("icon").captureToImage().containsColor(k.onSuccess), "icon colour")
    }

    @Test
    fun pillIsRoundedAndSquareIsNot() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KBadge("MMMM", Modifier.testTag("pill"), shape = KBadgeShape.Pill)
                    KBadge("MMMM", Modifier.testTag("square"), shape = KBadgeShape.Square)
                }
            }
        }
        val pill = onNodeWithTag("pill").captureToImage().toPixelMap()[1, 1]
        val square = onNodeWithTag("square").captureToImage().toPixelMap()[2, 2]
        assertEquals(0f, pill.alpha, 0.2f, "pill corner should be cut away")
        assertTrue(square.alpha > 0.9f, "square corner should be filled")
    }

    @Test
    fun singleCharacterBadgeIsAtLeast16dpWide() = runComposeUiTest {
        setContent { MaterialTheme(s) { KBadge("1", Modifier.testTag("b")) } }
        val size = onNodeWithTag("b").fetchSemanticsNode().size
        assertTrue(size.width >= 16 && size.height >= 16, "was $size")
    }

    @Test
    fun textIsTheAccessibilityDescription() = runComposeUiTest {
        setContent { MaterialTheme(s) { KBadge("12 new", Modifier.testTag("b")) } }
        val d = onNodeWithTag("b").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("12 new"), d)
    }

    @Test
    fun dotIs8dpAndDescribedOnlyWhenAsked() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KBadgeDot(Modifier.testTag("plain"))
                    KBadgeDot(Modifier.testTag("described"), contentDescription = "New activity")
                }
            }
        }
        val size = onNodeWithTag("plain").fetchSemanticsNode().size
        assertEquals(8, size.width)
        assertEquals(8, size.height)
        assertEquals(null, onNodeWithTag("plain").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(listOf("New activity"), onNodeWithTag("described").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
    }
}
