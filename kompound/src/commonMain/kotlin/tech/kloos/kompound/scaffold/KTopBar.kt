package tech.kloos.kompound.scaffold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Top app bar: an optional [navigation] slot (usually a back or menu `KIconButton`), a [title] and
 * [actions] on the end.
 *
 * Insets are explicit (COMPONENT_SPEC C-055): the bar's background extends under the system status bar and
 * its content is pushed down by [windowInsets]; pass `WindowInsets(0)` when the host already handles it.
 *
 * @param title Title text, shown on one line with an ellipsis.
 * @param modifier Modifier applied to the bar.
 * @param navigation Optional slot before the title.
 * @param actions Optional slot after the title, e.g. icon buttons.
 * @param windowInsets Insets the content avoids; defaults to the status bar.
 * @param style Overrides merged over [KTopBarDefaults.style].
 */
@Composable
public fun KTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    style: Style = Style,
) {
    KTopBar(
        title = { KText(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier, navigation = navigation, actions = actions, windowInsets = windowInsets, style = style,
    )
}

/** [KTopBar] with a composable title, e.g. a title with a subtitle. */
@Composable
public fun KTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.styleable(state, KTopBarDefaults.style(), style)) {
        Row(
            Modifier.windowInsetsPadding(windowInsets).defaultMinSize(minHeight = KTopBarDefaults.Height),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalKContentColor provides iconColor) { navigation?.invoke() }
            Box(Modifier.weight(1f)) { title() }
            if (actions != null) CompositionLocalProvider(LocalKContentColor provides iconColor) { actions() }
        }
    }
}

/** Defaults for [KTopBar]. */
public object KTopBarDefaults {
    /** Height of the bar's content area, below the status bar inset. */
    public val Height: Dp = 64.dp

    /** Bar style: `surface` background, title-large text in `onSurface`, 4dp side padding. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) {
            Style {
                background(c.surface)
                contentColor(c.onSurface)
                textStyle(type.titleLarge.copy(color = c.onSurface))
                contentPadding(horizontal = 4.dp, vertical = 0.dp)
            }
        }
    }
}
