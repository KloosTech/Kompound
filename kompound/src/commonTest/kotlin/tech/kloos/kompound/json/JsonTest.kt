package tech.kloos.kompound.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonTest {
    @Test
    fun parsesAndWritesEveryValueKind() {
        val text = """{"a":[1,-2.5,1e3,true,false,null,"x\n\"\u00e9\\"],"b":{},"c":[]}"""
        val value = JsonValue.parse(text)
        assertEquals(value, JsonValue.parse(value.toJson()))
        assertEquals(value, JsonValue.parse(value.toJson(pretty = true)))
        val a = ((value as JsonObject)["a"] as JsonArray).items
        assertEquals(JsonNumber(1000.0), a[2])
        assertEquals(JsonString("x\n\"é\\"), a[6])
    }

    @Test
    fun rejectsBrokenText() {
        for (bad in listOf("", "{", "[1,]", "{\"a\" 1}", "tru", "\"abc", "[1] x", "{\"a\":01x}", "\"\\q\"")) {
            assertFailsWith<KJsonException>(bad) { JsonValue.parse(bad) }
        }
        assertFailsWith<KJsonException> { JsonValue.parse("[".repeat(2000)) }
    }

    @Test
    fun writesWholeNumbersWithoutADecimalPoint() {
        assertEquals("[1,2.5,-3]", JsonArray(listOf(JsonNumber(1.0), JsonNumber(2.5), JsonNumber(-3.0))).toJson())
    }
}

