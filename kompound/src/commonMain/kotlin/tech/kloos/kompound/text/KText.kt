package tech.kloos.kompound.text

import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.LocalKContentColor

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
    // Keyed on the text: Compose's experimental inherited-text-style support loses the inherited style of an
    // existing text node when only its text changes (a changing title or label would fall back to the default
    // typography). A new node per text value inherits correctly at attach time.
    key(text) {
        BasicText(text, modifier = modifier.styleable(state, style), maxLines = maxLines, overflow = overflow)
    }
}

/**
 * [KText] for styled text: spans, links and other annotations of the [AnnotatedString] are kept.
 *
 * The experimental Styles API in the Compose version Kompound builds on does not apply a style's `textStyle`
 * (own or inherited) to annotated text, so the base look is passed as [textStyle]; [style] still applies
 * everything else (padding, background, size...). Without a colour in [textStyle] the text takes the
 * surrounding content colour.
 */
@Composable
public fun KText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: Style = Style,
    textStyle: TextStyle = TextStyle.Default,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val resolved = if (textStyle.color.isSpecified) textStyle else textStyle.copy(color = LocalKContentColor.current)
    key(text) {   // see the String overload
        BasicText(text, modifier = modifier.styleable(state, style), style = resolved, maxLines = maxLines, overflow = overflow)
    }
}
