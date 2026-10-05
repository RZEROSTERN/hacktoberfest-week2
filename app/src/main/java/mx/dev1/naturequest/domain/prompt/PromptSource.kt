package mx.dev1.naturequest.domain.prompt

/** Supplies ready-to-send prompts from the versioned prompt files. */
interface PromptSource {
    fun render(name: String, values: Map<String, String>): String
}
