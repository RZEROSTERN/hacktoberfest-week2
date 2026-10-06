package mx.dev1.naturequest.domain.language

import java.util.Locale

/**
 * The languages Nature Quest fully supports: UI strings, the model's prompts and answers, the voice,
 * and the safety word lists. Anything else falls back to [DEFAULT] *everywhere at once*, so a phone in
 * another language gets a consistent English app instead of English screens with a model writing
 * unchecked text in a language the safety validator does not know.
 *
 * To add a language: its `values-xx` strings and fallback hunt items, prompt examples, safety word
 * lists in [mx.dev1.naturequest.domain.hunt.SafetyValidator], and `res/xml/locales_config.xml`.
 */
enum class AppLanguage(val code: String, val promptName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Spanish"),
    ;

    companion object {
        val DEFAULT = ENGLISH

        fun resolve(locale: Locale): AppLanguage = entries.firstOrNull { it.code == locale.language } ?: DEFAULT
    }
}
