package tech.kloos.kompound.text

import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
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
 * [KText] with a Material [textStyle] (`MaterialTheme.typography.titleLarge`, [KTextDefaults.heading]): the font, size and weight come from
 * [textStyle]; the colour stays the surrounding content colour unless [textStyle] sets one. [style] still applies on top.
 */
@Composable
public fun KText(
    text: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    style: Style = Style,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val base = remember(textStyle) { Style { textStyle(textStyle) } }
    KText(text, modifier, Style(base, style), maxLines, overflow)
}

/** Ready-made text styles for [KText]. */
public object KTextDefaults {
    /** A section heading: the theme's `titleLarge`, semi-bold. */
    @Composable
    public fun heading(): TextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    /** A smaller heading: the theme's `titleMedium`, semi-bold. */
    @Composable
    public fun subheading(): TextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

    /** A quiet caption: the theme's `labelMedium` in the variant colour. */
    @Composable
    public fun caption(): TextStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
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
