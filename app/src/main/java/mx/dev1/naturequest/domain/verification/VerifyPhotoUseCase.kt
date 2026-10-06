package mx.dev1.naturequest.domain.verification

import mx.dev1.naturequest.domain.hunt.SafetyValidator
import mx.dev1.naturequest.domain.inference.InferenceEngine
import mx.dev1.naturequest.domain.language.AppLanguage
import mx.dev1.naturequest.domain.inference.InferenceRequest
import mx.dev1.naturequest.domain.inference.ModelJsonParser
import mx.dev1.naturequest.domain.inference.VerificationResponse
import mx.dev1.naturequest.domain.prompt.PromptSource
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * Asks the on-device model whether a photo shows a hunt item and turns the answer into something
 * safe to show a child. Invalid output gets one retry (with a new seed, since a repeat of the same
 * request would give the same answer), then the safe "I'm not sure, try another photo" default.
 * Anything the model says that could tell a child to pick, touch, approach or eat something is
 * replaced by a generic encouraging line. The photo only ever lives in memory here.
 *
 * Like hunt generation, it loads the engine for this one request and releases it after.
 */
class VerifyPhotoUseCase @Inject constructor(
    private val engine: InferenceEngine,
    private val prompts: PromptSource,
    private val parser: ModelJsonParser,
    private val validator: SafetyValidator,
    private val fallbacks: VerificationFallbacks,
) {
    suspend operator fun invoke(
        item: String,
        photoJpeg: ByteArray,
        locale: Locale = Locale.getDefault(),
    ): PhotoVerification {
        val prompt = prompts.render(
            PROMPT_NAME,
            mapOf("item" to item, "language" to AppLanguage.resolve(locale).promptName),
        )
        try {
            engine.load()
            repeat(MAX_ATTEMPTS) { attempt ->
                val response = try {
                    engine.generate(
                        InferenceRequest(
                            prompt = prompt,
                            imageJpeg = photoJpeg,
                            // Cold and repeatable on the first try; a new seed on the retry.
                            temperature = 0.2,
                            maxOutputTokens = MAX_OUTPUT_TOKENS,
                            seed = if (attempt == 0) 0 else Random.nextInt(),
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null // a native engine error counts as invalid output
                }
                val parsed = response?.let { parser.parseVerification(it.text).getOrNull() }
                if (parsed != null) return makeSafe(parsed)
            }
        } finally {
            engine.release()
        }
        return PhotoVerification(VerificationOutcome.NOT_SURE, fallbacks.notSure(), hint = null)
    }

    private fun makeSafe(response: VerificationResponse): PhotoVerification {
        val outcome = if (response.match) VerificationOutcome.MATCH else VerificationOutcome.NO_MATCH
        val hint = response.hint.trim().takeIf { it.isNotEmpty() && outcome == VerificationOutcome.NO_MATCH }
        val unsafe = validator.containsUnsafeInstruction(response.message) ||
            (hint != null && validator.containsUnsafeInstruction(hint))
        if (unsafe) {
            val generic = if (response.match) fallbacks.genericMatch() else fallbacks.genericMiss()
            return PhotoVerification(outcome, generic, hint = null)
        }
        return PhotoVerification(outcome, response.message.trim(), hint)
    }

    private companion object {
        const val PROMPT_NAME = "verification_v1"
        const val MAX_ATTEMPTS = 2 // one retry after invalid output
        const val MAX_OUTPUT_TOKENS = 120
    }
}
