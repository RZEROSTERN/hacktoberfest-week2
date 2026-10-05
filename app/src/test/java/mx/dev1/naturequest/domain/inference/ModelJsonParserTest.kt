package mx.dev1.naturequest.domain.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelJsonParserTest {
    private val parser = ModelJsonParser()

    @Test
    fun `parses a clean hunt list`() {
        val result = parser.parseHuntList("""{"items": ["a red leaf", "a feather"]}""")
        assertEquals(listOf("a red leaf", "a feather"), result.getOrThrow().items)
    }

    @Test
    fun `parses JSON wrapped in code fences and prose`() {
        val raw = """
            Sure! Here is your list:
            ```json
            {"items": ["bark with moss on it"]}
            ```
            Have fun!
        """.trimIndent()
        assertEquals(listOf("bark with moss on it"), parser.parseHuntList(raw).getOrThrow().items)
    }

    @Test
    fun `ignores unknown keys`() {
        val result = parser.parseHuntList("""{"items": ["a leaf"], "notes": "extra"}""")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fails on empty or blank hunt items`() {
        assertTrue(parser.parseHuntList("""{"items": []}""").isFailure)
        assertTrue(parser.parseHuntList("""{"items": ["a leaf", "  "]}""").isFailure)
    }

    @Test
    fun `fails when there is no JSON or it has the wrong shape`() {
        assertTrue(parser.parseHuntList("I cannot help with that.").isFailure)
        assertTrue(parser.parseHuntList("""{"things": ["a leaf"]}""").isFailure)
        assertTrue(parser.parseHuntList("""{"items": ["a leaf"]""").isFailure)
    }

    @Test
    fun `parses a verification response`() {
        val raw = """{"match": true, "message": "Great red leaf!", "hint": ""}"""
        val response = parser.parseVerification(raw).getOrThrow()
        assertTrue(response.match)
        assertEquals("Great red leaf!", response.message)
        assertEquals("", response.hint)
    }

    @Test
    fun `hint is optional in a verification response`() {
        val response = parser.parseVerification("""{"match": false, "message": "That looks like a rock."}""")
        assertFalse(response.getOrThrow().match)
        assertEquals("", response.getOrThrow().hint)
    }

    @Test
    fun `fails on a blank verification message or a non boolean match`() {
        assertTrue(parser.parseVerification("""{"match": true, "message": " "}""").isFailure)
        assertTrue(parser.parseVerification("""{"match": "maybe", "message": "hm"}""").isFailure)
    }

    @Test
    fun `extractor ignores braces inside strings and handles escapes`() {
        val raw = """noise {"message": "use {braces} and \"quotes\"", "match": true} trailing {"x": 1}"""
        assertEquals(
            """{"message": "use {braces} and \"quotes\"", "match": true}""",
            extractJsonObject(raw),
        )
    }

    @Test
    fun `extractor returns null without an object`() {
        assertNull(extractJsonObject("no braces here"))
        assertNull(extractJsonObject("""{"unterminated": true"""))
    }
}
