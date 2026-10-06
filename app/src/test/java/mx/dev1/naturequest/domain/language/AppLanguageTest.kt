package mx.dev1.naturequest.domain.language

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun `English and Spanish phones keep their language, whatever the region`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(Locale.forLanguageTag("en-US")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(Locale.forLanguageTag("en-GB")))
        assertEquals(AppLanguage.SPANISH, AppLanguage.resolve(Locale.forLanguageTag("es-MX")))
        assertEquals(AppLanguage.SPANISH, AppLanguage.resolve(Locale.forLanguageTag("es-ES")))
        assertEquals(AppLanguage.SPANISH, AppLanguage.resolve(Locale.forLanguageTag("es")))
    }

    @Test
    fun `any other language falls back to English, matching the English screens`() {
        listOf("fr-FR", "pt-BR", "de-DE", "it", "ja-JP", "zh-CN", "ar").forEach { tag ->
            assertEquals(tag, AppLanguage.ENGLISH, AppLanguage.resolve(Locale.forLanguageTag(tag)))
        }
    }

    @Test
    fun `the language name sent to the model is plain English`() {
        assertEquals("English", AppLanguage.ENGLISH.promptName)
        assertEquals("Spanish", AppLanguage.SPANISH.promptName)
    }
}
