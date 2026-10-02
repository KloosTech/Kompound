package tech.kloos.kompound.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkdownInlineRenderTest {
    private val look = InlineLook(link = Color.Blue, codeBackground = Color.LightGray, code = Color.Black, marker = Color.Gray)
    private fun render(src: String) = renderInline(src, look) {}

    @Test
    fun markersAreRemovedAndStylesApplied() {
        val text = render("a **bold** *it* ~~gone~~ `code`")
        assertEquals("a bold it gone  code ", text.text)
        fun styleAt(word: String) = text.spanStyles.filter { it.start <= text.text.indexOf(word) && it.end > text.text.indexOf(word) }.map { it.item }
        assertTrue(styleAt("bold").any { it.fontWeight == FontWeight.Bold })
        assertTrue(styleAt("it").any { it.fontStyle == FontStyle.Italic })
        assertTrue(styleAt("gone").any { it.textDecoration == TextDecoration.LineThrough })
        assertTrue(styleAt("code").any { it.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun escapesAndSoftBreaks() {
        assertEquals("*literal* and one line", render("\\*literal\\* and one\nline").text)
        assertEquals("a\nb", render("a  \nb").text)
        assertEquals("a\nb", render("a\\\nb").text)
    }

    @Test
    fun linksKeepTheirUrlAndShowOnlyTheLabel() {
        val text = render("see [the docs](https://kloos.tech) or <https://x.dev>")
        assertEquals("see the docs or https://x.dev", text.text)
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals(listOf("https://kloos.tech", "https://x.dev"), links.map { (it.item as androidx.compose.ui.text.LinkAnnotation.Clickable).tag })
    }

    @Test
    fun imagesShowTheirAltText() {
        assertEquals("a logo here", render("a ![logo](x.png) here").text)
    }

    @Test
    fun unmatchedMarkersStayLiteral() {
        assertEquals("**open and `tick", render("**open and `tick").text)
    }
}

@OptIn(ExperimentalTestApi::class)
class KMarkdownTest {
    private val scheme = lightColorScheme()

    private fun androidx.compose.ui.test.ComposeUiTest.show(md: String, onLink: (String) -> Unit = {}) =
        setContent { MaterialTheme(scheme) { KMarkdown(md, Modifier.testTag("md"), onLinkClick = onLink) } }

    @Test
    fun headingsAndParagraphsRenderWithoutMarkers() = runComposeUiTest {
        show("# Title\n\nHello **bold** world")
        onNodeWithText("Title").assertExists()
        onNodeWithText("Hello bold world").assertExists()
        assertEquals(0, onAllNodesWithText("**", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithText("# ", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aHeadingIsTallerThanBodyText() = runComposeUiTest {
        show("# Big\n\nsmall")
        val big = onNodeWithText("Big").fetchSemanticsNode().size.height
        val small = onNodeWithText("small").fetchSemanticsNode().size.height
        assertTrue(big > small, "heading $big vs body $small")
    }

    @Test
    fun bulletAndOrderedListsShowTheirMarkers() = runComposeUiTest {
        show("- apple\n- pear\n\n3. three\n4. four")
        assertEquals(2, onAllNodesWithText("•").fetchSemanticsNodes().size)
        onNodeWithText("3.").assertExists()
        onNodeWithText("4.").assertExists()
        onNodeWithText("apple").assertExists()
    }

    @Test
    fun nestedListsUseAnotherBullet() = runComposeUiTest {
        show("- outer\n  - inner")
        onNodeWithText("•").assertExists()
        onNodeWithText("◦").assertExists()
    }

    @Test
    fun taskItemsExposeTheirState() = runComposeUiTest {
        show("- [x] done\n- [ ] todo")
        fun described(state: String) = SemanticsMatcher("state $state") { it.config.getOrNull(SemanticsProperties.StateDescription) == state }
        assertEquals(1, onAllNodes(described("Done")).fetchSemanticsNodes().size)
        assertEquals(1, onAllNodes(described("Not done")).fetchSemanticsNodes().size)
    }

    @Test
    fun fencedCodeIsShownAsCode() = runComposeUiTest {
        show("```kotlin\nval answer = 42\n```")
        val shown = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
        assertEquals(listOf("val answer = 42"), shown)
    }

    @Test
    fun tablesShowHeaderAndCells() = runComposeUiTest {
        show("| Name | Qty |\n|---|---:|\n| Apple | 3 |")
        listOf("Name", "Qty", "Apple", "3").forEach { onNodeWithText(it).assertExists() }
    }

    @Test
    fun quotesAndRulesRender() = runComposeUiTest {
        show("> wise words\n\n---\n\nafter")
        onNodeWithText("wise words").assertExists()
        onNodeWithText("after").assertExists()
    }

    @Test
    fun tappingALinkReportsItsUrl() = runComposeUiTest {
        var clicked: String? = null
        show("[Go](https://kloos.tech) now") { clicked = it }
        onNodeWithText("Go now").performTouchInput { click(Offset(6f, 8f)) }
        waitForIdle()
        assertEquals("https://kloos.tech", clicked)
    }

    @Test
    fun updatingTheMarkdownReplacesTheContent() = runComposeUiTest {
        var md by mutableStateOf("# One")
        setContent { MaterialTheme(scheme) { KMarkdown(md) } }
        onNodeWithText("One").assertExists()
        md = "# Two\n\n- item"
        waitForIdle()
        onNodeWithText("Two").assertExists()
        onNodeWithText("One").assertDoesNotExist()
    }

    @Test
    fun emptyAndHostileDocumentsDoNotCrash() = runComposeUiTest {
        var md by mutableStateOf("")
        setContent { MaterialTheme(scheme) { KMarkdown(md, selectable = false) } }
        for (input in listOf("", "   ", "**", "[", "| a |\n|", "```", "> > > >", "- - - -", "1.", "#")) {
            md = input
            waitForIdle()
        }
    }
}

@OptIn(ExperimentalTestApi::class)
class KMarkdownFieldTest {
    private val scheme = lightColorScheme()
    private val look = InlineLook(link = Color.Blue, codeBackground = Color.LightGray, code = Color.Black, marker = Color.Gray)

    @Test
    fun stylingNeverChangesTheSource() {
        val source = "# Title\n> quote **b**\n- item `c`\n```kotlin\nval x = *1\n```\n[a](b) ~~s~~"
        assertEquals(source, styleMarkdownSource(source, look).text)
        assertEquals("", styleMarkdownSource("", look).text)
    }

    @Test
    fun markersAreDimmedAndContentIsStyled() {
        val source = "## Head and **bold**"
        val styled = styleMarkdownSource(source, look)
        fun at(i: Int) = styled.spanStyles.filter { it.start <= i && it.end > i }.map { it.item }
        assertTrue(at(0).any { it.color == look.marker }, "# marker not dimmed")
        assertTrue(at(source.indexOf("Head")).any { it.fontWeight == FontWeight.SemiBold })
        assertTrue(at(source.indexOf("bold")).any { it.fontWeight == FontWeight.Bold })
        assertTrue(at(source.indexOf("**")).any { it.color == look.marker }, "** marker not dimmed")
    }

    @Test
    fun codeInsideFencesIsNotInterpretedAsMarkdown() {
        val source = "```\n# not heading **x**\n```"
        val styled = styleMarkdownSource(source, look)
        val inside = source.indexOf("not")
        assertTrue(styled.spanStyles.none { it.start <= inside && it.end > inside && it.item.fontWeight != null })
    }

    @Test
    fun typingCallsBackWithTheNewSource() = runComposeUiTest {
        var text by mutableStateOf("# ")
        setContent { MaterialTheme(scheme) { KMarkdownField(text, { text = it }, Modifier.testTag("f")) } }
        onNode(hasSetTextAction()).performTextInput("Hello")
        waitForIdle()
        assertTrue(text.contains("Hello"), "source was '$text'")
        assertNotNull(text)
    }
}
