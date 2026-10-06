package tech.kloos.kompound.template

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.json.FieldInfo
import tech.kloos.kompound.json.JsonNumber
import tech.kloos.kompound.json.JsonString
import tech.kloos.kompound.json.TemplateScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class TemplateUiTest {
    private val fields = listOf(
        FieldInfo("title", "string", JsonString("Hello")),
        FieldInfo("description", "string", JsonString("A long text")),
        FieldInfo("count", "integer", JsonNumber(3.0)),
        FieldInfo("tasks[*].title", "string", JsonString("x")),
    )

    @Test
    fun completionContextFollowsTheCaret() {
        assertNull(templateCompletionAt("plain", 5))
        assertEquals("", templateCompletionAt("a {{", 4)!!.query)
        val c = templateCompletionAt("a {{ ti", 7)!!
        assertEquals("ti", c.query)
        assertEquals(5, c.replaceStart, "the spaces after {{ are kept")
        assertNull(templateCompletionAt("a {{x}} b", 9), "after a finished placeholder")
        assertNull(templateCompletionAt("a {{x|", 6), "after a filter bar there are no fields to offer")
        assertNull(templateCompletionAt("a \\{{x", 6), "an escaped brace is not a placeholder")
        assertEquals("y", templateCompletionAt("{{x}} {{y", 9)!!.query)
    }

    @Test
    fun applyingACompletionClosesTheBracesOnlyWhenNeeded() {
        val open = templateCompletionAt("go {{ti", 7)!!
        assertEquals("go {{title}}" to 12, applyTemplateCompletion("go {{ti", open, "title"))
        val mid = templateCompletionAt("go {{ti}} now", 7)!!
        assertEquals("go {{title}} now" to 12, applyTemplateCompletion("go {{ti}} now", mid, "title"))
        val spaced = templateCompletionAt("go {{ ti }} now", 8)!!
        assertEquals("go {{ title }} now" to 14, applyTemplateCompletion("go {{ ti }} now", spaced, "title"))
    }

    @Test
    fun indexesCountAsStarWhenCheckingFields() {
        val known = fields.map { it.path }.toSet()
        assertEquals(true, fieldKnown("tasks[0].title", known))
        assertEquals(true, fieldKnown("tasks[*].title", known))
        assertEquals(false, fieldKnown("tasks[0].name", known))
        assertEquals(false, fieldKnown("tilte", known))
    }

    @Test
    fun bindingsResolveAndPreview() {
        val scope = TemplateScope.ofFieldExamples(fields)
        assertEquals(JsonNumber(3.0), FieldBinding.Field("count").resolve(scope), "a field keeps its JSON type")
        assertEquals(JsonString("fixed"), FieldBinding.Value("fixed").resolve(scope))
        assertEquals(JsonString("Hello (3)"), FieldBinding.Template("{{title}} ({{count}})").resolve(scope))
        assertNull(FieldBinding.Field("nope").resolve(scope))
        assertEquals("Hello", bindingPreview(FieldBinding.Field("title"), fields))
        assertEquals("Hello!", bindingPreview(FieldBinding.Template("{{title}}!"), fields))
        assertNull(bindingPreview(FieldBinding.Template("{{missing}}"), fields), "no preview for a template that cannot render")
        assertNull(bindingPreview(null, fields))
    }

    @Test
    fun switchingTheModeConvertsWhatItCan() {
        assertEquals(FieldBinding.Template("{{title}}"), convertBinding(FieldBinding.Field("title"), 2))
        assertEquals(FieldBinding.Field("title"), convertBinding(FieldBinding.Template("{{ title }}"), 0))
        assertNull(convertBinding(FieldBinding.Template("a {{title}}"), 0), "more than one placeholder is not a field")
        assertEquals(FieldBinding.Template("fixed"), convertBinding(FieldBinding.Value("fixed"), 2))
        assertNull(convertBinding(FieldBinding.Field("title"), 1))
    }

    @Test
    fun typingBracesOffersTheFieldsAndAClickInsertsOne() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent { MaterialTheme(lightColorScheme()) { KTemplateField(text, { text = it }, fields, label = "Body") } }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("{{")
        waitForIdle()
        onNodeWithText("description").assertIsDisplayed()
        onNodeWithText("string · \"A long text\"").assertIsDisplayed()
        onNodeWithText("description").performClick()
        waitForIdle()
        assertEquals("{{description}}", text)
        onAllNodesWithText("count").assertCountEquals(0)
    }

    @Test
    fun typingNarrowsTheOffer() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent { MaterialTheme(lightColorScheme()) { KTemplateField(text, { text = it }, fields) } }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("url {{co")
        waitForIdle()
        onNodeWithText("count").assertIsDisplayed()
        onAllNodesWithText("title").assertCountEquals(0)
    }

    @Test
    fun anUnknownFieldIsReportedBeforeTheRun() = runComposeUiTest {
        setContent { MaterialTheme(lightColorScheme()) { KTemplateField("hello {{tilte}} {{title}}", {}, fields) } }
        onNodeWithText("Unknown field \"tilte\"").assertIsDisplayed()
    }

    @Test
    fun nothingIsMarkedWhileNothingIsKnownAndExtraPathsAreFine() = runComposeUiTest {
        setContent {
            MaterialTheme(lightColorScheme()) {
                KTemplateField("{{anything}}", {}, emptyList())
                KTemplateField("{{env.HOST}} {{in.title}}", {}, fields, isKnown = { it.startsWith("env.") || it == "in.title" })
            }
        }
        onAllNodesWithText("Unknown field \"anything\"").assertCountEquals(0)
        onAllNodesWithText("Unknown field \"env.HOST\"").assertCountEquals(0)
        onAllNodesWithText("Unknown field \"in.title\"").assertCountEquals(0)
    }

    @Test
    fun invalidPlaceholdersAreExplained() = runComposeUiTest {
        setContent { MaterialTheme(lightColorScheme()) { KTemplateField("{{title|nope}} {{ }}", {}, fields) } }
        onNodeWithText("Unknown filter \"nope\"; Empty placeholder").assertIsDisplayed()
    }

    @Test
    fun theAreaWorksLikeTheField() = runComposeUiTest {
        var text by mutableStateOf("line one\n")
        setContent { MaterialTheme(lightColorScheme()) { KTemplateArea(text, { text = it }, fields) } }
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("{{")
        waitForIdle()
        onNodeWithText("title").performClick()
        waitForIdle()
        assertEquals("line one\n{{title}}", text)
    }

    @Test
    fun theFieldPickerListsFieldsAndMarksUnknownPaths() = runComposeUiTest {
        var picked by mutableStateOf("")
        setContent { MaterialTheme(lightColorScheme()) { KFieldPicker(fields, picked, { picked = it }, label = "Field") } }
        onNode(hasSetTextAction()).performClick()
        waitForIdle()
        onNodeWithText("count").assertIsDisplayed()
        onNodeWithText("count").performClick()
        waitForIdle()
        assertEquals("count", picked)
        onNode(hasSetTextAction()).performTextInput("x")
        waitForIdle()
        assertEquals("xcount", picked)
        onNodeWithText("Unknown field \"xcount\"").assertIsDisplayed()
    }

    @Test
    fun theFieldPickerFiltersByType() = runComposeUiTest {
        setContent { MaterialTheme(lightColorScheme()) { KFieldPicker(fields, "", {}, types = setOf("integer")) } }
        onNode(hasSetTextAction()).performClick()
        waitForIdle()
        onNodeWithText("count").assertIsDisplayed()
        onAllNodesWithText("description").assertCountEquals(0)
    }

    @Test
    fun theMapperBindsAParameterToAFieldAndShowsTheResult() = runComposeUiTest {
        var bindings by mutableStateOf(emptyMap<String, FieldBinding>())
        setContent {
            MaterialTheme(lightColorScheme()) {
                KFieldMapper(fields, listOf(ParamSpec("b", "Second number", "number")), bindings, { bindings = it })
            }
        }
        onNode(hasSetTextAction()).performClick()
        waitForIdle()
        onNodeWithText("count").performClick()
        waitForIdle()
        assertEquals(mapOf("b" to FieldBinding.Field("count")), bindings)
        onNodeWithText("Result: 3").assertIsDisplayed()
        // switch to a template: the field becomes {{count}}
        onNodeWithText("Template").performClick()
        waitForIdle()
        assertEquals(mapOf("b" to FieldBinding.Template("{{count}}")), bindings)
        // remove the binding
        onNodeWithContentDescription("Remove Second number").performClick()
        waitForIdle()
        assertEquals(emptyMap(), bindings)
    }
}
