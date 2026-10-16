package tech.kloos.kompound.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.theme.KompoundTheme

private val LocalSettingsQuery = compositionLocalOf { "" }
private val LocalSettingsLabel = compositionLocalOf<String?> { null }

/**
 * Wraps a settings screen so its [KSettingsRow]s can be filtered by [query]: a row whose label, supporting text or keywords do not contain
 * the query (ignoring case) is not shown, and a [KSettingsGroup] with no visible row hides its title too.
 */
@Composable
public fun KSettingsFilter(query: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSettingsQuery provides query.trim()) {
        Column(modifier.fillMaxWidth()) { content() }
    }
}

/**
 * A titled section of settings rows. Hides completely (title too) when a surrounding [KSettingsFilter] filters out all its rows.
 *
 * @param title Section heading.
 * @param modifier Modifier applied to the group.
 * @param style Overrides merged over [KSettingsDefaults.groupStyle] (the card around the rows).
 */
@Composable
public fun KSettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    style: Style = Style,
    content: @Composable () -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    // Subcomposed so a group without visible rows does not even compose its title (no stale node for screen readers).
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val loose = constraints.copy(minHeight = 0)
        val body = subcompose("body") {
            Column(Modifier.fillMaxWidth().styleable(state, KSettingsDefaults.groupStyle(), style)) { content() }
        }.first().measure(loose)
        // Filtered rows emit nothing, so an empty card is only as tall as its border (a few px).
        if (body.height <= 4.dp.roundToPx()) return@SubcomposeLayout layout(0, 0) {}
        val header = subcompose("title") {
            KText(title, Modifier.padding(horizontal = 4.dp, vertical = 4.dp), style = KSettingsDefaults.titleStyle())
        }.first().measure(loose)
        val gap = 16.dp.roundToPx()
        layout(maxOf(header.width, body.width), header.height + body.height + gap) {
            header.placeRelative(0, 0)
            body.placeRelative(0, header.height)
        }
    }
}

/**
 * One setting: [label] with optional [supporting] help on the left and the [control] on the right.
 *
 * @param label Name of the setting.
 * @param modifier Modifier applied to the row.
 * @param supporting Help text under the label.
 * @param keywords Extra words the [KSettingsFilter] matches besides label and supporting text.
 * @param saved Shows a "saved" check after the control while true; flip it for a moment after a value was stored (see [KSettingsTextField]).
 * @param savedText Text of the saved indicator, also read by screen readers.
 * @param control The editing control: a switch, dropdown, text field...
 */
@Composable
public fun KSettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    keywords: List<String> = emptyList(),
    saved: Boolean = false,
    savedText: String = "Saved",
    control: @Composable () -> Unit,
) {
    val query = LocalSettingsQuery.current
    if (query.isNotEmpty() && !matches(query, label, supporting, keywords)) return
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KText(label)
            if (supporting != null) KText(supporting, style = KSettingsDefaults.supportingStyle())
        }
        CompositionLocalProvider(LocalSettingsLabel provides label) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(saved) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    KIcon(KompoundIcons.Check, contentDescription = null, tint = KompoundTheme.tokens.colors.success, modifier = Modifier.padding(0.dp))
                    KText(savedText, style = KSettingsDefaults.savedStyle())
                }
            }
            control()
        } }
    }
}

/**
 * Text field that stores its value on Enter or when it loses focus and then shows a "saved" mark next to it: the auto-save field of a settings screen.
 * Escape drops the edit.
 *
 * @param value The stored value.
 * @param onCommit Called with the new text when it is committed and differs from [value].
 * @param savedText Text of the indicator.
 * @param savedMillis How long the indicator stays.
 * @param contentDescription Accessibility name; defaults to the label of the surrounding [KSettingsRow].
 */
@Composable
public fun KSettingsTextField(
    value: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    savedText: String = "Saved",
    savedMillis: Long = 1500,
    contentDescription: String? = LocalSettingsLabel.current,
) {
    var draft by remember(value) { mutableStateOf(value) }
    var saved by remember { mutableStateOf(false) }
    var stamp by remember { mutableStateOf(0) }
    fun commit() {
        if (draft != value) { onCommit(draft); saved = true; stamp++ }
    }
    LaunchedEffect(stamp) { if (stamp > 0) { delay(savedMillis); saved = false } }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        KTextField(
            draft, { draft = it },
            Modifier.widthIn(min = 160.dp)
                .onFocusChanged { if (!it.isFocused) commit() }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Enter -> { commit(); true }
                        Key.Escape -> { draft = value; true }
                        else -> false
                    }
                },
            placeholder = placeholder, enabled = enabled, singleLine = singleLine, contentDescription = contentDescription,
        )
        AnimatedVisibility(saved) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                KIcon(KompoundIcons.Check, contentDescription = null, tint = KompoundTheme.tokens.colors.success)
                KText(savedText, style = KSettingsDefaults.savedStyle())
            }
        }
    }
}

private fun matches(query: String, label: String, supporting: String?, keywords: List<String>): Boolean =
    label.contains(query, ignoreCase = true) || supporting?.contains(query, ignoreCase = true) == true ||
        keywords.any { it.contains(query, ignoreCase = true) }

/** Defaults for the settings components. */
public object KSettingsDefaults {
    /** The card around a group's rows: `surfaceContainerLow` with an `outlineVariant` border and the medium shape. */
    @Composable
    public fun groupStyle(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        return remember(c, shapes) {
            Style { background(c.surfaceContainerLow); shape(shapes.medium); borderWidth(1.dp); borderColor(c.outlineVariant) }
        }
    }

    /** Group title: `titleSmall` in `onSurfaceVariant`. */
    @Composable
    public fun titleStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { Style { contentColor(c.onSurfaceVariant); textStyle(type.titleSmall.copy(color = c.onSurfaceVariant)) } }
    }

    /** Help text under a label: `bodySmall` in `onSurfaceVariant`. */
    @Composable
    public fun supportingStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { Style { contentColor(c.onSurfaceVariant); textStyle(type.bodySmall.copy(color = c.onSurfaceVariant)) } }
    }

    /** The "saved" text: `labelSmall` in the success colour. */
    @Composable
    public fun savedStyle(): Style {
        val success = KompoundTheme.tokens.colors.success
        val type = MaterialTheme.typography
        return remember(success, type) { Style { contentColor(success); textStyle(type.labelSmall.copy(color = success)) } }
    }
}
