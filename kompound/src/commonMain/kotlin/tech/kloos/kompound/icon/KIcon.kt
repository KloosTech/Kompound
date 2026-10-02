package tech.kloos.kompound.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.size
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Icon tinted with [tint]. Icons are not text, so they do not inherit a parent Style's `contentColor`
 * (ADR 0001). Kompound components provide their content colour through `LocalKContentColor`, which is
 * the fallback when [tint] is unspecified; outside any component it falls back to `onSurface`.
 *
 * @param contentDescription Describes the icon for screen readers. Pass `null` only for decorative icons.
 * @param modifier Modifier applied to the icon.
 * @param style Overrides merged over [KIconDefaults.style] (size).
 * @param tint Explicit colour; unspecified means the ambient content colour.
 */
@Composable
public fun KIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    style: Style = Style,
    tint: Color = Color.Unspecified,
) {
    KIcon(rememberVectorPainter(imageVector), contentDescription, modifier, style, tint)
}

/** @see KIcon */
@Composable
public fun KIcon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    style: Style = Style,
    tint: Color = Color.Unspecified,
) {
    KIcon(remember(bitmap) { BitmapPainter(bitmap) }, contentDescription, modifier, style, tint)
}

/** @see KIcon */
@Composable
public fun KIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    style: Style = Style,
    tint: Color = Color.Unspecified,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val resolved = tint.takeOrElse { LocalKContentColor.current.takeOrElse { MaterialTheme.colorScheme.onSurface } }
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.styleable(state, KIconDefaults.style(), style),
        colorFilter = ColorFilter.tint(resolved),
    )
}

/** Defaults for [KIcon]. */
public object KIconDefaults {
    /** Base style: a 24dp square. */
    public fun style(): Style = Style { size(24.dp) }
}
