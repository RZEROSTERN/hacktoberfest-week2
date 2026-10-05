package mx.dev1.naturequest.data.prompts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.dev1.naturequest.domain.prompt.PromptSource
import javax.inject.Inject

/** Loads versioned prompt files from `assets/prompts/` (for example `hunt_generation_v2`). */
class PromptRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PromptSource {
    override fun render(name: String, values: Map<String, String>): String {
        val template = context.assets.open("prompts/$name.txt").bufferedReader().use { it.readText() }
        return PromptTemplate.render(template, values)
    }
}
