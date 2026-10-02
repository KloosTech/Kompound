package tech.kloos.kompound.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.size
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme

/** Diameter presets of [KAvatar]. */
public enum class KAvatarSize(internal val diameter: Dp, internal val statusDot: Dp) {
    Small(32.dp, 10.dp),
    Medium(40.dp, 12.dp),
    Large(56.dp, 16.dp),
}

/** Presence shown as a small dot on an avatar. */
public enum class KAvatarStatus(internal val label: String) {
    Online("online"),
    Away("away"),
    Busy("busy"),
    Offline("offline"),
}

/**
 * Round avatar: shows [image] when given, otherwise the initials of [name] on a colour derived from the
 * name (the same name always gets the same colour). Loading images is the caller's job (any image
 * library works in the [image] slot).
 *
 * @param name Person's name; used for initials, colour and the accessibility description.
 * @param modifier Modifier applied to the outermost node.
 * @param size Preset diameter.
 * @param style Overrides merged over [KAvatarDefaults.style].
 * @param status Optional presence dot at the bottom end.
 * @param image Optional slot filling the circle, e.g. a photo.
 */
@Composable
public fun KAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: KAvatarSize = KAvatarSize.Medium,
    style: Style = Style,
    status: KAvatarStatus? = null,
    image: (@Composable () -> Unit)? = null,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val description = if (status == null) name else "$name, ${status.label}"
    Box(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Box(Modifier.styleable(state, KAvatarDefaults.style(size, name), style), contentAlignment = Alignment.Center) {
            if (image != null) Box(Modifier.size(size.diameter).clip(CircleShape)) { image() } else KText(initials(name), maxLines = 1)
        }
        if (status != null) {
            val ring = MaterialTheme.colorScheme.surface
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size.statusDot)
                    .background(ring, CircleShape)
                    .padding(2.dp)
                    .background(KAvatarDefaults.statusColor(status), CircleShape),
            )
        }
    }
}

/** First letters of the first and last word of [name], upper-cased; `?` when there is none. */
public fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when (words.size) {
        0 -> "?"
        1 -> words[0].first().uppercase()
        else -> (words.first().first().toString() + words.last().first()).uppercase()
    }
}

/** Defaults for [KAvatar]. */
public object KAvatarDefaults {
    /** Container and content colours picked from the theme by hashing [name]. */
    @Composable
    public fun colorsFor(name: String): Pair<Color, Color> {
        val c = MaterialTheme.colorScheme
        val k = KompoundTheme.tokens.colors
        val palette = listOf(
            c.primaryContainer to c.onPrimaryContainer,
            c.secondaryContainer to c.onSecondaryContainer,
            c.tertiaryContainer to c.onTertiaryContainer,
            k.infoContainer to k.onInfoContainer,
            k.successContainer to k.onSuccessContainer,
            k.warningContainer to k.onWarningContainer,
        )
        return palette[name.trim().lowercase().hashCode().mod(palette.size)]
    }

    /** Colour of the presence dot. */
    @Composable
    public fun statusColor(status: KAvatarStatus): Color = when (status) {
        KAvatarStatus.Online -> KompoundTheme.tokens.colors.success
        KAvatarStatus.Away -> KompoundTheme.tokens.colors.warning
        KAvatarStatus.Busy -> MaterialTheme.colorScheme.error
        KAvatarStatus.Offline -> MaterialTheme.colorScheme.outline
    }

    /** Base style: circle of [size] filled with the colours derived from [name]. */
    @Composable
    public fun style(size: KAvatarSize = KAvatarSize.Medium, name: String = ""): Style {
        val (container, content) = colorsFor(name)
        val type = MaterialTheme.typography
        return remember(size, container, content, type) {
            Style {
                background(container)
                contentColor(content)
                textStyle((if (size == KAvatarSize.Large) type.titleMedium else type.labelLarge).copy(color = content))
                shape(CircleShape)
                size(size.diameter)
            }
        }
    }
}
