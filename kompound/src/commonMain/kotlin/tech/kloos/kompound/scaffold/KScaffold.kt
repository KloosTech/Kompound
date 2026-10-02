package tech.kloos.kompound.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles

/**
 * Basic screen layout: [topBar] at the top, [bottomBar] at the bottom, and [content] in between. The
 * [floatingActionButton] floats at the bottom end of the content area and the [snackbarHost] at its bottom
 * centre.
 *
 * Insets are explicit (COMPONENT_SPEC C-055): [content] receives [contentWindowInsets] as padding values
 * and nothing else is consumed; the bars take their own insets (see [KTopBar]).
 *
 * @param modifier Modifier applied to the whole screen.
 * @param topBar Optional slot at the top, usually a [KTopBar].
 * @param bottomBar Optional slot at the bottom, e.g. a navigation bar.
 * @param snackbarHost Optional slot overlaying the bottom centre of the content, usually a `KSnackbarHost`.
 * @param floatingActionButton Optional slot overlaying the bottom end of the content, usually a `KFab`.
 * @param contentWindowInsets Insets passed to [content] as padding values; defaults to none.
 * @param style Overrides merged over [KScaffoldDefaults.style] (the screen background).
 * @param content The screen body; receives the padding it should apply.
 */
@Composable
public fun KScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    snackbarHost: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    contentWindowInsets: WindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    style: Style = Style,
    content: @Composable (PaddingValues) -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    Column(modifier.fillMaxSize().styleable(state, KScaffoldDefaults.style(), style)) {
        topBar?.invoke()
        Box(Modifier.weight(1f).fillMaxSize()) {
            content(contentWindowInsets.asPaddingValues())
            if (floatingActionButton != null) {
                Box(Modifier.align(Alignment.BottomEnd).padding(KScaffoldDefaults.OverlayPadding)) { floatingActionButton() }
            }
            if (snackbarHost != null) {
                Box(Modifier.align(Alignment.BottomCenter).padding(KScaffoldDefaults.OverlayPadding)) { snackbarHost() }
            }
        }
        bottomBar?.invoke()
    }
}

/** Defaults for [KScaffold]. */
public object KScaffoldDefaults {
    /** Gap between the floating button or snackbar and the edges of the content area. */
    public val OverlayPadding: PaddingValues = PaddingValues(16.dp)

    /** Screen style: the theme's `background` colour and `onBackground` content. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        return remember(c) { Style { background(c.background); contentColor(c.onBackground) } }
    }
}
