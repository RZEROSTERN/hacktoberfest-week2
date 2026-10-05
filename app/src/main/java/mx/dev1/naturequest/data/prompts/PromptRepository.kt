package mx.dev1.naturequest.data.prompts

import android.content.Context

/** Loads versioned prompt files from `assets/prompts/` (for example `hunt_generation_v1`). */
class PromptRepository(private val context: Context) {
    fun render(name: String, values: Map<String, String>): String {
        val template = context.assets.open("prompts/$name.txt").bufferedReader().use { it.readText() }
        return PromptTemplate.render(template, values)
    }
}
