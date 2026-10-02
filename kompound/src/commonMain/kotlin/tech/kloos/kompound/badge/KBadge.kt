package tech.kloos.kompound.badge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.size
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/** Meaning of a badge; picks its colours. */
public enum class KBadgeTone { Neutral, Primary, Error, Success, Warning, Info }

/** How loud a badge is: [Strong] is a solid fill, [Subtle] a tinted container. */
public enum class KBadgeEmphasis { Strong, Subtle }

/** Corner shape of a badge. */
public enum class KBadgeShape { Pill, Square }

/**
 * Small status or count label. For a bare indicator without text use [KBadgeDot].
 *
 * @param text Short text such as a count or a status word. Also used as the accessibility description.
 * @param modifier Modifier applied to the badge.
 * @param tone Meaning of the badge, e.g. [KBadgeTone.Error] for unread counts.
 * @param emphasis [KBadgeEmphasis.Strong] (default) or [KBadgeEmphasis.Subtle].
 * @param shape [KBadgeShape.Pill] (default) or [KBadgeShape.Square].
 * @param style Overrides merged over [KBadgeDefaults.style].
 * @param leading Optional slot before the text, usually a small `KIcon`.
 */
@Composable
public fun KBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: KBadgeTone = KBadgeTone.Error,
    emphasis: KBadgeEmphasis = KBadgeEmphasis.Strong,
    shape: KBadgeShape = KBadgeShape.Pill,
    style: Style = Style,
    leading: (@Composable () -> Unit)? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val colors = KBadgeDefaults.colors(tone, emphasis)
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = text }
            .styleable(state, KBadgeDefaults.style(tone, emphasis, shape), style),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalKContentColor provides colors.second) {
            leading?.invoke()
            KText(text, maxLines = 1)
        }
    }
}

/**
 * Bare 8dp indicator dot, e.g. "something new here".
 *
 * @param contentDescription Optional description for screen readers; the dot is decorative without one.
 */
@Composable
public fun KBadgeDot(
    modifier: Modifier = Modifier,
    tone: KBadgeTone = KBadgeTone.Error,
    emphasis: KBadgeEmphasis = KBadgeEmphasis.Strong,
    style: Style = Style,
    contentDescription: String? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val container = KBadgeDefaults.colors(tone, emphasis).first
    val described = if (contentDescription != null) modifier.semantics { this.contentDescription = contentDescription } else modifier
    val dot = remember(container) { Style { background(container); shape(CircleShape); size(8.dp) } }
    Box(described.styleable(state, dot, style))
}

/** Defaults for [KBadge]. */
public object KBadgeDefaults {
    /** Container and content colour of [tone] at [emphasis], from the theme and Kompound tokens. */
    @Composable
    public fun colors(tone: KBadgeTone, emphasis: KBadgeEmphasis = KBadgeEmphasis.Strong): Pair<Color, Color> {
        val c = MaterialTheme.colorScheme
        val k = KompoundTheme.tokens.colors
        val strong = emphasis == KBadgeEmphasis.Strong
        return when (tone) {
            KBadgeTone.Neutral -> if (strong) c.onSurfaceVariant to c.surface else c.surfaceContainerHighest to c.onSurfaceVariant
            KBadgeTone.Primary -> if (strong) c.primary to c.onPrimary else c.primaryContainer to c.onPrimaryContainer
            KBadgeTone.Error -> if (strong) c.error to c.onError else c.errorContainer to c.onErrorContainer
            KBadgeTone.Success -> if (strong) k.success to k.onSuccess else k.successContainer to k.onSuccessContainer
            KBadgeTone.Warning -> if (strong) k.warning to k.onWarning else k.warningContainer to k.onWarningContainer
            KBadgeTone.Info -> if (strong) k.info to k.onInfo else k.infoContainer to k.onInfoContainer
        }
    }

    /** Base style: 16dp minimum pill (or 4dp-cornered square) with label-small text. */
    @Composable
    public fun style(
        tone: KBadgeTone = KBadgeTone.Error,
        emphasis: KBadgeEmphasis = KBadgeEmphasis.Strong,
        shape: KBadgeShape = KBadgeShape.Pill,
    ): Style {
        val (container, content) = colors(tone, emphasis)
        val type = MaterialTheme.typography
        val shapes = MaterialTheme.shapes
        return remember(container, content, shape, type, shapes) {
            Style {
                background(container)
                contentColor(content)
                textStyle(type.labelSmall.copy(color = content))
                shape(if (shape == KBadgeShape.Pill) CircleShape else shapes.extraSmall)
                contentPadding(horizontal = 6.dp, vertical = 0.dp)
                minHeight(16.dp)
                minWidth(16.dp)
            }
        }
    }
}
