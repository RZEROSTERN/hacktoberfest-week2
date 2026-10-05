package mx.dev1.naturequest.data.prompts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PromptTemplateTest {
    @Test
    fun `replaces every placeholder`() {
        val rendered = PromptTemplate.render(
            "Find {{count}} things in a {{place}}. Again: {{count}}.",
            mapOf("count" to "5", "place" to "park"),
        )
        assertEquals("Find 5 things in a park. Again: 5.", rendered)
    }

    @Test
    fun `leaves JSON examples with single braces untouched`() {
        val template = """Answer: {"items": ["a"]} for {{place}}"""
        assertEquals("""Answer: {"items": ["a"]} for forest""", PromptTemplate.render(template, mapOf("place" to "forest")))
    }

    @Test
    fun `inserts values literally`() {
        assertEquals("a \$1 {{x}}", PromptTemplate.render("{{v}}", mapOf("v" to "a \$1 {{x}}")))
    }

    @Test
    fun `fails fast on a missing variable`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PromptTemplate.render("Hello {{name}}", emptyMap())
        }
        assertEquals("Missing prompt variable: name", error.message)
    }
}
