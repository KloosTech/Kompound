package tech.kloos.kompound.code

/** Kind of a highlighted span. Anything not covered by a token is drawn in the plain code colour. */
public enum class KCodeTokenType { Keyword, Type, String, Number, Comment, Function, Annotation, Property, Punctuation }

/**
 * A highlighted range of the code.
 *
 * @property type What the range is.
 * @property start First character index.
 * @property end Index after the last character (exclusive).
 */
public data class KCodeToken(val type: KCodeTokenType, val start: Int, val end: Int)

/**
 * Splits code into [KCodeToken]s. Implement it to add a language; tokens must stay inside the text, must not
 * overlap and should be sorted by [KCodeToken.start]. It runs on every edit, so keep it a single pass.
 */
public fun interface KCodeLanguage {
    public fun tokenize(code: String): List<KCodeToken>

    public companion object {
        /** Kotlin: keywords, types, functions, annotations, strings (also raw strings), numbers, comments. */
        public val Kotlin: KCodeLanguage = KCodeLanguage { code -> CLikeLexer(KotlinKeywords).tokenize(code) }

        /** JSON: keys as properties, strings, numbers, `true`/`false`/`null`. */
        public val Json: KCodeLanguage = KCodeLanguage { code -> JsonLexer.tokenize(code) }

        /**
         * Shell (sh, bash, zsh): comments, `'single'` and `"double"` quoted strings, `$VARIABLES`, `${braced}` and `$(`...`)` starts, keywords
         * (`if`, `then`, `for`, `do`, `done`, `case`, ...), numbers, the command that starts each statement as a function, and operators.
         */
        public val Shell: KCodeLanguage = KCodeLanguage { code -> ShellLexer.tokenize(code) }

        /** No highlighting: the code is drawn in the plain colour. */
        public val Plain: KCodeLanguage = KCodeLanguage { emptyList() }

        /**
         * A language for the C family (`//` and `/* */` comments, `"..."` and `'...'` strings, `@annotations`, numbers, identifiers that are
         * [keywords], types as capitalised words, functions as words followed by `(`). It is what [Kotlin] is built from: pass your own keyword
         * set for Java, Swift, Dart, TypeScript and similar languages.
         */
        public fun cLike(keywords: Set<String>): KCodeLanguage = KCodeLanguage { code -> CLikeLexer(keywords).tokenize(code) }
    }
}

/**
 * Helpers for writing a [KCodeLanguage] as one pass over the text: the same scanning steps the built-in languages use.
 */
public object KCodeLexing {
    /** End (exclusive) of the quoted literal that starts at [start]; stops at the closing [quote], a newline or the end. Backslash escapes the next character. */
    public fun quotedEnd(code: String, start: Int, quote: Char): Int = quotedEndImpl(code, start, quote)

    /** Index after the last character from [start] that satisfies [part] (at least [start] itself is not required to match). */
    public fun scanWhile(code: String, start: Int, part: (Char) -> Boolean): Int {
        var i = start
        while (i < code.length && part(code[i])) i++
        return i
    }

    /** End of the identifier starting at [start] (letters, digits and `_`). */
    public fun identEnd(code: String, start: Int): Int = scanWhile(code, start) { it == '_' || it.isLetterOrDigit() }

    /** Index of the end of the line containing [from] (the `\n`, or the end of the text). */
    public fun lineEnd(code: String, from: Int): Int = code.indexOf('\n', from).let { if (it < 0) code.length else it }
}

private val KotlinKeywords = setOf(
    "package", "import", "class", "interface", "object", "fun", "val", "var", "typealias", "if", "else", "when", "for",
    "while", "do", "return", "break", "continue", "throw", "try", "catch", "finally", "is", "in", "as", "null", "true",
    "false", "this", "super", "public", "private", "protected", "internal", "open", "abstract", "final", "override",
    "sealed", "data", "enum", "annotation", "companion", "const", "lateinit", "suspend", "inline", "noinline",
    "crossinline", "reified", "operator", "infix", "tailrec", "vararg", "by", "init", "constructor", "get", "set",
    "where", "out", "expect", "actual", "value", "typeof",
)

private fun Char.isIdentStart() = this == '_' || this.isLetter()
private fun Char.isIdentPart() = this == '_' || this.isLetterOrDigit()

/** Single-pass scanner for C-like languages with `//` and `/* */` comments. */
internal class CLikeLexer(private val keywords: Set<String>) {
    fun tokenize(code: String): List<KCodeToken> {
        val out = ArrayList<KCodeToken>()
        var i = 0
        val n = code.length
        fun add(type: KCodeTokenType, start: Int, end: Int) { out.add(KCodeToken(type, start, end)) }
        while (i < n) {
            val c = code[i]
            when {
                c.isWhitespace() -> i++
                code.startsWith("//", i) -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                    add(KCodeTokenType.Comment, i, end); i = end
                }
                code.startsWith("/*", i) -> {
                    val close = code.indexOf("*/", i + 2)
                    val end = if (close < 0) n else close + 2
                    add(KCodeTokenType.Comment, i, end); i = end
                }
                code.startsWith("\"\"\"", i) -> {
                    val close = code.indexOf("\"\"\"", i + 3)
                    val end = if (close < 0) n else close + 3
                    add(KCodeTokenType.String, i, end); i = end
                }
                c == '"' || c == '\'' -> {
                    val end = quotedEnd(code, i, c)
                    add(KCodeTokenType.String, i, end); i = end
                }
                c == '@' && i + 1 < n && code[i + 1].isIdentStart() -> {
                    var j = i + 1
                    while (j < n && code[j].isIdentPart()) j++
                    add(KCodeTokenType.Annotation, i, j); i = j
                }
                c.isDigit() -> {
                    var j = i + 1
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || (code[j] == '.' && j + 1 < n && code[j + 1].isDigit()))) j++
                    add(KCodeTokenType.Number, i, j); i = j
                }
                c.isIdentStart() -> {
                    var j = i + 1
                    while (j < n && code[j].isIdentPart()) j++
                    val word = code.substring(i, j)
                    var k = j
                    while (k < n && (code[k] == ' ' || code[k] == '\t')) k++
                    val type = when {
                        word in keywords -> KCodeTokenType.Keyword
                        c.isUpperCase() -> KCodeTokenType.Type
                        k < n && code[k] == '(' -> KCodeTokenType.Function
                        else -> null
                    }
                    if (type != null) add(type, i, j)
                    i = j
                }
                c in Punctuation -> {
                    var j = i + 1
                    while (j < n && code[j] in Punctuation && !code.startsWith("//", j) && !code.startsWith("/*", j)) j++
                    add(KCodeTokenType.Punctuation, i, j); i = j
                }
                else -> i++
            }
        }
        return out
    }

    private companion object {
        const val Punctuation = "{}()[]<>.,;:=+-*/%!&|?~^"
    }
}

/** End (exclusive) of the quoted literal that starts at [start]; stops at the closing quote, a newline or the end. */
internal fun quotedEnd(code: String, start: Int, quote: Char): Int = quotedEndImpl(code, start, quote)

private fun quotedEndImpl(code: String, start: Int, quote: Char): Int {
    var j = start + 1
    while (j < code.length) {
        val ch = code[j]
        if (ch == '\\') { j += 2; continue }
        if (ch == quote) return j + 1
        if (ch == '\n') return j
        j++
    }
    return code.length
}

internal object JsonLexer {
    fun tokenize(code: String): List<KCodeToken> {
        val out = ArrayList<KCodeToken>()
        var i = 0
        val n = code.length
        while (i < n) {
            val c = code[i]
            when {
                c == '"' -> {
                    val end = quotedEnd(code, i, '"').coerceAtMost(n)
                    var k = end
                    while (k < n && code[k].isWhitespace()) k++
                    out.add(KCodeToken(if (k < n && code[k] == ':') KCodeTokenType.Property else KCodeTokenType.String, i, end))
                    i = end
                }
                c.isDigit() || (c == '-' && i + 1 < n && code[i + 1].isDigit()) -> {
                    var j = i + 1
                    while (j < n && (code[j].isDigit() || code[j] in ".eE+-")) j++
                    out.add(KCodeToken(KCodeTokenType.Number, i, j)); i = j
                }
                c.isLetter() -> {
                    var j = i + 1
                    while (j < n && code[j].isLetter()) j++
                    if (code.substring(i, j) in setOf("true", "false", "null")) out.add(KCodeToken(KCodeTokenType.Keyword, i, j))
                    i = j
                }
                c in "{}[],:" -> { out.add(KCodeToken(KCodeTokenType.Punctuation, i, i + 1)); i++ }
                else -> i++
            }
        }
        return out
    }
}
