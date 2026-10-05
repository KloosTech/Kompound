package tech.kloos.kompound.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonToolsTest {
    private val doc = JsonValue.parse("""{"items":[{"id":1,"name":"a"},{"id":2,"name":"b"}],"meta":{"ok":true}}""")

    @Test
    fun atReadsPaths() {
        assertEquals(JsonString("b"), doc.at("items[1].name"))
        assertEquals(JsonBool(true), doc.at("meta.ok"))
        assertEquals(JsonNumber(1.0), doc.at("items[0].id"))
        assertNull(doc.at("items[5]"))
        assertNull(doc.at("meta.missing.deeper"))
        assertEquals(doc, doc.at(""))
    }

    @Test
    fun wildcardCollectsFromEveryItem() {
        assertEquals(JsonArray(listOf(JsonString("a"), JsonString("b"))), doc.at("items[*].name"))
        assertEquals(JsonArray(listOf(JsonNumber(1.0), JsonNumber(2.0))), doc.at("items.*.id"))
    }

    @Test
    fun ofBuildsFromKotlinValues() {
        val v = JsonValue.of(mapOf("a" to 1, "b" to listOf("x", null, true), "c" to mapOf("d" to 2.5)))
        assertEquals("""{"a":1,"b":["x",null,true],"c":{"d":2.5}}""", v.toJson())
        assertEquals(JsonNull, JsonValue.of(null))
        assertEquals(JsonString("Up"), JsonValue.of(Dir.Up))
    }

    private enum class Dir { Up }

    @Test
    fun parseOrNullAndMerge() {
        assertNull(JsonValue.parseOrNull("{oops"))
        assertEquals(JsonNumber(1.0), JsonValue.parseOrNull("1"))
        val a = jsonObjectOf("x" to JsonNumber(1.0), "n" to jsonObjectOf("p" to JsonNumber(1.0)))
        val b = jsonObjectOf("x" to JsonNumber(2.0), "n" to jsonObjectOf("q" to JsonNumber(2.0)))
        assertEquals(JsonNumber(2.0), (a + b)["x"])
        assertEquals(jsonObjectOf("q" to JsonNumber(2.0)), (a + b)["n"])
        assertEquals(jsonObjectOf("p" to JsonNumber(1.0), "q" to JsonNumber(2.0)), a.merge(b, deep = true)["n"])
    }

    @Test
    fun schemaReportsProblemsWithPaths() {
        val schema = JsonValue.parse("""{"type":"object","required":["name","tags"],"properties":{"name":{"type":"string","minLength":2},"age":{"type":"integer","minimum":0},"tags":{"type":"array","items":{"type":"string"}}},"additionalProperties":false}""")
        assertEquals(emptyList(), JsonSchema.validate(JsonValue.parse("""{"name":"Al","tags":["x"],"age":3}"""), schema))
        val bad = JsonSchema.validate(JsonValue.parse("""{"name":"A","tags":["x",2],"age":-1.5,"extra":1}"""), schema).map { it.toString() }
        assertTrue("name: must have at least 2 characters" in bad, "$bad")
        assertTrue(bad.any { it.startsWith("tags[1]: expected string") }, "$bad")
        assertTrue(bad.any { it.startsWith("age: expected integer") }, "$bad")
        assertTrue("extra: is not allowed" in bad, "$bad")
        assertEquals(listOf("tags: is required"), JsonSchema.validate(JsonValue.parse("""{"name":"Al"}"""), schema).map { it.toString() })
    }

    @Test
    fun shorthandBecomesASchema() {
        val schema = JsonSchema.fromShorthand(JsonValue.parse("""{"city":"string","pop":"number"}""") as JsonObject)
        assertTrue(JsonSchema.validate(JsonValue.parse("""{"city":"Oslo","pop":1.5}"""), schema).isEmpty())
        assertEquals(listOf("pop: is required"), JsonSchema.validate(JsonValue.parse("""{"city":"Oslo"}"""), schema).map { it.toString() })
    }
}
