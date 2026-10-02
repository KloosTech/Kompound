package tech.kloos.kompound.showcase.accordion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.accordion.KAccordion
import tech.kloos.kompound.accordion.KAccordionState
import tech.kloos.kompound.accordion.KExpandable
import tech.kloos.kompound.accordion.rememberKAccordionState
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.text.KText

private const val Usage_accordion_basic = """import tech.kloos.kompound.accordion.KAccordion
import tech.kloos.kompound.accordion.KExpandable
import tech.kloos.kompound.accordion.rememberKAccordionState
import tech.kloos.kompound.text.KText

// Several sections managed together; exclusive = true keeps one open at a time.
val state = rememberKAccordionState(exclusive = true, initiallyExpanded = setOf("shipping"))
KAccordion(state = state) {
    Item(key = "shipping", title = "Shipping") {
        KText("Free for orders above 50 euros.")
    }
    Item(key = "returns", title = "Returns") {
        KText("Send items back within 30 days.")
    }
}

// A single section you control yourself; the header can be any content.
var open by remember { mutableStateOf(false) }
KExpandable(expanded = open, onExpandedChange = { open = it }, header = { KText("Advanced", Modifier.weight(1f)) }) {
    KText("Only for experts.")
}"""

@KompoundDemo(
    id = "accordion.basic",
    title = "KAccordion",
    description = "Sections that fold open under a tappable header, one at a time or several together; KExpandable is a single section.",
    category = KompoundCategory.Layout,
    tags = ["accordion", "expandable", "collapse", "faq", "disclosure", "section"],
    since = "0.1.0",
    usage = Usage_accordion_basic,
)
@Composable
fun DemoScope.KAccordionDemo() {
    val exclusive = boolControl("Exclusive", false)
    val enabled = boolControl("Enabled", true)
    val badge = boolControl("Badge in header", true)
    val state = remember(exclusive) { KAccordionState(exclusive, setOf("first")) }
    var single by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        KAccordion(Modifier.fillMaxWidth(), state = state) {
            Item(key = "first", title = "Shipping", enabled = enabled) { KText("Free for orders above 50 euros. Standard delivery takes two to four working days.") }
            Item(
                key = "second",
                enabled = enabled,
                header = {
                    KText("Returns", Modifier.weight(1f))
                    if (badge) KBadge("New")
                },
            ) { KText("Send items back within 30 days. We refund to the original payment method.") }
            Item(key = "third", title = "Warranty", enabled = enabled) { KText("Two years on all devices.") }
        }
        KExpandable("A single KExpandable", single, { single = it }, enabled = enabled) { KText("Controlled by you: the caller owns the expanded state.") }
    }
}
