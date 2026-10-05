package tech.kloos.kompound.search

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KFuzzyTest {
    @Test
    fun matchesCharactersInOrderIgnoringCase() {
        assertNotNull(KFuzzy.match("gts", "Go to Settings"))
        assertNotNull(KFuzzy.match("GTS", "go to settings"))
        assertNull(KFuzzy.match("stg", "Go to Settings".take(5)))
        assertNull(KFuzzy.match("zz", "Go to Settings"))
        assertNull(KFuzzy.match("abcd", "abc"))
    }

    @Test
    fun blankQueryMatchesEverything() {
        assertEquals(0, KFuzzy.match("", "x")!!.score)
        assertEquals(emptyList(), KFuzzy.match("  ", "x")!!.indices)
    }

    @Test
    fun wordStartsAndRunsScoreHigher() {
        val start = KFuzzy.match("set", "Settings")!!.score
        val middle = KFuzzy.match("set", "Reset all")!!.score
        assertTrue(start > middle, "$start vs $middle")
        val run = KFuzzy.match("abc", "abc xyz")!!.score
        val spread = KFuzzy.match("abc", "a-b-c")!!.score
        assertTrue(run > spread, "$run vs $spread")
    }

    @Test
    fun indicesPointAtTheMatchedCharacters() {
        assertEquals(listOf(0, 3, 6), KFuzzy.match("gts", "Go to Settings")!!.indices)
        val m = KFuzzy.match("ope", "Open file")!!
        assertEquals(listOf(0, 1, 2), m.indices)
    }

    @Test
    fun filterRanksBestFirstAndKeepsOrderForBlankQueries() {
        val items = listOf("Reset all", "Settings", "Set theme")
        assertEquals(items, KFuzzy.filter(items, "") { it })
        val found = KFuzzy.filter(items, "set") { it }
        assertEquals("Reset all", found.last(), "a match in the middle of a word ranks last")
        assertEquals(3, found.size)
    }

    @Test
    fun highlightMarksOnlyTheMatchedCharacters() {
        val text = KFuzzy.highlight("Open", listOf(0, 2), Color.Red)
        assertEquals(listOf(0 to 1, 2 to 3), text.spanStyles.map { it.start to it.end })
    }
}
