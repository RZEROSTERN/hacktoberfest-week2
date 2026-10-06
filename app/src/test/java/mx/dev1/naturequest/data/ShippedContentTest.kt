package mx.dev1.naturequest.data

import mx.dev1.naturequest.data.prompts.PromptTemplate
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.SafetyValidator
import mx.dev1.naturequest.domain.language.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks the files we ship rather than code: the built-in safe hunt items and the prompt files.
 * These are exactly the things that break silently on a phone and are cheap to check here.
 */
class ShippedContentTest {
    private val validator = SafetyValidator()

    /** Unit tests run from the module directory, but be tolerant if run from the repo root. */
    private fun file(path: String): File =
        File(path).takeIf { it.exists() } ?: File("app/$path")

    private fun fallbackItems(stringsFile: String): List<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(stringsFile))
        val arrays = document.getElementsByTagName("string-array")
        for (i in 0 until arrays.length) {
            val array = arrays.item(i)
            if (array.attributes.getNamedItem("name").nodeValue == "fallback_hunt_items") {
                val items = (array as org.w3c.dom.Element).getElementsByTagName("item")
                return (0 until items.length).map { items.item(it).textContent }
            }
        }
        error("fallback_hunt_items not found in $stringsFile")
    }

    private fun assertAllSafeAndDistinct(items: List<String>, language: String) {
        assertTrue(
            "$language fallback list needs at least ${HuntLength.LONG.items} items to fill the longest hunt",
            items.size >= HuntLength.LONG.items,
        )
        val accepted = mutableListOf<String>()
        items.forEach { item ->
            val verdict = validator.validate(item, accepted)
            assertTrue("$language fallback \"$item\" must pass the safety validator, but got $verdict", verdict is SafetyValidator.Verdict.Valid)
            accepted += item
        }
    }

    @Test
    fun `built-in English hunt items are all safe and distinct`() {
        assertAllSafeAndDistinct(fallbackItems("src/main/res/values/strings.xml"), "English")
    }

    @Test
    fun `built-in Spanish hunt items are all safe and distinct`() {
        assertAllSafeAndDistinct(fallbackItems("src/main/res/values-es/strings.xml"), "Spanish")
    }

    @Test
    fun `English and Spanish fallback lists have the same length`() {
        assertEquals(
            fallbackItems("src/main/res/values/strings.xml").size,
            fallbackItems("src/main/res/values-es/strings.xml").size,
        )
    }

    private fun stringValue(stringsFile: String, name: String): String {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(stringsFile))
        val strings = document.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            if (strings.item(i).attributes.getNamedItem("name").nodeValue == name) return strings.item(i).textContent
        }
        error("$name not found in $stringsFile")
    }

    @Test
    fun `built-in verification feedback texts are safe to read to a child`() {
        listOf("src/main/res/values/strings.xml", "src/main/res/values-es/strings.xml").forEach { path ->
            listOf("verify_fallback_not_sure", "verify_fallback_match", "verify_fallback_miss").forEach { name ->
                val text = stringValue(path, name)
                assertTrue("$name in $path is empty", text.isNotBlank())
                assertTrue("$name in $path must not tell a child to do anything unsafe: $text", !validator.containsUnsafeInstruction(text))
            }
        }
    }

    // setup_adult_notice is left out on purpose: it is the safety rule itself ("never pick or touch
    // anything"), so it names the forbidden verbs in a prohibition.
    @Test
    fun `texts read aloud to children are safe`() {
        listOf("src/main/res/values/strings.xml", "src/main/res/values-es/strings.xml").forEach { path ->
            listOf(
                "speech_hunt_ready", "speech_summary", "hunt_tap_hint", "hunt_wait_hint", "verify_checking_hint",
                "summary_title", "hunt_leave_body",
            ).forEach { name ->
                val text = stringValue(path, name)
                assertTrue(
                    "$name in $path must not tell a child to do anything unsafe: $text",
                    !validator.containsUnsafeInstruction(text),
                )
            }
        }
    }

    @Test
    fun `the invitation always mentions going with an adult`() {
        assertTrue(stringValue("src/main/res/values/strings.xml", "speech_hunt_ready").contains("adult"))
        assertTrue(stringValue("src/main/res/values-es/strings.xml", "speech_hunt_ready").contains("adulto"))
    }

    @Test
    fun `the per-app language list matches the translations and languages that ship`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file("src/main/res/xml/locales_config.xml"))
        val listed = (0 until document.getElementsByTagName("locale").length)
            .map { (document.getElementsByTagName("locale").item(it) as org.w3c.dom.Element).getAttribute("android:name") }
            .toSet()
        val translationFolders = file("src/main/res").listFiles { f -> f.isDirectory && Regex("values-[a-z]{2}").matches(f.name) }
            .orEmpty().map { it.name.removePrefix("values-") }.toSet()

        assertEquals("Every translation folder must be offered in Android's App languages", translationFolders + "en", listed)
        assertEquals("AppLanguage must list the same languages", AppLanguage.entries.map { it.code }.toSet(), listed)
    }

    @Test
    fun `hunt generation prompt renders with the variables the use case sends`() {
        val template = file("src/main/assets/prompts/hunt_generation_v2.txt").readText()
        val values = listOf("count", "place", "month", "age_range", "language", "avoid").associateWith { "<$it>" }

        val rendered = PromptTemplate.render(template, values)

        assertTrue(values.values.all { rendered.contains(it) })
        assertTrue("A placeholder was left unfilled", !rendered.contains("{{"))
    }

    @Test
    fun `verification prompt renders with the variables the verifier sends`() {
        val template = file("src/main/assets/prompts/verification_v1.txt").readText()

        val rendered = PromptTemplate.render(template, mapOf("item" to "a red leaf", "language" to "English"))

        assertTrue(rendered.contains("\"a red leaf\""))
        assertTrue("A placeholder was left unfilled", !rendered.contains("{{"))
    }
}
