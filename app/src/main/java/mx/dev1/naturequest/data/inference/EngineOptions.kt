package mx.dev1.naturequest.data.inference

enum class InferenceBackend { CPU, GPU }

/**
 * Tunables for the on-device engine.
 *
 * @property visionBackend backend for the image encoder; null means the same as [backend].
 * @property visualTokenBudget tokens spent per image; Gemma 4 accepts 70, 140, 280, 560 or 1120.
 *   Lower is faster and uses less memory, higher sees more detail. Null uses the engine default.
 * @property maxNumTokens context window (prompt + image + answer). Null uses the model default.
 */
data class EngineOptions(
    val backend: InferenceBackend = InferenceBackend.CPU,
    val visionBackend: InferenceBackend? = null,
    val visualTokenBudget: Int? = 140,
    val maxNumTokens: Int? = 2048,
) {
    init {
        require(visualTokenBudget == null || visualTokenBudget in ALLOWED_VISUAL_TOKEN_BUDGETS) {
            "visualTokenBudget must be one of $ALLOWED_VISUAL_TOKEN_BUDGETS"
        }
    }

    companion object {
        val ALLOWED_VISUAL_TOKEN_BUDGETS = listOf(70, 140, 280, 560, 1120)
    }
}
