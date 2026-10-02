package tech.kloos.kompound.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownBlockTest {
    private fun parse(vararg lines: String) = parseMarkdown(lines.joinToString("\n"))

    @Test
    fun headingsAndParagraphs() {
        val blocks = parse("# Title", "", "Some text", "continues here", "", "###### Small ##")
        assertEquals(
            listOf(MdBlock.Heading(1, "Title"), MdBlock.Paragraph("Some text\ncontinues here"), MdBlock.Heading(6, "Small")),
            blocks,
        )
    }

    @Test
    fun trailingSpacesBeforeALineBreakSurvive() {
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph("a  \nb")), parse("a  ", "b  "))
    }

    @Test
    fun hashWithoutSpaceIsNotAHeading() {
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph("#hashtag")), parse("#hashtag"))
    }

    @Test
    fun setextHeadings() {
        assertEquals(listOf<MdBlock>(MdBlock.Heading(1, "Title")), parse("Title", "====="))
        assertEquals(listOf<MdBlock>(MdBlock.Heading(2, "Sub")), parse("Sub", "---"))
    }

    @Test
    fun rulesNeedThreeCharacters() {
        assertEquals(listOf(MdBlock.Rule, MdBlock.Rule, MdBlock.Rule), parse("---", "", "***", "", "_ _ _"))
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph("--")), parse("--"))
    }

    @Test
    fun fencedCodeKeepsContentAndLanguage() {
        val blocks = parse("```kotlin", "val x = 1", "  # not a heading", "```", "after")
        assertEquals(MdBlock.CodeBlock("kotlin", "val x = 1\n  # not a heading"), blocks[0])
        assertEquals(MdBlock.Paragraph("after"), blocks[1])
    }

    @Test
    fun unclosedFenceRunsToTheEnd() {
        assertEquals(listOf<MdBlock>(MdBlock.CodeBlock("", "a\nb")), parse("```", "a", "b"))
    }

    @Test
    fun tildeFencesAndLongerClosingFences() {
        assertEquals(listOf<MdBlock>(MdBlock.CodeBlock("json", "{}")), parse("~~~json", "{}", "~~~~"))
    }

    @Test
    fun blockquotesNestAndContinueLazily() {
        val blocks = parse("> quoted", "> > deeper", "lazy")
        val quote = blocks.single() as MdBlock.Quote
        assertTrue(quote.blocks.any { it is MdBlock.Quote }, "inner quote missing: $quote")
    }

    @Test
    fun bulletListWithNestedList() {
        val list = parse("- one", "- two", "  - nested", "- three").single() as MdBlock.ListBlock
        assertEquals(false, list.ordered)
        assertEquals(3, list.items.size)
        val nested = list.items[1].blocks.filterIsInstance<MdBlock.ListBlock>().single()
        assertEquals(1, nested.items.size)
    }

    @Test
    fun orderedListKeepsItsStartNumber() {
        val list = parse("3. c", "4. d").single() as MdBlock.ListBlock
        assertEquals(true, list.ordered)
        assertEquals(3, list.start)
        assertEquals(2, list.items.size)
    }

    @Test
    fun taskListItems() {
        val list = parse("- [x] done", "- [ ] todo", "- plain").single() as MdBlock.ListBlock
        assertEquals(listOf(true, false, null), list.items.map { it.checked })
        assertEquals(MdBlock.Paragraph("done"), list.items[0].blocks.single())
    }

    @Test
    fun listsAreInterruptedByOtherBlocksAndBlankLinesBetweenItemsKeepOneList() {
        assertEquals(1, parse("- a", "", "- b").filterIsInstance<MdBlock.ListBlock>().size)
        val blocks = parse("- a", "", "text after")
        assertTrue(blocks[0] is MdBlock.ListBlock && blocks[1] == MdBlock.Paragraph("text after"), "$blocks")
    }

    @Test
    fun aListItemMayContainACodeBlockAndSecondParagraph() {
        val item = (parse("- intro", "", "  more", "", "  ```", "  code", "  ```").single() as MdBlock.ListBlock).items.single()
        assertEquals(3, item.blocks.size)
        assertTrue(item.blocks[2] is MdBlock.CodeBlock)
    }

    @Test
    fun tables() {
        val table = parse("| Name | Qty |", "|:-----|----:|", "| Apple | 3 |", "| Fig |").single() as MdBlock.Table
        assertEquals(listOf("Name", "Qty"), table.header)
        assertEquals(listOf(MdAlign.Start, MdAlign.End), table.aligns)
        assertEquals(listOf(listOf("Apple", "3"), listOf("Fig", "")), table.rows)
    }

    @Test
    fun pipeInAParagraphWithoutSeparatorIsText() {
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph("a | b")), parse("a | b"))
    }

    @Test
    fun windowsLineEndingsAndTabs() {
        assertEquals(listOf(MdBlock.Heading(1, "A"), MdBlock.Paragraph("b")), parseMarkdown("# A\r\n\r\nb"))
    }

    @Test
    fun emptyAndWhitespaceDocuments() {
        assertEquals(emptyList(), parseMarkdown(""))
        assertEquals(emptyList(), parseMarkdown("   \n\n  "))
    }

    @Test
    fun hostileInputNeverThrows() {
        val nasty = listOf("[", "![", "[a](", "**", "`", "```", ">", "-", "1.", "|", "| a |\n|", "\\", "<", "~~", "_ _ _ _", "[[[[[[", "((((((", "*" .repeat(500))
        for (input in nasty) parseMarkdown(input).forEach { block ->
            if (block is MdBlock.Paragraph) parseInline(block.text)
        }
    }
}

class MarkdownInlineTest {
    private fun spans(src: String) = parseInline(src)
    private fun Span.text(src: String) = src.substring(start + open, end - close)

    @Test
    fun boldItalicStrikeAndCode() {
        val src = "a **b** *c* ~~d~~ `e`"
        val s = spans(src)
        assertEquals(listOf(SpanKind.Bold, SpanKind.Italic, SpanKind.Strike, SpanKind.Code), s.map { it.kind })
        assertEquals(listOf("b", "c", "d", "e"), s.map { it.text(src) })
    }

    @Test
    fun emphasisNests() {
        val src = "**bold *and italic* text**"
        val bold = spans(src).single()
        assertEquals(SpanKind.Bold, bold.kind)
        assertEquals(SpanKind.Italic, bold.children.single().kind)
    }

    @Test
    fun tripleMarkersAreBoldAndItalic() {
        val outer = spans("***both***").single()
        assertEquals(SpanKind.Italic, outer.kind)
        assertEquals(SpanKind.Bold, outer.children.single().kind)
    }

    @Test
    fun snakeCaseAndMathStayLiteral() {
        assertTrue(spans("my_snake_case_name").isEmpty())
        assertTrue(spans("2 * 3 * 4").isEmpty())
        assertTrue(spans("a * b").isEmpty())
    }

    @Test
    fun codeSpansAreLiteralInside() {
        val src = "`**not bold**` and ``a ` b``"
        val s = spans(src)
        assertEquals(listOf(SpanKind.Code, SpanKind.Code), s.map { it.kind })
        assertEquals("a ` b", s[1].text(src))
    }

    @Test
    fun escapesProtectMarkers() {
        val src = "\\*not italic\\*"
        assertEquals(listOf(SpanKind.Escape, SpanKind.Escape), spans(src).map { it.kind })
    }

    @Test
    fun linksImagesAndAutolinks() {
        val src = "[Kompound](https://kloos.tech \"title\") ![logo](a.png) <https://x.dev> https://y.dev/path."
        val s = spans(src)
        assertEquals(listOf(SpanKind.Link, SpanKind.Image, SpanKind.Link, SpanKind.Link), s.map { it.kind })
        assertEquals("https://kloos.tech", s[0].url)
        assertEquals("a.png", s[1].url)
        assertEquals("https://x.dev", s[2].url)
        assertEquals("https://y.dev/path", s[3].url)
    }

    @Test
    fun linkLabelsMayContainEmphasis() {
        val link = spans("[**bold** label](u)").single()
        assertEquals(SpanKind.Bold, link.children.single().kind)
    }

    @Test
    fun hardBreaks() {
        assertEquals(SpanKind.HardBreak, spans("a  \nb").single().kind)
        assertEquals(SpanKind.HardBreak, spans("a\\\nb").single().kind)
    }

    @Test
    fun unmatchedMarkersAreLiteral() {
        assertTrue(spans("**open only").isEmpty())
        assertTrue(spans("[text] (not a link)").isEmpty())
        assertTrue(spans("`open").isEmpty())
    }

    @Test
    fun spansStayInsideTheTextAndDoNotOverlapAtTheSameLevel() {
        val src = "x **a *b* c** `d` [e *f*](g) ~~h~~ <i:j> \\* ***k***"
        fun check(list: List<Span>, from: Int, to: Int) {
            var end = from
            for (s in list) {
                assertTrue(s.start >= end && s.end <= to && s.start < s.end, "bad span ${s.kind} ${s.start}..${s.end}")
                check(s.children, s.start + s.open, s.end - s.close)
                end = s.end
            }
        }
        check(spans(src), 0, src.length)
    }
}
