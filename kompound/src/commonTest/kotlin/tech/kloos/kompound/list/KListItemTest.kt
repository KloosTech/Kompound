package tech.kloos.kompound.list

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.buttons.topCentre
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.near
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KListItemTest {
    private val s = ButtonTestScheme.copy(onSurfaceVariant = Color(0xFFFF0080))

    @Test
    fun showsAllTextSlots() = runComposeUiTest {
        setContent { MaterialTheme(s) { KListItem("Headline", supporting = "Supporting", overline = "Overline") } }
        listOf("Headline", "Supporting", "Overline").forEach { onNodeWithText(it, useUnmergedTree = true).assertExists() }
    }

    @Test
    fun clickableRowReportsClicksWithButtonRole() = runComposeUiTest {
        var clicks = 0
        setContent { MaterialTheme(s) { KListItem("Row", Modifier.testTag("r"), onClick = { clicks++ }) } }
        onNodeWithTag("r").performClick()
        assertEquals(1, clicks)
        assertEquals(Role.Button, onNodeWithTag("r").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun staticRowHasNoClickAction() = runComposeUiTest {
        setContent { MaterialTheme(s) { KListItem("Row", Modifier.testTag("r")) } }
        assertEquals(null, onNodeWithTag("r").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun selectableRowExposesSelectedStateAndTint() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KListItem("On", Modifier.testTag("on"), onClick = {}, selected = true)
                    KListItem("Off", Modifier.testTag("off"), onClick = {}, selected = false)
                }
            }
        }
        val on = onNodeWithTag("on").fetchSemanticsNode().config
        assertEquals(true, on.getOrNull(SemanticsProperties.Selected))
        assertEquals(Role.RadioButton, on.getOrNull(SemanticsProperties.Role))
        assertTrue(onNodeWithTag("on").captureToImage().topCentre().near(s.secondaryContainer), "selected tint")
        assertEquals(0f, onNodeWithTag("off").captureToImage().topCentre().alpha, 0.01f)
    }

    @Test
    fun supportingTextIsQuieterThanTheHeadline() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KListItem("MMMM", Modifier.testTag("head"))
                    KListItem(headline = { }, Modifier.testTag("support"), supporting = { KText("MMMM") })
                }
            }
        }
        assertTrue(onNodeWithTag("head").captureToImage().containsColor(s.onSurface), "headline uses onSurface")
        assertTrue(onNodeWithTag("support").captureToImage().containsColor(s.onSurfaceVariant), "supporting uses onSurfaceVariant")
    }

    @Test
    fun leadingAndTrailingIconsGetTheQuietColour() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KListItem("", Modifier.testTag("lead"), leading = { KIcon(SquareIcon, null) })
                    KListItem("", Modifier.testTag("trail"), trailing = { KIcon(SquareIcon, null) })
                }
            }
        }
        assertTrue(onNodeWithTag("lead").captureToImage().containsColor(s.onSurfaceVariant))
        assertTrue(onNodeWithTag("trail").captureToImage().containsColor(s.onSurfaceVariant))
    }

    @Test
    fun rowHeightGrowsWithItsContentAndHasAMinimum() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KListItem("One line", Modifier.testTag("one"))
                    KListItem("Two lines", Modifier.testTag("two"), supporting = "Second line")
                }
            }
        }
        val one = onNodeWithTag("one").fetchSemanticsNode().size.height
        val two = onNodeWithTag("two").fetchSemanticsNode().size.height
        assertTrue(one >= 56, "minimum height, was $one")
        assertTrue(two >= 72, "two-line row has a 72 minimum, was $two")
        assertTrue(two > one, "two-line row ($two) should be taller than one-line ($one)")
    }

    @Test
    fun bottomSlotIsShownAndDisabledRowIgnoresClicks() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(s) {
                KListItem("Row", Modifier.testTag("r"), bottom = { KText("Chips here") }, onClick = { clicks++ }, enabled = false)
            }
        }
        onNodeWithText("Chips here", useUnmergedTree = true).assertExists()
        onNodeWithTag("r").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }
}
