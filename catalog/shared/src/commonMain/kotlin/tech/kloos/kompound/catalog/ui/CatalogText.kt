package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.style.Style
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** Text styles of the catalog page: a theme typography role plus either the main or the quiet colour. */
@Composable
internal fun textRole(quiet: Boolean = false, weight: FontWeight? = null, role: (Typography) -> TextStyle): Style {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val color: Color = if (quiet) scheme.onSurfaceVariant else scheme.onSurface
    return remember(scheme, type, quiet, weight) {
        Style {
            contentColor(color)
            textStyle(role(type).let { if (weight != null) it.copy(fontWeight = weight, color = color) else it.copy(color = color) })
        }
    }
}
