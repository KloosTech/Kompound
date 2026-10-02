package tech.kloos.kompound.text

import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import tech.kloos.kompound.KompoundStyles

/**
 * Text that takes part in the Styles API: it inherits `contentColor` and text properties from the
 * nearest styled parent (for example [tech.kloos.kompound.buttons.KButton]) and can be restyled via [style].
 * Material `Text` does not read these inherited properties.
 */
@Composable
public fun KText(
    text: String,
    modifier: Modifier = Modifier,
    style: Style = Style,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    BasicText(text, modifier = modifier.styleable(state, style), maxLines = maxLines, overflow = overflow)
}
