package tech.kloos.kompound.markdown

import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import tech.kloos.kompound.textfield.KTextField

/**
 * Text field for writing Markdown. The source stays exactly as typed, with the markers dimmed, while headings,
 * **bold**, *italic*, ~~strikethrough~~, `code`, links, quotes, list markers and fenced code are styled as you
 * type. Show the finished result next to it with [KMarkdown].
 *
 * Styling is per line, so emphasis that spans several lines is not styled.
 *
 * @param value The Markdown source.
 * @param onValueChange Called with the new source on every edit.
 * @param modifier Modifier applied to the outermost node.
 * @param label Label shown above the field.
 * @param placeholder Hint shown while the field is empty.
 * @param supportingText Text under the field; replaced by the error text when [isError].
 * @param isError Draws the field in its error state.
 * @param enabled When false the field ignores input.
 * @param readOnly Selectable but not editable.
 * @param minLines Height of the empty field in lines.
 * @param maxLines Lines before the field scrolls.
 * @param style Overrides merged over the field's default style.
 */
@Composable
public fun KMarkdownField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    minLines: Int = 4,
    maxLines: Int = 12,
    style: Style = Style,
) {
    val look = KMarkdownDefaults.look()
    val transformation = remember(look) { MarkdownSourceTransformation(look) }
    KTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = false,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = transformation,
        style = style,
    )
}

/** Styles the Markdown source without changing it, so offsets map one to one. */
internal class MarkdownSourceTransformation(private val look: InlineLook) : VisualTransformation {
    private var lastSource: String? = null
    private var lastResult: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val cached = lastResult
        val result = if (cached != null && source == lastSource) cached else styleMarkdownSource(source, look).also { lastSource = source; lastResult = it }
        return TransformedText(result, OffsetMapping.Identity)
    }
}

private val HeadingSizes = listOf(26.sp, 22.sp, 19.sp, 17.sp, 16.sp, 16.sp)

internal fun styleMarkdownSource(source: String, look: InlineLook): AnnotatedString = buildAnnotatedString {
    append(source)
    val marker = SpanStyle(color = look.marker)
    val mono = SpanStyle(fontFamily = FontFamily.Monospace, background = look.codeBackground, color = look.code)
    var offset = 0
    var fence: String? = null
    for (line in source.split('\n')) {
        val end = offset + line.length
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
        when {
            fence != null -> {
                if (trimmed.startsWith(fence) && trimmed.all { it == fence!![0] }) { addStyle(marker, offset, end); fence = null }
                else addStyle(mono, offset, end)
            }
            trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                fence = trimmed.takeWhile { it == trimmed[0] }
                addStyle(marker, offset, end)
            }
            else -> styleLine(line, offset, indent, marker, look)
        }
        offset = end + 1
    }
}

private fun AnnotatedString.Builder.styleLine(line: String, offset: Int, indent: Int, marker: SpanStyle, look: InlineLook) {
    val trimmed = line.substring(indent)
    var contentStart = indent
    val hashes = trimmed.takeWhile { it == '#' }.length
    when {
        hashes in 1..6 && (trimmed.length == hashes || trimmed[hashes] == ' ') -> {
            addStyle(marker, offset + indent, offset + indent + hashes)
            addStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = HeadingSizes[hashes - 1]), offset + indent + hashes, offset + line.length)
            contentStart = indent + hashes
        }
        trimmed.startsWith(">") -> {
            val bar = if (trimmed.startsWith("> ")) 2 else 1
            addStyle(marker, offset + indent, offset + indent + bar)
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), offset + indent + bar, offset + line.length)
            contentStart = indent + bar
        }
        Regex("^([-*+]|\\d{1,9}[.)])( +|$)").find(trimmed) != null -> {
            val m = Regex("^([-*+]|\\d{1,9}[.)])").find(trimmed)!!
            addStyle(marker, offset + indent, offset + indent + m.value.length)
            contentStart = indent + m.value.length
        }
        Regex("^([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$").matches(trimmed) -> addStyle(marker, offset, offset + line.length)
    }
    val spans = parseInline(line, contentStart, line.length)
    styleSpans(spans, offset, marker, look)
}

private fun AnnotatedString.Builder.styleSpans(spans: List<Span>, offset: Int, marker: SpanStyle, look: InlineLook) {
    for (span in spans) {
        val start = offset + span.start
        val end = offset + span.end
        val innerStart = start + span.open
        val innerEnd = end - span.close
        when (span.kind) {
            SpanKind.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            SpanKind.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            SpanKind.Strike -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            SpanKind.Code -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = look.codeBackground, color = look.code), start, end)
            SpanKind.Link, SpanKind.Image -> addStyle(SpanStyle(color = look.link, textDecoration = TextDecoration.Underline), innerStart, innerEnd.coerceAtLeast(innerStart))
            SpanKind.Escape, SpanKind.HardBreak -> {}
        }
        // Markers, and for links the `](url)` tail, are dimmed.
        if (span.open > 0) addStyle(marker, start, innerStart)
        if (span.close > 0) addStyle(marker, innerEnd, end)
        if (span.kind == SpanKind.Escape) addStyle(marker, start, start + 1)
        styleSpans(span.children, offset, marker, look)
    }
}
