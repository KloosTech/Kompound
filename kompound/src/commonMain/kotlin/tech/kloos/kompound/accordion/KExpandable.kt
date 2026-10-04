package tech.kloos.kompound.accordion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * A section whose [content] folds in and out under a tappable [header]. The header ends in a chevron that
 * turns while the section opens. Controlled: the caller owns [expanded]; use [KAccordion] to manage several
 * sections together.
 *
 * Screen readers get `expand` and `collapse` actions on the header. The whole header row is the touch target.
 *
 * @param expanded Whether the content is shown.
 * @param onExpandedChange Called with the new state when the header is activated.
 * @param modifier Modifier applied to the outermost node.
 * @param enabled When false the header ignores input and uses the disabled style block.
 * @param headerStyle Overrides merged over [KExpandableDefaults.headerStyle].
 * @param contentStyle Overrides merged over [KExpandableDefaults.contentStyle] (padding of the folded content).
 * @param interactionSource Feeds pressed/hovered/focused state of the header into its style.
 * @param header The header's content, usually a [KText]; it is laid out in a row before the chevron.
 * @param content The folded content.
 */
@Composable
public fun KExpandable(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    headerStyle: Style = Style,
    contentStyle: Style = Style,
    interactionSource: MutableInteractionSource? = null,
    header: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val headerState = rememberUpdatedStyleState(source) { it.isEnabled = enabled }
    val contentState = remember { MutableStyleState(null) }
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(source, enabled)
                .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button) { onExpandedChange(!expanded) }
                .semantics { if (expanded) collapse { onExpandedChange(false); true } else expand { onExpandedChange(true); true } }
                .styleable(headerState, KExpandableDefaults.headerStyle(), headerStyle),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalKContentColor provides KExpandableDefaults.contentColor(enabled)) {
                header()
                KIcon(KompoundIcons.ChevronDown, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = angle })
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().styleable(contentState, KExpandableDefaults.contentStyle(), contentStyle), content = content)
        }
    }
}

/** [KExpandable] whose header is a single line of [title]. */
@Composable
public fun KExpandable(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    headerStyle: Style = Style,
    contentStyle: Style = Style,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    KExpandable(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        enabled = enabled,
        headerStyle = headerStyle,
        contentStyle = contentStyle,
        interactionSource = interactionSource,
        header = { KText(title, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        content = content,
    )
}

/** Defaults for [KExpandable] and [KAccordion]. */
public object KExpandableDefaults {
    /** Header: 48dp row with 16dp side padding, title typography and state layers on hover, focus and press. */
    @Composable
    public fun headerStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        val density = KompoundTheme.tokens.density
        return remember(c, type, l, density) {
            fun layer(alpha: Float) = c.onSurface.copy(alpha = alpha).compositeOver(Color.Transparent)
            Style {
                contentColor(c.onSurface)
                textStyle(type.titleMedium.copy(color = c.onSurface))
                contentPadding(horizontal = 16.dp, vertical = density.space(12.dp))
                minHeight(density.height(48.dp))
                hovered { background(layer(l.hovered)) }
                focused { background(layer(l.focused)) }
                pressed { background(layer(l.pressed)) }
                disabled { contentColor(c.onSurface.copy(alpha = l.disabledContent)); textStyle(type.titleMedium.copy(color = c.onSurface.copy(alpha = l.disabledContent))) }
            }
        }
    }

    /** Folded content: 16dp padding on the sides and below, body typography. */
    @Composable
    public fun contentStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) {
            Style {
                contentColor(c.onSurfaceVariant)
                textStyle(type.bodyMedium.copy(color = c.onSurfaceVariant))
                contentPadding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
            }
        }
    }

    /** The colour of the chevron and header icons. */
    @Composable
    public fun contentColor(enabled: Boolean = true): Color {
        val c = MaterialTheme.colorScheme
        return if (enabled) c.onSurface else c.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
    }
}
