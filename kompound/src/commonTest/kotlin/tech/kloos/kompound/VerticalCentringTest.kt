package tech.kloos.kompound

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KFab
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.buttons.KProgressButton
import tech.kloos.kompound.buttons.KToggleButton
import tech.kloos.kompound.chip.KChip
import tech.kloos.kompound.date.KDateField
import tech.kloos.kompound.dropdown.KDropdown
import tech.kloos.kompound.accordion.KExpandable
import tech.kloos.kompound.avatar.KAvatar
import tech.kloos.kompound.inline.KInlineEdit
import tech.kloos.kompound.list.KListItem
import tech.kloos.kompound.menu.KMenuItem
import tech.kloos.kompound.scaffold.KTopBar
import tech.kloos.kompound.search.KSearchBar
import tech.kloos.kompound.segmented.KSegmentedControl
import tech.kloos.kompound.selection.KCheckbox
import tech.kloos.kompound.selection.KRadioButton
import tech.kloos.kompound.selection.KSwitch
import tech.kloos.kompound.slide.KSlideToConfirm
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KNumberField
import tech.kloos.kompound.textfield.KPasswordField
import tech.kloos.kompound.textfield.KTextField
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Text inside controls with a fixed height must sit in the vertical middle. Each case renders one control with
 * a label of capital letters (no descenders, so the glyph box is symmetric), finds the rows the text occupies
 * and compares their centre with the control's centre.
 */
@OptIn(ExperimentalTestApi::class)
class VerticalCentringTest {
    private val scheme = lightColorScheme()
    private val failures = mutableListOf<String>()

    private fun Color.differs(bg: Color) = abs(red - bg.red) + abs(green - bg.green) + abs(blue - bg.blue) + 3 * abs(alpha - bg.alpha) > 0.5f

    /** Text rows of the node's image, looking only at the middle band (no borders, rounded corners or icons). */
    private fun textRows(img: ImageBitmap, from: Float = 0.04f, to: Float = 0.6f): IntRange? {
        val map = img.toPixelMap()
        val x0 = (map.width * from).toInt()
        val x1 = (map.width * to).toInt()
        val bg = map[x0, map.height / 2].let { first ->
            // most common colour in the band is the background
            val counts = HashMap<Color, Int>()
            for (y in 0 until map.height step 1) for (x in x0 until x1 step 2) map[x, y].let { c -> counts[c] = (counts[c] ?: 0) + 1 }
            counts.maxByOrNull { it.value }?.key ?: first
        }
        val rows = (2 until map.height - 2).filter { y -> (x0 until x1).any { x -> map[x, y].differs(bg) } }
        return if (rows.isEmpty()) null else rows.first()..rows.last()
    }

    private fun check(name: String, tolerance: Double = 2.0, from: Float = 0.04f, to: Float = 0.6f, content: @Composable () -> Unit) = runComposeUiTest {
        setContent { MaterialTheme(scheme) { Box(Modifier.testTag("t")) { content() } } }
        mainClock.advanceTimeBy(1_000)
        val img = onNodeWithTag("t").captureToImage()
        val rows = textRows(img, from, to)
        if (rows == null) { failures += "$name: no text found"; return@runComposeUiTest }
        val centre = (rows.first + rows.last) / 2.0
        val offset = centre - (img.height - 1) / 2.0
        if (abs(offset) > tolerance) failures += "$name: text rows ${rows.first}..${rows.last} in ${img.height}px, off by ${offset}px"
    }

    @Test
    fun controlsCentreTheirText() {
        val w = Modifier.width(240.dp)
        KButtonVariant.entries.forEach { v -> check("KButton $v") { KButton({}, w, variant = v) { KText("HEH") } } }
        check("KToggleButton off") { KToggleButton(false, {}, w) { KText("HEH") } }
        check("KToggleButton on") { KToggleButton(true, {}, w) { KText("HEH") } }
        check("KChip") { KChip("HEH", {}, w) }
        check("KChip selected") { KChip("HEH", {}, w, selected = true) }
        check("KBadge") { KBadge("HEH", w) }
        check("KSegmentedControl") { KSegmentedControl(listOf("HEH", "HEH", "HEH"), 1, {}, w) }
        check("KProgressButton idle") { KProgressButton("HEH", null, {}, w) }
        check("KProgressButton running") { KProgressButton("HEH", 0.5f, {}, w) }
        check("KMenuItem") { KMenuItem("HEH", {}, w) }
        check("KListItem") { KListItem(headline = "HEH", modifier = w, onClick = {}) }
        check("KExpandable header") { KExpandable("HEH", false, {}, w) { KText("x") } }
        check("KSlideToConfirm") { KSlideToConfirm("HEH", {}, w, animated = false) }
        check("KTopBar") { KTopBar("HEH", w) }
        check("KAvatar", from = 0.35f, to = 0.65f) { KAvatar("H E") }
        check("KTextField value") { KTextField("HEH", {}, w) }
        check("KTextField placeholder") { KTextField("", {}, w, placeholder = "HEH") }
        check("KNumberField") { KNumberField("123", {}, w) }
        check("KPasswordField placeholder") { KPasswordField("", {}, w, placeholder = "HEH") }
        check("KSearchBar") { KSearchBar("HEH", {}, w) }
        check("KDropdown") { KDropdown(listOf("HEH"), "HEH", {}, w) }
        check("KDateField") { KDateField(1_700_000_000_000L, {}, w) }
        check("KInlineEdit view") { KInlineEdit("HEH", {}, w) }
        check("KButton with leading icon", from = 0.45f, to = 0.7f) { KButton({}, w) { KIcon(SquareIcon, null); KText("HEH") } }
        check("KChip with leading icon", from = 0.17f, to = 0.4f) { KChip("HEH", {}, w, leading = { KIcon(SquareIcon, null) }) }
        check("KMenuItem with icon", from = 0.1f, to = 0.5f) { KMenuItem("HEH", {}, w, leading = { KIcon(SquareIcon, null) }) }
        check("KListItem with leading", from = 0.2f, to = 0.6f) { KListItem(headline = "HEH", modifier = w, leading = { KIcon(SquareIcon, null) }) }
        check("KFab with text", from = 0.0f, to = 1.0f) { KFab({}, "x", Modifier) { KText("HEH") } }
        check("KCheckbox label", from = 0.12f, to = 0.5f) { KCheckbox(true, {}, w, label = { KText("HEH") }) }
        check("KRadioButton label", from = 0.12f, to = 0.5f) { KRadioButton(true, {}, w, label = { KText("HEH") }) }
        check("KSwitch label", from = 0.0f, to = 0.5f) { KSwitch(true, {}, w, label = { KText("HEH") }) }
        assertTrue(failures.isEmpty(), "text is not vertically centred:\n" + failures.joinToString("\n"))
    }
}
