package mx.dev1.naturequest.data.prompts

/** Fills `{{name}}` placeholders in a prompt file. A placeholder without a value is a bug. */
object PromptTemplate {
    // Both braces escaped: Android's ICU regex rejects a bare `}` that the JVM tolerates.
    private val placeholder = Regex("""\{\{(\w+)\}\}""")

    fun render(template: String, values: Map<String, String>): String =
        placeholder.replace(template) { match ->
            val name = match.groupValues[1]
            values[name] ?: throw IllegalArgumentException("Missing prompt variable: $name")
        }
}
