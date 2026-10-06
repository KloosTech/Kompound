package tech.kloos.kompound.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaCompatTest {
    private fun s(text: String) = JsonValue.parse(text)
    private fun check(source: String, target: String) = JsonSchema.incompatibilities(s(source), s(target))

    @Test
    fun matchingShapesFit() {
        val obj = """{"type":"object","properties":{"title":{"type":"string"},"n":{"type":"integer"}}}"""
        assertEquals(emptyList(), check(obj, obj))
        assertEquals(emptyList(), check("""{"type":"integer"}""", """{"type":"number"}"""), "integer fits number")
        assertEquals(emptyList(), check("""{"type":"string"}""", """{"type":["string","null"]}"""))
    }

    @Test
    fun aDifferentTypeIsReportedWithItsPath() {
        assertEquals(listOf("expected number, gets string"), check("""{"type":"string"}""", """{"type":"number"}"""))
        assertEquals(listOf("expected integer, gets number"), check("""{"type":"number"}""", """{"type":"integer"}"""))
        val src = """{"type":"object","properties":{"owner":{"type":"object","properties":{"id":{"type":"string"}}}}}"""
        val dst = """{"type":"object","properties":{"owner":{"type":"object","properties":{"id":{"type":"integer"}}}}}"""
        assertEquals(listOf("owner.id: expected integer, gets string"), check(src, dst))
    }

    @Test
    fun aRequiredPropertyTheSourceLacksIsReported() {
        val src = """{"type":"object","properties":{"title":{"type":"string"}}}"""
        val dst = """{"type":"object","required":["title","description"],"properties":{"title":{"type":"string"}}}"""
        assertEquals(listOf("missing field \"description\""), check(src, dst))
    }

    @Test
    fun arraysCompareTheirItems() {
        val src = """{"type":"array","items":{"type":"object","properties":{"id":{"type":"string"}}}}"""
        val dst = """{"type":"array","items":{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}}"""
        assertEquals(listOf("[*]: missing field \"name\""), check(src, dst))
        assertEquals(listOf("expected array, gets object"), check("""{"type":"object"}""", """{"type":"array"}"""))
    }

    @Test
    fun saysNothingWhenTheSourceSaysTooLittle() {
        assertEquals(emptyList(), check("""{}""", """{"type":"object","required":["a"]}"""))
        assertEquals(emptyList(), check("""{"type":"object"}""", """{"type":"object","required":["a"]}"""), "no properties declared: cannot tell")
        assertEquals(emptyList(), JsonSchema.incompatibilities(JsonString("not a schema"), s("""{"type":"string"}""")))
        assertTrue(check("""{"type":"object","properties":{}}""", """{"type":"object","required":["a"]}""").isNotEmpty())
    }
}
