package tech.kloos.kompound.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TemplateTest {
    private val data = TemplateScope.of(
        JsonValue.parse("""{"title":"Fix \"it\"","n":3,"ok":true,"none":null,"empty":"","owner":{"name":"Ann Lee"},"tasks":[{"title":"x"},{"title":"y z"}],"tags":["a","b"]}"""),
    )

    private fun render(t: String, scope: TemplateScope = data) = Template.render(t, scope)

    @Test
    fun fillsPathsNestedFieldsAndArrayItems() {
        assertEquals("Fix \"it\"", render("{{title}}"))
        assertEquals("Ann Lee", render("{{owner.name}}"))
        assertEquals("y z", render("{{tasks[1].title}}"))
        assertEquals("""["x","y z"]""", render("{{tasks[*].title}}"))
        assertEquals("3 true", render("{{n}} {{ok}}"))
        assertEquals("[\"a\",\"b\"]", render("{{tags}}"))
        assertEquals("""{"name":"Ann Lee"}""", render("{{owner}}"))
        assertEquals("", render("{{none}}"))
    }

    @Test
    fun whitespaceInsideTheBracesAndLiteralTextAround() {
        assertEquals("POST /issues/Ann Lee?x=1", render("POST /issues/{{  owner.name }}?x=1"))
        assertEquals("no placeholders", render("no placeholders"))
        assertEquals("", render(""))
    }

    @Test
    fun anEscapedBraceIsLiteral() {
        assertEquals("{{title}} is Fix \"it\"", render("\\{{title}} is {{title}}"))
    }

    @Test
    fun jsonBodiesKeepTheirOwnBraces() {
        assertEquals("""{"a": "Fix \"it\"", "o": {"n": 3}}""", render("""{"a": "{{title|json}}", "o": {"n": {{n}}}}"""))
        assertEquals("""{"tags": ["a","b"]}""", render("""{"tags": {{tags|json}}}"""))
    }

    @Test
    fun filters() {
        assertEquals("Fix \\\"it\\\"", render("{{title|json}}"))
        assertEquals("Ann%20Lee", render("{{owner.name|url}}"))
        assertEquals("%C3%A4%2F%3F", render("{{x|url}}", TemplateScope.of(JsonValue.parse("""{"x":"ä/?"}"""))))
        assertEquals("none", render("{{missing|default:none}}"))
        assertEquals("none", render("{{none|default:none}}"))
        assertEquals("none", render("{{empty|default:none}}"))
        assertEquals("Ann Lee", render("{{owner.name|default:nobody}}"))
        assertEquals("Ann%20Lee", render("{{missing|default:Ann Lee|url}}"))
    }

    @Test
    fun aMissingFieldThrowsWithItsPath() {
        val e = assertFailsWith<MissingFieldException> { render("hello {{tilte}}") }
        assertEquals("tilte", e.path)
        assertTrue("tilte" in e.message!!)
        assertFailsWith<MissingFieldException> { render("{{owner.age}}") }
        assertFailsWith<MissingFieldException> { render("{{tasks[9].title}}") }
    }

    @Test
    fun invalidPlaceholdersAreReportedWithOffsetsAndFailRendering() {
        val text = "a {{ }} b {{x|nope}} c {{open"
        val problems = Template.problems(text)
        assertEquals(listOf("Empty placeholder", "Unknown filter \"nope\"", "Unclosed {{"), problems.map { it.message })
        assertEquals(2, problems[0].start)
        assertEquals(7, problems[0].end)
        assertFailsWith<TemplateException> { render(text) }
        assertFailsWith<TemplateException> { render("{{x|default}}") }
    }

    @Test
    fun namesAndParts() {
        assertEquals(setOf("title", "owner.name", "tasks[*].title"), Template.names("{{title}} {{ owner.name |url}} {{title}} {{tasks[*].title}} \\{{skip}}"))
        val parts = Template.parse("ab{{x}}cd")
        assertEquals(3, parts.size)
        assertEquals(2, parts[1].start)
        assertEquals(7, parts[1].end)
        assertEquals("cd", (parts[2] as TemplatePart.Literal).text)
    }

    @Test
    fun scopesCombine() {
        val vars = TemplateScope.ofNamed(mapOf("env" to mapOf("HOST" to "h.example"), "count" to 2))
        val scope = data then vars
        assertEquals("https://h.example/Ann Lee/2", render("https://{{env.HOST}}/{{owner.name}}/{{count}}", scope))
        assertFailsWith<MissingFieldException> { render("{{nope}}", scope) }
        // the first scope wins
        assertEquals("3", render("{{n}}", TemplateScope.ofNamed(mapOf("n" to 99)).let { data then it }))
    }
}
