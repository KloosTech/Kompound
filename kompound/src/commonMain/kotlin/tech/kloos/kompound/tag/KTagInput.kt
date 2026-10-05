package tech.kloos.kompound.tag

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.chip.KChip
import tech.kloos.kompound.combobox.OptionRow
import tech.kloos.kompound.combobox.OptionsPopup
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.search.KFuzzy
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.ErrorKey
import tech.kloos.kompound.textfield.KTextFieldDefaults
import tech.kloos.kompound.theme.KompoundTheme

/**
 * A field that turns what you type into removable tags (e-mail recipients, labels, keywords). Enter or a separator character (comma by
 * default) ends the tag; Backspace in the empty text removes the last one; clicking a tag's chip removes it; leaving the field keeps what
 * is typed as a tag. Pasting `a, b, c` makes three tags. Optional [suggestions] appear under the field as you type (fuzzy, Down and Up to
 * move, Enter to pick).
 *
 * The tags are yours ([tags], [onTagsChange]). Empty text, duplicates (unless [allowDuplicates]) and anything over [maxTags] are ignored;
 * [validate] can refuse a tag with a message that is shown under the field (and announced) until the next edit.
 *
 * @param tags The current tags, in order.
 * @param onTagsChange Called with the new list.
 * @param label Label above the field.
 * @param placeholder Hint while there are no tags and no text.
 * @param supportingText Help or error message below the field; a message from [validate] replaces it for a moment.
 * @param isError Marks the field invalid.
 * @param enabled When false tags cannot be added or removed.
 * @param suggestions Suggested tags (those already added are left out).
 * @param separators Characters that end a tag when typed or pasted.
 * @param allowDuplicates Allow the same tag twice (compared ignoring case).
 * @param maxTags The most tags that can be added.
 * @param validate Returns a message for a tag that must not be added, or `null`.
 * @param style Overrides merged over the field container style.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KTagInput(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    suggestions: List<String> = emptyList(),
    separators: String = ",",
    allowDuplicates: Boolean = false,
    maxTags: Int = Int.MAX_VALUE,
    validate: (String) -> String? = { null },
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val strings = KompoundTheme.strings
    val source = remember { MutableInteractionSource() }
    var text by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var active by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val shownError = problem ?: supportingText
    val failed = isError || problem != null
    val state = rememberUpdatedStyleState(source) { it.isEnabled = enabled; it.set(ErrorKey, failed) }
    val scheme = MaterialTheme.colorScheme

    // Commits [raw] as a tag against the list [base]; returns the new list (same list when nothing was added).
    fun add(base: List<String>, raw: String): List<String> {
        val tag = raw.trim()
        if (tag.isEmpty() || base.size >= maxTags) return base
        if (!allowDuplicates && base.any { it.equals(tag, ignoreCase = true) }) return base
        val refusal = validate(tag)
        if (refusal != null) { problem = refusal; return base }
        problem = null
        return base + tag
    }
    fun commit(raw: String) {
        val next = add(tags, raw)
        if (next !== tags) onTagsChange(next)
    }
    val matches = remember(suggestions, tags, text) {
        if (text.isBlank()) emptyList() else KFuzzy.filter(suggestions.filter { s -> tags.none { it.equals(s, ignoreCase = true) } }, text) { it }.take(8)
    }
    val open = enabled && focused && !dismissed && matches.isNotEmpty()
    val activeIndex = active.coerceIn(0, (matches.size - 1).coerceAtLeast(0))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) KText(label, style = KTextFieldDefaults.labelStyle(failed, enabled))
        Box(Modifier.onSizeChanged { widthPx = it.width }) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(enabled) { if (enabled) detectTapGestures { focusRequester.requestFocus() } }
                    .styleable(state, KTextFieldDefaults.style(), style)
                    .semantics { if (label != null) contentDescription = label },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                tags.forEachIndexed { i, tag ->
                    KChip(
                        tag, { if (enabled) onTagsChange(tags.filterIndexed { j, _ -> j != i }) },
                        Modifier.semantics { contentDescription = strings.removeNamed(tag) },
                        enabled = enabled,
                        trailing = { KIcon(KompoundIcons.Close, contentDescription = null) },
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { new ->
                        problem = null; dismissed = false; active = 0
                        if (separators.any { it in new }) {
                            val parts = new.split(*separators.toCharArray())
                            var next = tags
                            for (part in parts.dropLast(1)) next = add(next, part)
                            if (next !== tags) onTagsChange(next)
                            text = parts.last()
                        } else text = new
                    },
                    modifier = Modifier
                        .widthIn(min = 96.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            focused = it.isFocused
                            if (!it.isFocused && text.isNotBlank()) { commit(text); text = "" }
                        }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown || !enabled) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.Enter, Key.NumPadEnter -> {
                                    val picked = if (open) matches[activeIndex] else text
                                    if (picked.isNotBlank()) { commit(picked); text = ""; true } else false
                                }
                                Key.Backspace -> if (text.isEmpty() && tags.isNotEmpty()) { onTagsChange(tags.dropLast(1)); true } else false
                                Key.DirectionDown -> if (matches.isNotEmpty()) { if (!open) dismissed = false else active = (activeIndex + 1).coerceAtMost(matches.lastIndex); true } else false
                                Key.DirectionUp -> if (open) { active = (activeIndex - 1).coerceAtLeast(0); true } else false
                                Key.Escape -> if (open) { dismissed = true; true } else false
                                else -> false
                            }
                        }
                        .semantics { if (failed) error(shownError ?: "Invalid input") },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = KTextFieldDefaults.textStyle(enabled),
                    cursorBrush = SolidColor(if (failed) scheme.error else scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { commit(text); text = "" } }),
                    interactionSource = source,
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty() && tags.isEmpty() && placeholder != null) {
                                KText(placeholder, style = KTextFieldDefaults.placeholderStyle(enabled), maxLines = 1)
                            }
                            inner()
                        }
                    },
                )
            }
            if (open) {
                OptionsPopup(
                    rows = matches.map { OptionRow(it) }, active = activeIndex,
                    width = with(LocalDensity.current) { widthPx.toDp() }, emptyText = null,
                    onPick = { commit(matches[it]); text = "" }, onDismiss = { dismissed = true },
                )
            }
        }
        if (shownError != null) {
            KText(shownError, Modifier.fillMaxWidth().padding(horizontal = 16.dp), style = KTextFieldDefaults.supportingStyle(failed, enabled))
        }
    }
}
