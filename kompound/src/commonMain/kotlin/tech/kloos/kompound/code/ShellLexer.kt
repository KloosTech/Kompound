package tech.kloos.kompound.code

/** Single-pass scanner for POSIX-style shell scripts. */
internal object ShellLexer {
    private val Keywords = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case", "esac", "in", "function", "select", "time",
        "return", "break", "continue", "exit", "export", "local", "readonly", "declare", "unset", "shift", "source", "set", "trap", "eval", "exec",
    )

    /** What may follow a statement separator: the next word is a command. */
    private const val Separators = ";|&(\n{"

    fun tokenize(code: String): List<KCodeToken> {
        val out = ArrayList<KCodeToken>()
        val n = code.length
        var i = 0
        var statementStart = true
        fun add(type: KCodeTokenType, start: Int, end: Int) { out.add(KCodeToken(type, start, end)) }
        while (i < n) {
            val c = code[i]
            when {
                c == '\n' -> { statementStart = true; i++ }
                c == ' ' || c == '\t' || c == '\r' -> i++
                c == '#' && (i == 0 || code[i - 1].isWhitespace() || code[i - 1] in Separators) -> {
                    val end = KCodeLexing.lineEnd(code, i)
                    add(KCodeTokenType.Comment, i, end); i = end
                }
                c == '\'' -> {
                    // single quotes: no escapes, may span lines
                    val close = code.indexOf('\'', i + 1)
                    val end = if (close < 0) n else close + 1
                    add(KCodeTokenType.String, i, end); i = end; statementStart = false
                }
                c == '"' -> {
                    var j = i + 1
                    while (j < n) {
                        if (code[j] == '\\') { j += 2; continue }
                        if (code[j] == '"') { j++; break }
                        j++
                    }
                    val end = j.coerceAtMost(n)
                    add(KCodeTokenType.String, i, end); i = end; statementStart = false
                }
                c == '$' && i + 1 < n -> {
                    val next = code[i + 1]
                    when {
                        next == '{' -> {
                            val close = code.indexOf('}', i + 2)
                            val end = if (close < 0) KCodeLexing.lineEnd(code, i) else close + 1
                            add(KCodeTokenType.Property, i, end); i = end
                        }
                        next == '(' -> { add(KCodeTokenType.Punctuation, i, i + 2); i += 2; statementStart = true; continue }
                        next.isLetter() || next == '_' -> {
                            val end = KCodeLexing.identEnd(code, i + 1)
                            add(KCodeTokenType.Property, i, end); i = end
                        }
                        next.isDigit() || next in "@*#?$!-" -> { add(KCodeTokenType.Property, i, i + 2); i += 2 }
                        else -> i++
                    }
                    statementStart = false
                }
                c.isDigit() -> {
                    val end = KCodeLexing.scanWhile(code, i) { it.isDigit() }
                    // a number only when it is a word of its own (not part of `file2.txt`)
                    if (end >= n || !(code[end].isLetter() || code[end] == '_' || code[end] == '.' || code[end] == '-' || code[end] == '/')) add(KCodeTokenType.Number, i, end)
                    i = end; statementStart = false
                }
                c.isLetter() || c == '_' || c == '.' || c == '/' || c == '~' -> {
                    val end = KCodeLexing.scanWhile(code, i) { it.isLetterOrDigit() || it in "_-./~:@%+,=" }
                    val word = code.substring(i, end)
                    when {
                        word in Keywords -> { add(KCodeTokenType.Keyword, i, end); statementStart = word in setOf("then", "else", "do", "elif", "if", "while", "until", "time", "!") }
                        statementStart && '=' !in word -> { add(KCodeTokenType.Function, i, end); statementStart = false }
                        else -> statementStart = false
                    }
                    i = end
                }
                c in Separators -> {
                    var j = i + 1
                    while (j < n && code[j] in "&|;") j++
                    add(KCodeTokenType.Punctuation, i, j); i = j; statementStart = true
                }
                c in "<>)}[]=!" -> {
                    var j = i + 1
                    while (j < n && code[j] in "<>&=") j++
                    add(KCodeTokenType.Punctuation, i, j); i = j
                    if (c == ')' || c == '}') statementStart = false else if (c == '!') statementStart = true
                }
                c == '-' -> {
                    // options such as -la or --force are plain
                    i = KCodeLexing.scanWhile(code, i + 1) { it.isLetterOrDigit() || it in "-_=" }.coerceAtLeast(i + 1)
                    statementStart = false
                }
                else -> { i++; statementStart = false }
            }
        }
        return out
    }
}
