package tech.kloos.kompound.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonFieldsTest {
    private val doc = JsonValue.parse(
        """{"title":"T","count":3,"ratio":0.5,"ok":true,"owner":{"name":"Ann","tags":["a","b"]},"tasks":[{"title":"x","done":false},{"title":"y"}],"a b":1}""",
    )

    private fun paths(f: List<FieldInfo>) = f.map { it.path }

    @Test
    fun listsKeysNestedKeysAndArrayItemsBehindStar() {
        val fields = JsonFields.of(doc)
        assertEquals(
            listOf("title", "count", "ratio", "ok", "owner", "owner.name", "owner.tags", "owner.tags[*]", "tasks", "tasks[*].title", "tasks[*].done", "[\"a b\"]"),
            paths(fields),
        )
        assertEquals("string", fields.first { it.path == "title" }.type)
        assertEquals("integer", fields.first { it.path == "count" }.type)
        assertEquals("number", fields.first { it.path == "ratio" }.type)
        assertEquals("array", fields.first { it.path == "tasks" }.type)
        assertEquals(JsonString("T"), fields.first { it.path == "title" }.example)
        assertEquals(null, fields.first { it.path == "owner" }.example, "containers have no single example")
    }

    @Test
    fun everyListedPathReadsBackWithAt() {
        for (f in JsonFields.of(doc)) {
            val readable = doc.at(f.path)
            assertTrue(readable != null, "${f.path} should read back")
        }
    }

    @Test
    fun rootArraysAndScalarArrays() {
        assertEquals(listOf("[*].id"), paths(JsonFields.of(JsonValue.parse("""[{"id":1},{"id":2}]"""))))
        assertEquals(listOf("[*]"), paths(JsonFields.of(JsonValue.parse("""["a","b"]"""))))
        assertEquals(emptyList(), JsonFields.of(JsonValue.parse("""[]""")))
        assertEquals(emptyList(), JsonFields.of(JsonString("scalar")))
    }

    @Test
    fun limitsAndLongExamples() {
        val deep = JsonValue.parse("""{"a":{"b":{"c":{"d":1}}}}""")
        assertEquals(listOf("a", "a.b"), paths(JsonFields.of(deep, maxDepth = 2)))
        val wide = JsonObject((1..500).associate { "k$it" to JsonNumber(it.toDouble()) })
        assertEquals(JsonFields.MaxFields, JsonFields.of(wide).size)
        val long = JsonFields.of(JsonValue.parse("""{"s":"${"x".repeat(200)}"}"""), exampleLength = 10).single()
        assertEquals(JsonString("xxxxxxxxxx…"), long.example)
    }

    @Test
    fun schemaFieldsWithTypesAndExamples() {
        val schema = JsonValue.parse(
            """{"type":"object","properties":{"title":{"type":"string","example":"Hello"},"n":{"type":"integer"},"tasks":{"type":"array","items":{"type":"object","properties":{"name":{"type":"string","enum":["a","b"]}}}}}}""",
        )
        val f = JsonFields.ofSchema(schema)
        assertEquals(listOf("title", "n", "tasks", "tasks[*].name"), paths(f))
        assertEquals(JsonString("Hello"), f[0].example)
        assertEquals("integer", f[1].type)
        assertEquals(JsonString("a"), f[3].example)
    }

    @Test
    fun exampleFromSchemaAndNestedShorthand() {
        val schema = JsonSchema.fromNestedShorthand(JsonValue.parse("""{"name":"string","tags":["string"],"owner":{"id":"integer"},"ok":"boolean"}"""))
        assertEquals("""{"name":"","tags":[""],"owner":{"id":0},"ok":false}""", JsonSchema.example(schema).toJson())
        assertEquals(listOf("name", "tags", "tags[*]", "owner", "owner.id", "ok").sorted(), paths(JsonFields.ofSchema(schema)).sorted().let { it })
        assertEquals(emptyList(), JsonSchema.validate(JsonSchema.example(schema), schema))
    }
}
