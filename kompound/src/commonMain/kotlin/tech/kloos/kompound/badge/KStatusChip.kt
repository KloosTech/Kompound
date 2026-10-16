package tech.kloos.kompound.badge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.progress.KCircularProgress
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Live status label: a spinner (while [busy]) or a dot, and a phase label such as "Loading", "Prefilling", "Generating".
 * When [label] changes the text cross-fades and the chip glides to its new width, so a streaming status does not jump.
 *
 * @param label Current phase. Used as the accessibility description.
 * @param modifier Modifier applied to the chip.
 * @param tone Meaning, picks the colours ([KBadgeTone.Info] by default).
 * @param busy Show a spinner (work in progress) instead of the status dot.
 * @param emphasis [KBadgeEmphasis.Subtle] (default) or [KBadgeEmphasis.Strong].
 * @param style Overrides merged over [KStatusChipDefaults.style].
 */
@Composable
public fun KStatusChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: KBadgeTone = KBadgeTone.Info,
    busy: Boolean = true,
    emphasis: KBadgeEmphasis = KBadgeEmphasis.Subtle,
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val content = KBadgeDefaults.colors(tone, emphasis).second
    val motion = KompoundTheme.tokens.motion
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = label }
            .styleable(state, KStatusChipDefaults.style(tone, emphasis), style)
            .animateContentSize(tween(motion.durationMedium)),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalKContentColor provides content) {
            if (busy) {
                KCircularProgress(null, size = 12.dp, strokeWidth = 2.dp, color = content, trackColor = androidx.compose.ui.graphics.Color.Transparent)
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(content))
            }
            AnimatedContent(
                targetState = label,
                transitionSpec = { fadeIn(tween(motion.durationMedium)) togetherWith fadeOut(tween(motion.durationShort)) },
                label = "KStatusChip",
            ) { KText(it, maxLines = 1) }
        }
    }
}

/** Defaults for [KStatusChip]. */
public object KStatusChipDefaults {
    /** Base style: [KBadgeDefaults.style] pill with 10dp side padding and a 24dp minimum height. */
    @Composable
    public fun style(tone: KBadgeTone = KBadgeTone.Info, emphasis: KBadgeEmphasis = KBadgeEmphasis.Subtle): Style {
        val base = KBadgeDefaults.style(tone, emphasis, KBadgeShape.Pill)
        return remember(base) { Style(base, Style { contentPadding(horizontal = 10.dp, vertical = 2.dp); minHeight(24.dp) }) }
    }
}
