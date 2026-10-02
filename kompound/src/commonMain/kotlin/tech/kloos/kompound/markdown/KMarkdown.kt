package tech.kloos.kompound.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.code.KCode
import tech.kloos.kompound.code.KCodeLanguage
import tech.kloos.kompound.divider.KDivider
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText

/**
 * Renders a Markdown document: headings, paragraphs with **bold**, *italic*, ~~strikethrough~~, `code` and
 * links, nested and numbered lists, task lists, quotes, fenced code blocks (highlighted by [KCode] for
 * `kotlin` and `json`), rules and pipe tables. Images are shown as their alt text.
 *
 * The text can be selected across blocks. Parsing is cached per [markdown] value, so updating it while
 * streaming (a chat reply, an editor preview) only re-parses what changed in the string, not on recomposition.
 * It is a pragmatic CommonMark subset, not a full implementation: no HTML, footnotes or reference links.
 *
 * @param markdown The document source.
 * @param modifier Modifier applied to the outermost node.
 * @param onLinkClick Called with the URL of a tapped link; by default the platform opens it.
 * @param selectable Whether text can be selected and copied.
 * @param style Overrides merged over [KMarkdownDefaults.style] (container look: padding, background, ...).
 * @param textStyle Body text style; headings and quotes derive from the theme's typography.
 */
@Composable
public fun KMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    selectable: Boolean = true,
    style: Style = Style,
    textStyle: TextStyle = KMarkdownDefaults.textStyle(),
) {
    remember { KompoundStyles.ensureEnabled() }
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val uriHandler = LocalUriHandler.current
    val handler: (String) -> Unit = onLinkClick ?: { url -> runCatching { uriHandler.openUri(url) } }
    val currentHandler by rememberUpdatedState(handler)
    val look = KMarkdownDefaults.look()
    val context = remember(look, textStyle) { MdContext(look, textStyle) { url -> currentHandler(url) } }
    val state = remember { MutableStyleState(null) }
    val content = @Composable {
        Column(
            modifier = modifier.styleable(state, KMarkdownDefaults.style(), style),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MdBlocks(blocks, context, depth = 0)
        }
    }
    if (selectable) SelectionContainer { content() } else content()
}

/** Defaults for [KMarkdown] and [KMarkdownField]. */
public object KMarkdownDefaults {
    /** Container style: content colour for icons and other non-text content. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        return remember(c) { Style { contentColor(c.onSurface) } }
    }

    /** Body text: `bodyLarge` in `onSurface`. */
    @Composable
    public fun textStyle(): TextStyle {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { type.bodyLarge.copy(color = c.onSurface) }
    }

    /** Text style of a heading of [level] (1 to 6): the theme's headline and title styles in semi-bold. */
    @Composable
    public fun headingTextStyle(level: Int): TextStyle {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type, level) {
            val base = when (level) {
                1 -> type.headlineMedium
                2 -> type.headlineSmall
                3 -> type.titleLarge
                4 -> type.titleMedium
                5 -> type.titleSmall
                else -> type.labelLarge
            }
            base.copy(fontWeight = FontWeight.SemiBold, color = c.onSurface)
        }
    }

    @Composable
    internal fun look(): InlineLook {
        val c = MaterialTheme.colorScheme
        return remember(c) { InlineLook(link = c.primary, codeBackground = c.surfaceContainerHighest, code = c.onSurface, marker = c.onSurfaceVariant.copy(alpha = 0.7f)) }
    }
}

@Immutable
internal class InlineLook(val link: Color, val codeBackground: Color, val code: Color, val marker: Color)

@Immutable
internal class MdContext(val look: InlineLook, val textStyle: TextStyle, val onLink: (String) -> Unit) {
    fun inline(src: String): AnnotatedString = renderInline(src, look, onLink)
}

// ---------------------------------------------------------------------------------------------------------
// Blocks

@Composable
private fun MdBlocks(blocks: List<MdBlock>, ctx: MdContext, depth: Int) {
    val scheme = MaterialTheme.colorScheme
    blocks.forEach { block ->
        when (block) {
            is MdBlock.Heading -> {
                val text = remember(block.text, ctx) { ctx.inline(block.text) }
                KText(text, textStyle = KMarkdownDefaults.headingTextStyle(block.level))
            }
            is MdBlock.Paragraph -> {
                val text = remember(block.text, ctx) { ctx.inline(block.text) }
                KText(text, textStyle = ctx.textStyle)
            }
            is MdBlock.CodeBlock -> KCode(
                code = block.code,
                modifier = Modifier.fillMaxWidth(),
                language = languageOf(block.language),
                showLineNumbers = false,
            )
            MdBlock.Rule -> KDivider(Modifier.padding(vertical = 4.dp))
            is MdBlock.Quote -> {
                val quoteCtx = remember(ctx, scheme) { MdContext(ctx.look, ctx.textStyle.copy(color = scheme.onSurfaceVariant), ctx.onLink) }
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.fillMaxHeight().width(3.dp).clip(RoundedCornerShape(2.dp)).background(scheme.outlineVariant))
                    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MdBlocks(block.blocks, quoteCtx, depth + 1)
                    }
                }
            }
            is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val numberWidth = (block.start + block.items.size).toString().length * 9 + 14
                block.items.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            item.checked != null -> TaskBox(item.checked, Modifier.padding(top = 3.dp))
                            block.ordered -> KText(AnnotatedString("${block.start + index}."), Modifier.widthIn(min = numberWidth.dp), textStyle = ctx.textStyle)
                            else -> KText(AnnotatedString(Bullets[depth % Bullets.size]), Modifier.widthIn(min = 14.dp), textStyle = ctx.textStyle)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { MdBlocks(item.blocks, ctx, depth + 1) }
                    }
                }
            }
            is MdBlock.Table -> MdTable(block, ctx)
        }
    }
}

private val Bullets = listOf("•", "◦", "▪")

@Composable
private fun TaskBox(checked: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .size(18.dp)
            .clip(shape)
            .then(if (checked) Modifier.background(c.primary) else Modifier.border(2.dp, c.outline, shape))
            .semantics { stateDescription = if (checked) "Done" else "Not done" },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) KIcon(KompoundIcons.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun MdTable(table: MdBlock.Table, ctx: MdContext) {
    val c = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val columns = table.header.size
    fun cell(source: String, align: MdAlign, bold: Boolean): AnnotatedString {
        val inline = ctx.inline(source)
        val textAlign = when (align) { MdAlign.Start -> TextAlign.Start; MdAlign.Center -> TextAlign.Center; MdAlign.End -> TextAlign.End }
        return buildAnnotatedString {
            withStyle(ParagraphStyle(textAlign = textAlign)) {
                if (bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inline) } else append(inline)
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, c.outlineVariant, shape)) {
        Row(Modifier.fillMaxWidth().background(c.surfaceContainer)) {
            for (col in 0 until columns) KText(remember(table, ctx) { cell(table.header[col], table.aligns[col], true) }, Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp), textStyle = ctx.textStyle)
        }
        table.rows.forEach { row ->
            KDivider()
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until columns) KText(remember(row, ctx) { cell(row[col], table.aligns[col], false) }, Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp), textStyle = ctx.textStyle)
            }
        }
    }
}

private fun languageOf(info: String): KCodeLanguage = when (info.lowercase()) {
    "kotlin", "kt", "kts" -> KCodeLanguage.Kotlin
    "json" -> KCodeLanguage.Json
    else -> KCodeLanguage.Plain
}

// ---------------------------------------------------------------------------------------------------------
// Inline rendering

internal fun renderInline(src: String, look: InlineLook, onLink: (String) -> Unit): AnnotatedString =
    buildAnnotatedString { renderSpans(src, 0, src.length, parseInline(src), look, onLink) }

private fun AnnotatedString.Builder.appendPlain(src: String, from: Int, to: Int) {
    // A soft line break is a space; hard breaks were consumed as spans.
    for (i in from until to) append(if (src[i] == '\n') ' ' else src[i])
}

private fun AnnotatedString.Builder.renderSpans(src: String, from: Int, to: Int, spans: List<Span>, look: InlineLook, onLink: (String) -> Unit) {
    var p = from
    for (span in spans) {
        appendPlain(src, p, span.start)
        renderSpan(src, span, look, onLink)
        p = span.end
    }
    appendPlain(src, p, to)
}

private fun AnnotatedString.Builder.renderSpan(src: String, span: Span, look: InlineLook, onLink: (String) -> Unit) {
    val innerStart = span.start + span.open
    val innerEnd = span.end - span.close
    when (span.kind) {
        SpanKind.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { renderSpans(src, innerStart, innerEnd, span.children, look, onLink) }
        SpanKind.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { renderSpans(src, innerStart, innerEnd, span.children, look, onLink) }
        SpanKind.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { renderSpans(src, innerStart, innerEnd, span.children, look, onLink) }
        SpanKind.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = look.codeBackground, color = look.code)) {
            append(' '); appendPlain(src, innerStart, innerEnd); append(' ')
        }
        SpanKind.Escape -> append(src[span.start + 1])
        SpanKind.HardBreak -> append('\n')
        SpanKind.Image -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { renderSpans(src, innerStart, labelEnd(span), span.children, look, onLink) }
        SpanKind.Link -> {
            val url = span.url.orEmpty()
            val link = LinkAnnotation.Clickable(
                tag = url,
                styles = TextLinkStyles(SpanStyle(color = look.link, textDecoration = TextDecoration.Underline)),
                linkInteractionListener = { onLink(url) },
            )
            withLink(link) {
                if (span.children.isEmpty() && span.close <= 1) appendPlain(src, innerStart, innerEnd.coerceAtLeast(innerStart))
                else renderSpans(src, innerStart, labelEnd(span), span.children, look, onLink)
            }
        }
    }
}

/** End of a link or image label: the `](url)` tail is [Span.close] characters long. */
private fun labelEnd(span: Span): Int = span.end - span.close
