package tech.kloos.kompound.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Fuzzy matching for search boxes, command palettes and comboboxes: the query's characters must appear in the text in order, not next to each other
 * (`gts` finds "Go to Settings"). Ignores case; starts of words, runs of characters and the start of the text score higher.
 */
public object KFuzzy {
    /** A match: [score] (higher is better; rank by it) and the [indices] of the matched characters of the text, for highlighting. */
    public class Match(public val score: Int, public val indices: List<Int>)

    /** [query] against [text], or `null` when the text does not contain the query's characters in order. A blank query matches everything with score 0. */
    public fun match(query: String, text: String): Match? {
        val q = query.filter { !it.isWhitespace() }
        if (q.isEmpty()) return Match(0, emptyList())
        val n = text.length
        val m = q.length
        if (m > n) return null
        val t = text.lowercase()
        val ql = q.lowercase()
        if (t.length != n) return null   // lowercasing changed the length (rare scripts): no match rather than wrong highlights
        val none = Int.MIN_VALUE / 2
        // best[i][j]: best score matching ql[0..i] with ql[i] on t[j]; from[i][j]: where ql[i-1] sat.
        val best = Array(m) { IntArray(n) { none } }
        val from = Array(m) { IntArray(n) { -1 } }
        for (j in 0 until n) if (t[j] == ql[0]) best[0][j] = charScore(text, j) - minOf(j, 5)
        for (i in 1 until m) for (j in i until n) {
            if (t[j] != ql[i]) continue
            var bestPrev = none
            var bestK = -1
            for (k in i - 1 until j) {
                val prev = best[i - 1][k]
                if (prev == none) continue
                val gap = j - k - 1
                val s = prev + (if (gap == 0) 9 else -minOf(gap, 3))
                if (s > bestPrev) { bestPrev = s; bestK = k }
            }
            if (bestK >= 0) { best[i][j] = bestPrev + charScore(text, j); from[i][j] = bestK }
        }
        var endJ = -1
        var endScore = none
        for (j in 0 until n) if (best[m - 1][j] > endScore) { endScore = best[m - 1][j]; endJ = j }
        if (endJ < 0) return null
        val idx = IntArray(m)
        var j = endJ
        for (i in m - 1 downTo 0) { idx[i] = j; j = from[i][j] }
        return Match(endScore - (n - m) / 8, idx.toList())
    }

    private fun charScore(text: String, j: Int): Int {
        var s = 1
        val boundary = j == 0 || !text[j - 1].isLetterOrDigit() || (text[j].isUpperCase() && text[j - 1].isLowerCase())
        if (boundary) s += 6
        if (j == 0) s += 4
        return s
    }

    /** The items of [items] that match [query], best first (stable for equal scores); everything, unchanged, for a blank query. */
    public fun <T> filter(items: List<T>, query: String, text: (T) -> String): List<T> {
        if (query.isBlank()) return items
        return items.mapNotNull { item -> match(query, text(item))?.let { item to it.score } }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** [text] with the matched characters in [color] and bold, for result lists. */
    public fun highlight(text: String, indices: List<Int>, color: Color): AnnotatedString = buildAnnotatedString {
        append(text)
        val style = SpanStyle(color = color, fontWeight = FontWeight.Bold)
        for (i in indices) if (i in text.indices) addStyle(style, i, i + 1)
    }
}
