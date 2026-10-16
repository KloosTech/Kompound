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

        /**
         * The built-in language for a Markdown fence tag or file type (case-insensitive), or [Plain] when unknown: `kotlin`/`kt`/`kts`, `json`,
         * `bash`/`sh`/`shell`/`zsh`, `java`, `javascript`/`js`/`jsx`, `typescript`/`ts`/`tsx`, `swift`, `c`, `cpp`/`c++`, `cs`/`csharp`, `go`, `rust`/`rs`,
         * `groovy`, `dart`, `python`/`py`, `yaml`/`yml`, `toml`, `sql`, `xml`/`html`.
         */
        public fun fromName(name: String): KCodeLanguage = when (name.trim().lowercase()) {
            "kotlin", "kt", "kts" -> Kotlin
            "json", "jsonc" -> Json
            "bash", "sh", "shell", "zsh", "console" -> Shell
            "java" -> cLike(JavaKeywords)
            "javascript", "js", "jsx", "mjs" -> cLike(JsKeywords)
            "typescript", "ts", "tsx" -> cLike(JsKeywords + TsKeywords)
            "swift" -> cLike(SwiftKeywords)
            "c", "h" -> cLike(CKeywords)
            "cpp", "c++", "cc", "hpp" -> cLike(CKeywords + CppKeywords)
            "cs", "csharp" -> cLike(CSharpKeywords)
            "go", "golang" -> cLike(GoKeywords)
            "rust", "rs" -> cLike(RustKeywords)
            "groovy", "gradle" -> cLike(JavaKeywords + setOf("def", "it"))
            "dart" -> cLike(JavaKeywords + setOf("late", "final", "async", "await", "var", "required"))
            "python", "py" -> KCodeLanguage { code -> CLikeLexer(PythonKeywords, lineComments = listOf("#"), blockComments = false).tokenize(code) }
            "yaml", "yml", "toml", "ini" -> KCodeLanguage { code -> CLikeLexer(setOf("true", "false", "null", "yes", "no"), lineComments = listOf("#"), blockComments = false).tokenize(code) }
            "sql" -> KCodeLanguage { code -> CLikeLexer(SqlKeywords, lineComments = listOf("--"), ignoreCase = true).tokenize(code) }
            "xml", "html", "svg", "xhtml" -> KCodeLanguage { code -> MarkupLexer.tokenize(code) }
            else -> Plain
        }
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
internal class CLikeLexer(
    private val keywords: Set<String>,
    private val lineComments: List<String> = listOf("//"),
    private val blockComments: Boolean = true,
    private val ignoreCase: Boolean = false,
) {
    fun tokenize(code: String): List<KCodeToken> {
        val out = ArrayList<KCodeToken>()
        var i = 0
        val n = code.length
        fun add(type: KCodeTokenType, start: Int, end: Int) { out.add(KCodeToken(type, start, end)) }
        while (i < n) {
            val c = code[i]
            when {
                c.isWhitespace() -> i++
                lineComments.any { code.startsWith(it, i) } -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                    add(KCodeTokenType.Comment, i, end); i = end
                }
                blockComments && code.startsWith("/*", i) -> {
                    val close = code.indexOf("*/", i + 2)
                    val end = if (close < 0) n else close + 2
                    add(KCodeTokenType.Comment, i, end); i = end
                }
                code.startsWith("\"\"\"", i) || code.startsWith("'''", i) -> {
                    val q = code.substring(i, i + 3)
                    val close = code.indexOf(q, i + 3)
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
                        (if (ignoreCase) word.lowercase() else word) in keywords -> KCodeTokenType.Keyword
                        c.isUpperCase() -> KCodeTokenType.Type
                        k < n && code[k] == '(' -> KCodeTokenType.Function
                        else -> null
                    }
                    if (type != null) add(type, i, j)
                    i = j
                }
                c in Punctuation -> {
                    var j = i + 1
                    while (j < n && code[j] in Punctuation && lineComments.none { code.startsWith(it, j) } && !(blockComments && code.startsWith("/*", j))) j++
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

private val JavaKeywords = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while", "var", "record", "null", "true", "false", "sealed", "permits", "yield",
)
private val JsKeywords = setOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "export", "extends",
    "finally", "for", "from", "function", "if", "import", "in", "instanceof", "let", "new", "null", "of", "return", "static", "super", "switch", "this",
    "throw", "try", "typeof", "undefined", "var", "void", "while", "with", "yield", "true", "false",
)
private val TsKeywords = setOf(
    "interface", "type", "enum", "implements", "namespace", "declare", "abstract", "readonly", "private", "public", "protected", "as", "keyof", "is",
    "string", "number", "boolean", "any", "unknown", "never",
)
private val SwiftKeywords = setOf(
    "associatedtype", "class", "deinit", "enum", "extension", "fileprivate", "func", "import", "init", "inout", "internal", "let", "open", "operator",
    "private", "protocol", "public", "rethrows", "static", "struct", "subscript", "typealias", "var", "break", "case", "continue", "default", "defer", "do",
    "else", "fallthrough", "for", "guard", "if", "in", "repeat", "return", "switch", "where", "while", "as", "catch", "false", "is", "nil", "self", "super",
    "throw", "throws", "true", "try", "async", "await", "some", "any",
)
private val CKeywords = setOf(
    "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int",
    "long", "register", "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
    "NULL", "true", "false", "bool",
)
private val CppKeywords = setOf(
    "class", "namespace", "template", "typename", "public", "private", "protected", "virtual", "override", "new", "delete", "this", "using", "nullptr",
    "constexpr", "noexcept", "try", "catch", "throw", "operator", "friend", "explicit", "mutable", "auto",
)
private val CSharpKeywords = setOf(
    "abstract", "as", "async", "await", "base", "bool", "break", "byte", "case", "catch", "char", "class", "const", "continue", "decimal", "default",
    "delegate", "do", "double", "else", "enum", "event", "explicit", "extern", "false", "finally", "fixed", "float", "for", "foreach", "if", "implicit", "in",
    "int", "interface", "internal", "is", "lock", "long", "namespace", "new", "null", "object", "operator", "out", "override", "params", "private",
    "protected", "public", "readonly", "ref", "return", "sealed", "short", "static", "string", "struct", "switch", "this", "throw", "true", "try", "typeof",
    "using", "var", "virtual", "void", "volatile", "while", "record", "init", "get", "set",
)
private val GoKeywords = setOf(
    "break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map",
    "package", "range", "return", "select", "struct", "switch", "type", "var", "nil", "true", "false", "string", "int", "bool", "error", "byte",
)
private val RustKeywords = setOf(
    "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop",
    "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super", "trait", "true", "type", "unsafe", "use", "where", "while",
)
private val PythonKeywords = setOf(
    "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else", "except", "finally", "for",
    "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield", "self", "print",
)
private val SqlKeywords = setOf(
    "select", "from", "where", "and", "or", "not", "insert", "into", "values", "update", "set", "delete", "create", "table", "drop", "alter", "add", "index",
    "join", "inner", "left", "right", "outer", "full", "cross", "on", "as", "group", "by", "order", "having", "limit", "offset", "distinct", "union", "all",
    "null", "is", "in", "like", "between", "exists", "case", "when", "then", "else", "end", "primary", "key", "foreign", "references", "default", "unique",
    "with", "asc", "desc", "count", "sum", "avg", "min", "max", "true", "false", "int", "integer", "text", "varchar", "boolean", "date", "timestamp",
)

/** Tags, attributes, quoted values and comments of XML and HTML. */
internal object MarkupLexer {
    fun tokenize(code: String): List<KCodeToken> {
        val out = ArrayList<KCodeToken>()
        var i = 0
        val n = code.length
        while (i < n) {
            when {
                code.startsWith("<!--", i) -> {
                    val close = code.indexOf("-->", i + 4)
                    val end = if (close < 0) n else close + 3
                    out.add(KCodeToken(KCodeTokenType.Comment, i, end)); i = end
                }
                code[i] == '<' -> {
                    var j = i + 1
                    if (j < n && (code[j] == '/' || code[j] == '?' || code[j] == '!')) j++
                    val nameEnd = KCodeLexing.scanWhile(code, j) { it.isLetterOrDigit() || it == '-' || it == '_' || it == ':' || it == '.' }
                    out.add(KCodeToken(KCodeTokenType.Punctuation, i, j))
                    if (nameEnd > j) out.add(KCodeToken(KCodeTokenType.Keyword, j, nameEnd))
                    i = nameEnd
                    while (i < n && code[i] != '>') {
                        val c = code[i]
                        when {
                            c == '"' || c == '\'' -> { val e = quotedEnd(code, i, c); out.add(KCodeToken(KCodeTokenType.String, i, e)); i = e }
                            c.isLetter() || c == '_' || c == ':' || c == '@' -> {
                                val e = KCodeLexing.scanWhile(code, i) { it.isLetterOrDigit() || it == '-' || it == '_' || it == ':' || it == '.' || it == '@' }
                                out.add(KCodeToken(KCodeTokenType.Property, i, e)); i = e
                            }
                            else -> i++
                        }
                    }
                    if (i < n) { out.add(KCodeToken(KCodeTokenType.Punctuation, i, i + 1)); i++ }
                }
                else -> i++
            }
        }
        return out
    }
}
