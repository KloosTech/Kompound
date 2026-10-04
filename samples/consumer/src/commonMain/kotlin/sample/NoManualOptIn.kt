package sample

import androidx.compose.runtime.Composable
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField

/** Regression check: calls with default `style` compile with no `optIn` in this module's build; the `tech.kloos.kompound` plugin adds it. */
@Composable
fun NoManualOptIn() {
    KButton(onClick = {}) { KText("Hi") }
    KTextField("", {})
}
