package tech.kloos.kompound.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.demo.DemoControls
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Automated accessibility checks over every demo (the registry grows, the test follows). In the merged semantics tree, which is what
 * a screen reader walks:
 *
 * 1. every control that can be clicked or toggled has a name (text or content description),
 * 2. every text field has a name,
 * 3. every control other than a text field is at least 24 x 24 dp (WCAG 2.2 AA, target size minimum; a field's container is its target),
 * 4. no control is announced as clickable without a role,
 * 5. every control can take keyboard focus (it offers a focus request).
 *
 * Known gaps are listed with a reason in [knownGaps]; the list may only shrink.
 */
@OptIn(ExperimentalTestApi::class)
class DemoAccessibilityTest {
    /** `demo id` to `check number`: the demo shows a control that does not (yet) meet the check. */
    private val knownGaps: Set<Pair<String, Int>> = emptySet()

    private val interactiveRoles = setOf(Role.Button, Role.Checkbox, Role.Switch, Role.RadioButton, Role.Tab, Role.DropdownList)

    private fun SemanticsNode.name(): String {
        val cd = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        return (cd + " " + text).trim()
    }

    private fun SemanticsNode.walk(): Sequence<SemanticsNode> = sequence {
        yield(this@walk)
        for (c in children) yieldAll(c.walk())
    }

    @Test
    fun everyInteractiveControlInEveryDemoIsNamedSizedAndHasARole() {
        val problems = mutableListOf<String>()
        for (entry in KompoundAllDemos.entries) {
            runComposeUiTest {
                mainClock.autoAdvance = false
                var density = 1f
                setContent {
                    MaterialTheme(lightColorScheme()) {
                        density = LocalDensity.current.density
                        val controls = remember { DemoControls() }
                        val content = entry.content
                        // like the catalog's preview: inside a vertical scroll, so the height is unbounded
                        Box(Modifier.testTag("demo").verticalScroll(rememberScrollState())) { controls.content() }
                    }
                }
                repeat(3) { mainClock.advanceTimeByFrame() }
                for (node in onRoot().fetchSemanticsNode().walk()) {
                    val config = node.config
                    if (config.getOrNull(SemanticsProperties.Disabled) != null) continue
                    val clickable = config.contains(SemanticsActions.OnClick)
                    val role = config.getOrNull(SemanticsProperties.Role)
                    val editable = config.contains(SemanticsActions.SetText) || config.contains(SemanticsProperties.EditableText)
                    // Compose's own link nodes (the words of a link inside a text) take their name from the text around them.
                    if (config.any { it.key.name == "LinkTestMarker" }) continue
                    if (!clickable && !editable && role !in interactiveRoles) continue
                    val w0 = node.size.width / density
                    if (w0 == 0f || node.size.height == 0) continue   // not on screen (folded content)
                    val where = "${entry.qualifiedId} [${node.name().ifEmpty { "unnamed" }.take(30)}; role=$role; near='${generateSequence(node.parent) { it.parent }.map { it.name() }.firstOrNull { it.isNotBlank() }.orEmpty().take(40)}'; at=${node.positionInRoot}; ${config.map { it.key.name }.joinToString(",")}]"
                    if (node.name().isBlank() && (entry.qualifiedId to 1) !in knownGaps) problems += "$where: no name (check 1/2)"
                    val w = node.size.width / density
                    val h = node.size.height / density
                    // A text field's semantics node is the text line; the touch target is its 56dp container, which focuses the field on a tap.
                    if (!editable && (w < 24f || h < 24f) && (entry.qualifiedId to 3) !in knownGaps) problems += "$where: ${w.toInt()}x${h.toInt()}dp is below 24dp (check 3)"
                    if (!config.contains(SemanticsActions.RequestFocus) && (entry.qualifiedId to 5) !in knownGaps) problems += "$where: cannot take keyboard focus (check 5)"
                    if (clickable && role == null && !editable && (entry.qualifiedId to 4) !in knownGaps) problems += "$where: clickable without a role (check 4)"
                }
            }
        }
        assertTrue(problems.isEmpty(), "Accessibility problems:\n" + problems.joinToString("\n"))
    }
}
