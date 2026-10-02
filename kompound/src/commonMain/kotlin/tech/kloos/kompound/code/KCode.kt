package tech.kloos.kompound.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
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
 * Long lines scroll horizontally and the view scrolls vertically when the caller limits its height
 * (`Modifier.heightIn(max = ...)`). Line numbers count logical lines.
 *
 * @param code The text to show.
 * @param modifier Modifier applied to the outermost node.
 * @param onCodeChange Called with the new text on every edit; `null` makes the view read-only.
 * @param language Tokenizer: [KCodeLanguage.Kotlin] (default), [KCodeLanguage.Json], [KCodeLanguage.Plain] or your own.
 * @param showLineNumbers Show a gutter with line numbers.
 * @param colors Token, background and gutter colours; follows the theme by default, see [KCodeColors.OneDark].
 * @param style Overrides merged over [KCodeDefaults.style].
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
) {
    remember { KompoundStyles.ensureEnabled() }
    val styleState = rememberUpdatedStyleState(null) {}
    val textStyle = KCodeDefaults.textStyle(colors)
    val transformation = remember(language, colors) { HighlightTransformation(language, colors) }
    Row(
        modifier = modifier.styleable(styleState, KCodeDefaults.style(colors), style),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // One vertical scroll for gutter and text so they stay aligned.
        val vertical = rememberScrollState()
        if (showLineNumbers) {
            val lines = code.count { it == '\n' } + 1
            val width = lines.toString().length
            KText(
                (1..lines).joinToString("\n") { it.toString().padStart(width) },
                Modifier.verticalScroll(vertical),
                style = Style { textStyle(textStyle.copy(color = colors.gutter)) },
            )
        }
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState()).verticalScroll(vertical)) {
            BasicTextField(
                value = code,
                onValueChange = { onCodeChange?.invoke(it) },
                readOnly = onCodeChange == null,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.plain),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                visualTransformation = transformation,
            )
        }
    }
}

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
                    property = Color(0xFF7FD6E8), punctuation = c.onSurfaceVariant,
                )
            } else {
                KCodeColors(
                    background = c.surfaceContainer, plain = c.onSurface, gutter = c.onSurfaceVariant.copy(alpha = 0.7f),
                    keyword = Color(0xFF8E24AA), type = Color(0xFF9A5400), string = Color(0xFF276E2B), number = Color(0xFFC2410C),
                    comment = c.onSurfaceVariant, function = Color(0xFF1565C0), annotation = Color(0xFF8A6100),
                    property = Color(0xFF00707F), punctuation = c.onSurfaceVariant,
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
) : VisualTransformation {
    private var lastCode: String? = null
    private var lastResult: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val code = text.text
        val cached = lastResult
        val result = if (cached != null && code == lastCode) cached else highlight(code, language, colors).also { lastCode = code; lastResult = it }
        return TransformedText(result, OffsetMapping.Identity)
    }
}

internal fun highlight(code: String, language: KCodeLanguage, colors: KCodeColors): AnnotatedString = buildAnnotatedString {
    append(code)
    for (token in language.tokenize(code)) {
        if (token.start < 0 || token.end > code.length || token.start >= token.end) continue
        val italic = if (token.type == KCodeTokenType.Comment) FontStyle.Italic else null
        addStyle(SpanStyle(color = colors.of(token.type), fontStyle = italic), token.start, token.end)
    }
}
