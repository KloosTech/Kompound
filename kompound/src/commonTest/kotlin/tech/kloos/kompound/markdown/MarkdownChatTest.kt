package tech.kloos.kompound.markdown

import tech.kloos.kompound.code.KCodeLanguage
import tech.kloos.kompound.code.KCodeTokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** Chat and notes use (found while migrating Agent010): typical LLM output, Obsidian notes, streaming cost. */
class MarkdownChatTest {
    @Test
    fun codeInsideAListItemStaysInTheItem() {
        val b = parseMarkdown("1. Step:\n   ```bash\n   ls\n   ```\n2. Next") as List<MdBlock>
        val list = b.single() as MdBlock.ListBlock
        assertEquals(2, list.items.size)
        assertTrue(list.items[0].blocks[1] is MdBlock.CodeBlock)
    }

    @Test
    fun anUnclosedFenceWhileStreamingIsACodeBlock() {
        assertEquals(MdBlock.CodeBlock("kotlin", "fun main() {"), parseMarkdown("text\n```kotlin\nfun main() {")[1])
    }

    @Test
    fun aTableWhoseDelimiterRowHasTooFewCellsIsStillATable() {
        // Seen in a model reply: 5 header cells, 4 delimiter cells, rows with 4 or 5 cells.
        val md = "| # | Name | Typ | Size | Date |\n|---|------|-----|----------|\n| 1 | a.ipa | 140 MB | 13.10. |\n| 2 | b.3mf | File | 2,55 MB | 16.10. |"
        val table = parseMarkdown(md).single() as MdBlock.Table
        assertEquals(5, table.header.size)
        assertEquals(5, table.aligns.size)
        assertEquals(listOf("1", "a.ipa", "140 MB", "13.10.", ""), table.rows[0])
        assertEquals(listOf("2", "b.3mf", "File", "2,55 MB", "16.10."), table.rows[1])
    }

    @Test
    fun aTableWithMoreDelimiterCellsThanHeaderCellsGetsEmptyHeaderCells() {
        val table = parseMarkdown("| a | b |\n|---|---|---|\n| 1 | 2 | 3 |").single() as MdBlock.Table
        assertEquals(listOf("a", "b", ""), table.header)
        assertEquals(listOf("1", "2", "3"), table.rows[0])
    }

    @Test
    fun frontmatterIsRemoved() {
        assertEquals("# Body", stripFrontmatter("---\ntitle: x\ntags: [a]\n---\n\n# Body"))
        assertEquals("---\nno end", stripFrontmatter("---\nno end"))
        assertEquals("text\n---\nmore", stripFrontmatter("text\n---\nmore"))
    }

    @Test
    fun wikiLinksBecomeLinksOutsideCode() {
        assertEquals("see [alias](wikilink:Other%20Note) and `[[code]]`", preprocessInline("see [[Other Note|alias]] and `[[code]]`", true))
        assertEquals("[Note](wikilink:Note) and *img.png*", preprocessInline("[[Note]] and ![[img.png]]", true))
        assertEquals("[[Note]]", preprocessInline("[[Note]]", false))
        assertEquals("Other Note", KMarkdownDefaults.wikiLinkTarget("wikilink:Other%20Note"))
        assertNull(KMarkdownDefaults.wikiLinkTarget("https://x.y"))
    }

    @Test
    fun brBecomesAHardBreak() {
        assertEquals("a  \nb", preprocessInline("a<br>b", false))
        assertEquals("a  \nb", preprocessInline("a<BR />b", false))
    }

    @Test
    fun fenceTagsMapToLanguages() {
        for (name in listOf("python", "py", "js", "ts", "java", "sql", "yaml", "html", "rust", "go", "bash", "kt")) {
            val tokens = KCodeLanguage.fromName(name).tokenize(
                when (name) { "html" -> "<a href=\"x\">t</a>"; "sql" -> "SELECT * FROM t -- c"; "python", "py", "yaml" -> "# c\nx = 1"; else -> "val x = 1 // c" }
            )
            assertTrue(tokens.isNotEmpty(), name)
        }
        assertTrue(KCodeLanguage.fromName("nonsense").tokenize("x = 1").isEmpty())
        assertEquals(KCodeTokenType.Comment, KCodeLanguage.fromName("python").tokenize("# hello").single().type)
        assertEquals(KCodeTokenType.Keyword, KCodeLanguage.fromName("sql").tokenize("select").single().type)
    }

    @Test
    fun reparsingAGrowingChatReplyIsCheap() {
        val para = "Here is **some** text with `code` and a [link](https://x.y):\n\n- one\n- two\n\n```kotlin\nval x = 1\n```\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n"
        val text = para.repeat(300)
        parseMarkdown(text) // warm up
        val one = measureTime { repeat(5) { parseMarkdown(text) } } / 5
        assertTrue(one.inWholeMilliseconds < 100, "one parse of ${text.length} chars took $one")
    }
}
