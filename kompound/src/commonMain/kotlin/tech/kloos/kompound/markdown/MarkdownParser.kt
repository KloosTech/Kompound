package tech.kloos.kompound.markdown

/**
 * Block-level model of a Markdown document: the subset of CommonMark that documentation, notes and chat need
 * (headings, paragraphs, quotes, nested lists with tasks, fenced code, rules and pipe tables). Text inside the
 * blocks is kept as raw inline source and turned into spans by [parseInline].
 */
internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<MdListItem>) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data object Rule : MdBlock
    data class Table(val header: List<String>, val aligns: List<MdAlign>, val rows: List<List<String>>) : MdBlock
}

internal class MdListItem(val blocks: List<MdBlock>, val checked: Boolean?) {
    override fun equals(other: Any?) = other is MdListItem && blocks == other.blocks && checked == other.checked
    override fun hashCode() = blocks.hashCode() * 31 + (checked?.hashCode() ?: 0)
    override fun toString() = "MdListItem($blocks, checked=$checked)"
}

internal enum class MdAlign { Start, Center, End }

private val FenceRegex = Regex("^ {0,3}(`{3,}|~{3,})[ \\t]*([^`\\s]*).*$")
private val HeadingRegex = Regex("^ {0,3}(#{1,6})(?:[ \\t]+(.*?))?(?:[ \\t]+#+)?[ \\t]*$")
private val RuleRegex = Regex("^ {0,3}([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$")
private val QuoteRegex = Regex("^ {0,3}> ?(.*)$")
private val ListRegex = Regex("^( {0,3})([-+*]|\\d{1,9}[.)])( +|\$)(.*)$")
private val SetextH1 = Regex("^ {0,3}=+[ \\t]*$")
private val SetextH2 = Regex("^ {0,3}-+[ \\t]*$")
private val TableSeparator = Regex("^[ \\t]*\\|?[ \\t]*:?-+:?[ \\t]*(\\|[ \\t]*:?-+:?[ \\t]*)*\\|?[ \\t]*$")
private val TaskRegex = Regex("^\\[([ xX])\\][ \\t]+(.*)$")

/** Parses [markdown] into blocks. Never throws; malformed input degrades to paragraphs. */
internal fun parseMarkdown(markdown: String): List<MdBlock> =
    parseBlocks(markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { it.replace("\t", "    ") })

private fun isBlank(line: String) = line.isBlank()
private fun indentOf(line: String) = line.length - line.trimStart(' ').length

private class Marker(val indent: Int, val ordered: Boolean, val number: Int, val contentOffset: Int, val rest: String)

private fun listMarker(line: String): Marker? {
    val m = ListRegex.matchEntire(line) ?: return null
    val indent = m.groupValues[1].length
    val marker = m.groupValues[2]
    val spaces = m.groupValues[3].length
    val rest = m.groupValues[4]
    // "-" alone, or four or more spaces (an indented code block in CommonMark), count as one space.
    val gap = if (rest.isBlank() || spaces > 4) 1 else spaces
    val ordered = marker.first().isDigit()
    return Marker(indent, ordered, if (ordered) marker.dropLast(1).toInt() else 0, indent + marker.length + gap, if (spaces > 4) " ".repeat(spaces - 1) + rest else rest)
}

private fun startsBlock(line: String): Boolean =
    FenceRegex.matches(line) || HeadingRegex.matches(line) || RuleRegex.matches(line) || QuoteRegex.matches(line)

private fun interruptsParagraph(line: String, next: String?): Boolean {
    if (startsBlock(line)) return true
    val marker = listMarker(line)
    if (marker != null && marker.rest.isNotBlank() && (!marker.ordered || marker.number == 1)) return true
    return line.contains('|') && next != null && isTableSeparator(next) && splitCells(next).size >= 2
}

private fun isTableSeparator(line: String) = line.contains('-') && line.contains('|') && TableSeparator.matches(line)

private fun parseBlocks(lines: List<String>): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (isBlank(line)) { i++; continue }

        val fenceMatch = FenceRegex.matchEntire(line)
        if (fenceMatch != null) {
            val fence = fenceMatch.groupValues[1]
            val body = ArrayList<String>()
            val open = indentOf(line).coerceAtMost(3)
            var j = i + 1
            while (j < lines.size) {
                val l = lines[j]
                val t = l.trim()
                if (t.length >= fence.length && t.all { it == fence[0] }) break
                body.add(if (indentOf(l) >= open) l.substring(open) else l.trimStart())
                j++
            }
            out.add(MdBlock.CodeBlock(fenceMatch.groupValues[2], body.joinToString("\n")))
            i = j + 1
            continue
        }

        val headingMatch = HeadingRegex.matchEntire(line)
        if (headingMatch != null) {
            out.add(MdBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2].trim()))
            i++
            continue
        }

        if (RuleRegex.matches(line)) { out.add(MdBlock.Rule); i++; continue }

        if (QuoteRegex.matches(line)) {
            val inner = ArrayList<String>()
            while (i < lines.size) {
                val q = QuoteRegex.matchEntire(lines[i])
                if (q != null) inner.add(q.groupValues[1])
                else if (!isBlank(lines[i]) && inner.isNotEmpty() && !isBlank(inner.last()) && !startsBlock(lines[i]) && listMarker(lines[i]) == null) inner.add(lines[i])   // lazy continuation
                else break
                i++
            }
            out.add(MdBlock.Quote(parseBlocks(inner)))
            continue
        }

        val marker = listMarker(line)
        if (marker != null) {
            val (block, next) = parseList(lines, i, marker)
            out.add(block)
            i = next
            continue
        }

        if (line.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            val header = splitCells(line)
            val separators = splitCells(lines[i + 1])
            if (header.size == separators.size && header.size >= 1 && separators.size >= 1 && (header.size >= 2 || line.trim().startsWith("|"))) {
                val aligns = separators.map {
                    val t = it.trim()
                    when {
                        t.startsWith(":") && t.endsWith(":") -> MdAlign.Center
                        t.endsWith(":") -> MdAlign.End
                        else -> MdAlign.Start
                    }
                }
                val rows = ArrayList<List<String>>()
                var j = i + 2
                while (j < lines.size && !isBlank(lines[j]) && lines[j].contains('|') && !startsBlock(lines[j])) {
                    val cells = splitCells(lines[j])
                    rows.add(List(header.size) { cells.getOrElse(it) { "" } })
                    j++
                }
                out.add(MdBlock.Table(header, aligns, rows))
                i = j
                continue
            }
        }

        // Paragraph, possibly turned into a setext heading by a following === or --- line.
        val para = ArrayList<String>()
        var heading = 0
        while (i < lines.size) {
            val l = lines[i]
            if (isBlank(l)) break
            if (para.isNotEmpty()) {
                if (SetextH1.matches(l)) { heading = 1; i++; break }
                if (SetextH2.matches(l)) { heading = 2; i++; break }
                if (interruptsParagraph(l, lines.getOrNull(i + 1))) break
            }
            para.add(l.trimStart())    // trailing spaces stay: two of them before a line break are a hard break
            i++
        }
        val text = para.joinToString("\n").trimEnd()
        out.add(if (heading > 0) MdBlock.Heading(heading, text.replace("\n", " ")) else MdBlock.Paragraph(text))
    }
    return out
}

private fun parseList(lines: List<String>, start: Int, first: Marker): Pair<MdBlock.ListBlock, Int> {
    val items = ArrayList<MdListItem>()
    var i = start
    while (i < lines.size) {
        val marker = listMarker(lines[i]) ?: break
        if (marker.ordered != first.ordered || kotlin.math.abs(marker.indent - first.indent) > 3) break
        val body = ArrayList<String>()
        body.add(marker.rest)
        var j = i + 1
        while (j < lines.size) {
            val l = lines[j]
            if (isBlank(l)) {
                var k = j + 1
                while (k < lines.size && isBlank(lines[k])) k++
                if (k < lines.size && indentOf(lines[k]) >= marker.contentOffset) {
                    for (n in j until k) body.add("")
                    j = k
                    continue
                }
                break
            }
            if (indentOf(l) >= marker.contentOffset) {
                body.add(l.substring(marker.contentOffset))
            } else if (body.last().isNotBlank() && listMarker(l) == null && !startsBlock(l)) {
                body.add(l.trim())     // lazy continuation of the item's paragraph
            } else break
            j++
        }
        var checked: Boolean? = null
        TaskRegex.matchEntire(body[0])?.let { t ->
            checked = t.groupValues[1] != " "
            body[0] = t.groupValues[2]
        }
        items.add(MdListItem(parseBlocks(body), checked))
        i = j
        // Blank lines between items keep the list going only when the next item follows.
        var k = i
        while (k < lines.size && isBlank(lines[k])) k++
        val next = if (k < lines.size) listMarker(lines[k]) else null
        if (next != null && next.ordered == first.ordered && kotlin.math.abs(next.indent - first.indent) <= 3) i = k else break
    }
    return MdBlock.ListBlock(first.ordered, first.number, items) to i
}

/** Splits a table row into trimmed cells, honouring `\|` and dropping the outer pipes. */
internal fun splitCells(row: String): List<String> {
    var s = row.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|") && !s.endsWith("\\|")) s = s.dropLast(1)
    val cells = ArrayList<String>()
    val cur = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s[i] == '\\' && i + 1 < s.length && s[i + 1] == '|') { cur.append('|'); i += 2; continue }
        if (s[i] == '|') { cells.add(cur.toString().trim()); cur.clear() } else cur.append(s[i])
        i++
    }
    cells.add(cur.toString().trim())
    return cells
}

// ---------------------------------------------------------------------------------------------------------
// Inline spans

internal enum class SpanKind { Bold, Italic, Strike, Code, Link, Image, Escape, HardBreak }

/**
 * An inline construct inside a source string. [start] until [end] covers the whole construct including its
 * markers: the first [open] and the last [close] characters are markers. Nested constructs are [children].
 */
internal class Span(
    val kind: SpanKind,
    val start: Int,
    val end: Int,
    val open: Int,
    val close: Int,
    val url: String? = null,
    val children: List<Span> = emptyList(),
)

private const val Punctuation = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

/** Finds the inline constructs of `src[from, to)`: emphasis, code, links, images, escapes and hard breaks. */
internal fun parseInline(src: String, from: Int = 0, to: Int = src.length): List<Span> {
    val out = ArrayList<Span>()
    var i = from
    while (i < to) {
        val c = src[i]
        when {
            c == '\\' && i + 1 < to -> {
                val n = src[i + 1]
                when {
                    n == '\n' -> { out.add(Span(SpanKind.HardBreak, i, i + 2, 2, 0)); i += 2 }
                    n in Punctuation -> { out.add(Span(SpanKind.Escape, i, i + 2, 1, 0)); i += 2 }
                    else -> i++
                }
            }
            c == '`' -> {
                var n = 1
                while (i + n < to && src[i + n] == '`') n++
                val close = findBackticks(src, i + n, to, n)
                if (close >= 0) { out.add(Span(SpanKind.Code, i, close + n, n, n)); i = close + n } else i += n
            }
            c == '!' && i + 1 < to && src[i + 1] == '[' -> {
                val link = parseLink(src, i + 1, to, SpanKind.Image, startAt = i)
                if (link != null) { out.add(link); i = link.end } else i++
            }
            c == '[' -> {
                val link = parseLink(src, i, to, SpanKind.Link, startAt = i)
                if (link != null) { out.add(link); i = link.end } else i++
            }
            c == '<' -> {
                val close = src.indexOf('>', i + 1)
                val inner = if (close in (i + 2) until to) src.substring(i + 1, close) else ""
                if (inner.isNotEmpty() && inner.none { it.isWhitespace() || it == '<' } && inner.contains(':') && inner.first().isLetter()) {
                    out.add(Span(SpanKind.Link, i, close + 1, 1, 1, url = inner)); i = close + 1
                } else i++
            }
            c == 'h' && (src.startsWith("http://", i) || src.startsWith("https://", i)) && (i == from || !src[i - 1].isLetterOrDigit()) -> {
                var e = i
                while (e < to && !src[e].isWhitespace() && src[e] != '<' && src[e] != '>') e++
                while (e > i && src[e - 1] in ".,;:!?)'\"") e--
                if (e > i + 8) { out.add(Span(SpanKind.Link, i, e, 0, 0, url = src.substring(i, e))); i = e } else i++
            }
            c == '*' || c == '_' -> {
                val span = parseEmphasis(src, i, to)
                if (span != null) { out.add(span); i = span.end } else {
                    var n = 1
                    while (i + n < to && src[i + n] == c) n++
                    i += n
                }
            }
            c == '~' && i + 1 < to && src[i + 1] == '~' -> {
                val close = findClosing(src, i + 2, to, "~~")
                if (close > i + 2 && !src[i + 2].isWhitespace()) {
                    out.add(Span(SpanKind.Strike, i, close + 2, 2, 2, children = parseInline(src, i + 2, close))); i = close + 2
                } else i += 2
            }
            c == ' ' && src.startsWith("  ", i) -> {
                var e = i
                while (e < to && src[e] == ' ') e++
                if (e < to && src[e] == '\n') { out.add(Span(SpanKind.HardBreak, i, e + 1, e + 1 - i, 0)); i = e + 1 } else i = e
            }
            else -> i++
        }
    }
    return out
}

private fun findBackticks(src: String, from: Int, to: Int, n: Int): Int {
    var i = from
    while (i < to) {
        if (src[i] == '`') {
            var run = 1
            while (i + run < to && src[i + run] == '`') run++
            if (run == n) return i
            i += run
        } else i++
    }
    return -1
}

/** Index of the next [marker] in `src[from, to)` that is not escaped, not inside a code span and not preceded by white space. */
private fun findClosing(src: String, from: Int, to: Int, marker: String): Int {
    var i = from
    while (i < to) {
        val c = src[i]
        when {
            c == '\\' -> i += 2
            c == '`' -> {
                var n = 1
                while (i + n < to && src[i + n] == '`') n++
                val close = findBackticks(src, i + n, to, n)
                i = if (close >= 0) close + n else i + n
            }
            src.startsWith(marker, i) && i > from && !src[i - 1].isWhitespace() -> return i
            else -> i++
        }
    }
    return -1
}

private fun parseEmphasis(src: String, i: Int, to: Int): Span? {
    val d = src[i]
    var run = 1
    while (i + run < to && src[i + run] == d) run++
    val after = if (i + run < to) src[i + run] else ' '
    val before = if (i > 0) src[i - 1] else ' '
    if (after.isWhitespace()) return null
    if (d == '_' && before.isLetterOrDigit()) return null     // snake_case stays literal
    val len = if (run >= 3) 3 else run
    val marker = d.toString().repeat(len)
    var close = findClosing(src, i + len, to, marker)
    while (close >= 0) {
        val next = if (close + len < to) src[close + len] else ' '
        val followedByMore = close + len < to && src[close + len] == d && len < 3
        val intraword = d == '_' && next.isLetterOrDigit()
        if (!followedByMore && !intraword && close > i + len) break
        close = findClosing(src, close + 1, to, marker)
    }
    if (close < 0) return null
    val end = close + len
    return when (len) {
        3 -> Span(SpanKind.Italic, i, end, 1, 1, children = listOf(Span(SpanKind.Bold, i + 1, end - 1, 2, 2, children = parseInline(src, i + 3, close))))
        2 -> Span(SpanKind.Bold, i, end, 2, 2, children = parseInline(src, i + 2, close))
        else -> Span(SpanKind.Italic, i, end, 1, 1, children = parseInline(src, i + 1, close))
    }
}

/** `[label](url "title")` starting at the `[` at [bracket]; [startAt] is where the whole construct (`!`) begins. */
private fun parseLink(src: String, bracket: Int, to: Int, kind: SpanKind, startAt: Int): Span? {
    var depth = 0
    var i = bracket
    var labelEnd = -1
    while (i < to) {
        when (src[i]) {
            '\\' -> i++
            '`' -> {
                var n = 1
                while (i + n < to && src[i + n] == '`') n++
                val close = findBackticks(src, i + n, to, n)
                if (close >= 0) i = close + n - 1
            }
            '[' -> depth++
            ']' -> { depth--; if (depth == 0) { labelEnd = i; break } }
        }
        i++
    }
    if (labelEnd < 0 || labelEnd + 1 >= to || src[labelEnd + 1] != '(') return null
    var parens = 0
    var j = labelEnd + 1
    var destEnd = -1
    while (j < to) {
        when (src[j]) {
            '\\' -> j++
            '(' -> parens++
            ')' -> { parens--; if (parens == 0) { destEnd = j; break } }
        }
        j++
    }
    if (destEnd < 0) return null
    var dest = src.substring(labelEnd + 2, destEnd).trim()
    dest.indexOfFirst { it.isWhitespace() }.takeIf { it > 0 }?.let { dest = dest.substring(0, it) }
    if (dest.startsWith("<") && dest.endsWith(">") && dest.length >= 2) dest = dest.substring(1, dest.length - 1)
    val open = bracket + 1 - startAt
    return Span(kind, startAt, destEnd + 1, open, destEnd + 1 - labelEnd, url = dest, children = parseInline(src, bracket + 1, labelEnd))
}
