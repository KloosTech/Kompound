package tech.kloos.kompound.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText

/**
 * Syntax-highlighted code in a selectable text field. Read-only by default (the text can be selected and
 * copied); pass [onCodeChange] to make it an editor. The highlighting is a visual transformation, so the
 * caret, selection and IME work as in any text field, and the text itself is never changed.
 *
 * Long lines scroll horizontally. When the caller limits the height (`Modifier.heightIn(max = ...)`) the view
 * scrolls vertically; in an unbounded parent, such as a scrolling column, it shows all lines. Line numbers
 * count logical lines.
 *
 * @param code The text to show.
 * @param modifier Modifier applied to the outermost node.
 * @param onCodeChange Called with the new text on every edit; `null` makes the view read-only.
 * @param language Tokenizer: [KCodeLanguage.Kotlin] (default), [KCodeLanguage.Json], [KCodeLanguage.Shell], [KCodeLanguage.Plain] or your own (see [KCodeLexing]).
 * @param showLineNumbers Show a gutter with line numbers.
 * @param colors Token, background and gutter colours; follows the theme by default, see [KCodeColors.OneDark].
 * @param style Overrides merged over [KCodeDefaults.style].
 * @param focusRequester Request focus for the editor from outside.
 * @param diagnostics Problems to show: each range is underlined and tinted, and the messages are listed under the editor (and announced).
 */
@Composable
public fun KCode(
    code: String,
    modifier: Modifier = Modifier,
    onCodeChange: ((String) -> Unit)? = null,
    language: KCodeLanguage = KCodeLanguage.Kotlin,
    showLineNumbers: Boolean = true,
    colors: KCodeColors = KCodeDefaults.colors(),
    style: Style = Style,
    focusRequester: FocusRequester? = null,
    diagnostics: List<KCodeDiagnostic> = emptyList(),
) {
    KCodeFrame(code, modifier, language, showLineNumbers, colors, style, diagnostics) { textStyle, transformation ->
        BasicTextField(
            value = code,
            onValueChange = { onCodeChange?.invoke(it) },
            modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            readOnly = onCodeChange == null,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.plain),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            visualTransformation = transformation,
        )
    }
}

/**
 * [KCode] as an editor whose caret and selection you can see and change: [value] carries the text, the selection and the IME composition
 * (a `TextFieldValue`), so code outside the editor can insert at the caret or replace the selection (see [TextFieldValue.insertAtCursor])
 * and then move the focus back with [focusRequester].
 *
 * @param value Text and selection.
 * @param onValueChange Called with the new value on every edit and selection change.
 * @param focusRequester Request focus for the editor, for example after inserting text from a button.
 * @param language See [KCode].
 * @param diagnostics See [KCode].
 */
@Composable
public fun KCode(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    language: KCodeLanguage = KCodeLanguage.Kotlin,
    showLineNumbers: Boolean = true,
    colors: KCodeColors = KCodeDefaults.colors(),
    style: Style = Style,
    focusRequester: FocusRequester? = null,
    diagnostics: List<KCodeDiagnostic> = emptyList(),
) {
    KCodeFrame(value.text, modifier, language, showLineNumbers, colors, style, diagnostics) { textStyle, transformation ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.plain),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            visualTransformation = transformation,
        )
    }
}

/** [text] with [inserted] put in place of the selection (at the caret when nothing is selected); the caret ends after it. */
public fun TextFieldValue.insertAtCursor(inserted: String): TextFieldValue {
    val start = minOf(selection.start, selection.end).coerceIn(0, text.length)
    val end = maxOf(selection.start, selection.end).coerceIn(0, text.length)
    return TextFieldValue(text.substring(0, start) + inserted + text.substring(end), TextRange(start + inserted.length))
}

/** The frame shared by both [KCode] overloads: container style, line-number gutter, scrolling, and the field the caller supplies. */
@Composable
private fun KCodeFrame(
    code: String,
    modifier: Modifier,
    language: KCodeLanguage,
    showLineNumbers: Boolean,
    colors: KCodeColors,
    style: Style,
    diagnostics: List<KCodeDiagnostic>,
    field: @Composable (textStyle: TextStyle, transformation: VisualTransformation) -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val styleState = rememberUpdatedStyleState(null) {}
    val textStyle = KCodeDefaults.textStyle(colors)
    val transformation = remember(language, colors, diagnostics) { HighlightTransformation(language, colors, diagnostics) }
    Column(modifier) {
    BoxWithConstraints {
        // Only scroll vertically when the caller bounded the height; inside a scrolling parent the code shows in full.
        val boundedHeight = constraints.hasBoundedHeight
        Row(
            modifier = Modifier.styleable(styleState, KCodeDefaults.style(colors), style),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // One vertical scroll for gutter and text so they stay aligned.
            val vertical = rememberScrollState()
            fun Modifier.scrollsVertically() = if (boundedHeight) verticalScroll(vertical) else this
            if (showLineNumbers) {
                val lines = code.count { it == '\n' } + 1
                val width = lines.toString().length
                KText(
                    (1..lines).joinToString("\n") { it.toString().padStart(width) },
                    Modifier.scrollsVertically(),
                    style = Style { textStyle(textStyle.copy(color = colors.gutter)) },
                )
            }
            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState()).scrollsVertically()) {
                field(textStyle, transformation)
            }
        }
    }
    DiagnosticMessages(code, diagnostics)
    }
}

@Composable
private fun DiagnosticMessages(code: String, diagnostics: List<KCodeDiagnostic>) {
    if (diagnostics.isEmpty()) return
    val color = MaterialTheme.colorScheme.error
    val textStyle = MaterialTheme.typography.labelMedium.copy(color = color)
    val messageStyle = Style { textStyle(textStyle) }
    Column(Modifier.padding(top = 4.dp, start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (d in diagnostics.take(5)) {
            val line = code.substring(0, d.start.coerceIn(0, code.length)).count { it == '\n' } + 1
            val text = "Line $line: ${d.message}"
            KText(text, Modifier.semantics { contentDescription = text }, style = messageStyle)
        }
        if (diagnostics.size > 5) KText("+${diagnostics.size - 5}", style = messageStyle)
    }
}

/**
 * A problem in the code of a [KCode]: the characters from [start] (inclusive) to [end] (exclusive) are underlined and [message] is shown
 * under the editor. An empty or out-of-range span is clamped to the text; use `end = start + 1` to mark a single position.
 */
@Immutable
public data class KCodeDiagnostic(val start: Int, val end: Int, val message: String)

/** Colours of a [KCode]: background, plain text, line-number gutter and one colour per token type. */
@Immutable
public data class KCodeColors(
    val background: Color,
    val plain: Color,
    val gutter: Color,
    val keyword: Color,
    val type: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val annotation: Color,
    val property: Color,
    val punctuation: Color,
    /** Underline tint and message colour of a [KCodeDiagnostic]. */
    val error: Color = Color(0xFFE5484D),
) {
    internal fun of(type: KCodeTokenType): Color = when (type) {
        KCodeTokenType.Keyword -> keyword
        KCodeTokenType.Type -> this.type
        KCodeTokenType.String -> string
        KCodeTokenType.Number -> number
        KCodeTokenType.Comment -> comment
        KCodeTokenType.Function -> function
        KCodeTokenType.Annotation -> annotation
        KCodeTokenType.Property -> property
        KCodeTokenType.Punctuation -> punctuation
    }

    public companion object {
        /** The One Dark editor palette with its own dark background. */
        public val OneDark: KCodeColors = KCodeColors(
            background = Color(0xFF282C34), plain = Color(0xFFABB2BF), gutter = Color(0xFF636D83),
            keyword = Color(0xFFC678DD), type = Color(0xFFE5C07B), string = Color(0xFF98C379), number = Color(0xFFD19A66),
            comment = Color(0xFF5C6370), function = Color(0xFF61AFEF), annotation = Color(0xFFE5C07B),
            property = Color(0xFFE06C75), punctuation = Color(0xFF56B6C2),
        )
    }
}

/** Defaults for [KCode]. */
public object KCodeDefaults {
    /** Monospace 13sp with a fixed 20sp line height, so the line-number gutter lines up with the code. */
    @Composable
    public fun textStyle(colors: KCodeColors = colors()): TextStyle =
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp, color = colors.plain)

    /** Theme-following colours: background, text and gutter come from the scheme, tokens from a light or dark palette. */
    @Composable
    public fun colors(): KCodeColors {
        val c = MaterialTheme.colorScheme
        return remember(c) {
            val dark = c.surfaceContainer.luminance() < 0.5f
            if (dark) {
                KCodeColors(
                    background = c.surfaceContainer, plain = c.onSurface, gutter = c.onSurfaceVariant.copy(alpha = 0.6f),
                    keyword = Color(0xFFD7A5F0), type = Color(0xFFF2CC7B), string = Color(0xFFA5DB8A), number = Color(0xFFF2A06D),
                    comment = c.onSurfaceVariant.copy(alpha = 0.8f), function = Color(0xFF8DB8FF), annotation = Color(0xFFE6B450),
                    property = Color(0xFF7FD6E8), punctuation = c.onSurfaceVariant, error = c.error,
                )
            } else {
                KCodeColors(
                    background = c.surfaceContainer, plain = c.onSurface, gutter = c.onSurfaceVariant.copy(alpha = 0.7f),
                    keyword = Color(0xFF8E24AA), type = Color(0xFF9A5400), string = Color(0xFF276E2B), number = Color(0xFFC2410C),
                    comment = c.onSurfaceVariant, function = Color(0xFF1565C0), annotation = Color(0xFF8A6100),
                    property = Color(0xFF00707F), punctuation = c.onSurfaceVariant, error = c.error,
                )
            }
        }
    }

    /** Container: [KCodeColors.background], 12dp shape from the theme, 1dp `outlineVariant` border, 12dp padding. */
    @Composable
    public fun style(colors: KCodeColors = colors()): Style {
        val scheme = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        return remember(colors, scheme, shapes) {
            Style {
                background(colors.background)
                shape(shapes.medium)
                borderWidth(1.dp)
                borderColor(scheme.outlineVariant)
                contentPadding(12.dp)
                contentColor(colors.plain)
            }
        }
    }
}

/** Colours the tokens of [language] without changing the text, so offsets map one to one. */
private class HighlightTransformation(
    private val language: KCodeLanguage,
    private val colors: KCodeColors,
    private val diagnostics: List<KCodeDiagnostic>,
) : VisualTransformation {
    private var lastCode: String? = null
    private var lastResult: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val code = text.text
        val cached = lastResult
        val result = if (cached != null && code == lastCode) cached else highlight(code, language, colors, diagnostics).also { lastCode = code; lastResult = it }
        return TransformedText(result, OffsetMapping.Identity)
    }
}

internal fun highlight(code: String, language: KCodeLanguage, colors: KCodeColors, diagnostics: List<KCodeDiagnostic> = emptyList()): AnnotatedString = buildAnnotatedString {
    append(code)
    for (token in language.tokenize(code)) {
        if (token.start < 0 || token.end > code.length || token.start >= token.end) continue
        val italic = if (token.type == KCodeTokenType.Comment) FontStyle.Italic else null
        addStyle(SpanStyle(color = colors.of(token.type), fontStyle = italic), token.start, token.end)
    }
    for (d in diagnostics) {
        val start = d.start.coerceIn(0, code.length)
        val end = d.end.coerceIn(start, code.length).let { if (it == start) minOf(code.length, start + 1) else it }
        if (start < end) addStyle(SpanStyle(textDecoration = TextDecoration.Underline, background = colors.error.copy(alpha = 0.25f)), start, end)
    }
}
