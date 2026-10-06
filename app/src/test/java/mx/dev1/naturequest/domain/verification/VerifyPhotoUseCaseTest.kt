package mx.dev1.naturequest.domain.verification

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.dev1.naturequest.domain.hunt.SafetyValidator
import mx.dev1.naturequest.domain.inference.ModelJsonParser
import mx.dev1.naturequest.domain.inference.ModelNotAvailableException
import mx.dev1.naturequest.testing.FakeInferenceEngine
import mx.dev1.naturequest.testing.FakePromptSource
import mx.dev1.naturequest.testing.FakeVerificationFallbacks
import mx.dev1.naturequest.testing.verificationJson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class VerifyPhotoUseCaseTest {
    private val prompts = FakePromptSource()
    private val photo = byteArrayOf(9, 8, 7)

    private fun useCase(engine: FakeInferenceEngine) =
        VerifyPhotoUseCase(engine, prompts, ModelJsonParser(), SafetyValidator(), FakeVerificationFallbacks())

    @Test
    fun `a match returns the models cheerful message and no hint`() = runTest {
        val engine = FakeInferenceEngine.scripted(verificationJson(true, "What a bright red leaf!", hint = "ignored"))

        val result = useCase(engine)("a red leaf", photo)

        assertEquals(VerificationOutcome.MATCH, result.outcome)
        assertEquals("What a bright red leaf!", result.message)
        assertNull(result.hint) // a hint makes no sense after a success
    }

    @Test
    fun `a miss keeps the message and the friendly hint`() = runTest {
        val engine = FakeInferenceEngine.scripted(
            verificationJson(false, "That looks like a stone.", hint = "Look up at the trees for leaves."),
        )

        val result = useCase(engine)("a red leaf", photo)

        assertEquals(VerificationOutcome.NO_MATCH, result.outcome)
        assertEquals("That looks like a stone.", result.message)
        assertEquals("Look up at the trees for leaves.", result.hint)
    }

    @Test
    fun `a miss without a hint has none`() = runTest {
        val engine = FakeInferenceEngine.scripted(verificationJson(false, "I see a gray stone."))
        assertNull(useCase(engine)("a red leaf", photo).hint)
    }

    @Test
    fun `sends the photo, the item and the device language to the model`() = runTest {
        val engine = FakeInferenceEngine.scripted(verificationJson(true, "Nice!"))

        useCase(engine)("una hoja roja", photo, Locale.forLanguageTag("es-MX"))

        val request = engine.requests.single()
        assertArrayEquals(photo, request.imageJpeg)
        assertEquals(0.2, request.temperature, 0.0)
        val (name, values) = prompts.rendered.single()
        assertEquals("verification_v1", name)
        assertEquals("una hoja roja", values["item"])
        assertEquals("Spanish", values["language"])
    }

    @Test
    fun `answers in English on a phone whose language the app does not support`() = runTest {
        val engine = FakeInferenceEngine.scripted(verificationJson(true, "Nice!"))

        useCase(engine)("a red leaf", photo, Locale.forLanguageTag("pt-BR"))

        assertEquals("English", prompts.rendered.single().second["language"])
    }

    @Test
    fun `retries once with a different seed after invalid output`() = runTest {
        val engine = FakeInferenceEngine.scripted("I think it is a leaf.", verificationJson(true, "Great find!"))

        val result = useCase(engine)("a red leaf", photo)

        assertEquals(VerificationOutcome.MATCH, result.outcome)
        assertEquals(2, engine.requests.size)
        assertEquals(0, engine.requests[0].seed)
        assertNotEquals("A repeat with the same seed would give the same answer", 0, engine.requests[1].seed)
    }

    @Test
    fun `says it is not sure when the model never gives usable output`() = runTest {
        val engine = FakeInferenceEngine.scripted("nope", "still nope")

        val result = useCase(engine)("a red leaf", photo)

        assertEquals(VerificationOutcome.NOT_SURE, result.outcome)
        assertEquals("NOT_SURE", result.message)
        assertNull(result.hint)
        assertEquals(2, engine.requests.size)
    }

    @Test
    fun `a native engine error is treated like invalid output`() = runTest {
        val engine = FakeInferenceEngine { _, _ -> throw IllegalStateException("native failure") }

        val result = useCase(engine)("a red leaf", photo)

        assertEquals(VerificationOutcome.NOT_SURE, result.outcome)
        assertEquals(1, engine.releaseCalls)
    }

    @Test
    fun `replaces a message that tells a child to pick or touch something`() = runTest {
        val engine = FakeInferenceEngine.scripted(verificationJson(true, "Wow! Now pick it up and take it home."))

        val result = useCase(engine)("a red leaf", photo)

        assertEquals(VerificationOutcome.MATCH, result.outcome)
        assertEquals("GENERIC_MATCH", result.message)
    }

    @Test
    fun `replaces an unsafe hint and drops it`() = runTest {
        val engine = FakeInferenceEngine.scripted(
            verificationJson(false, "That is a rock.", hint = "Climb the tree and grab a leaf."),
        )

        val result = useCase(engine)("a red leaf", photo)

        assertEquals(VerificationOutcome.NO_MATCH, result.outcome)
        assertEquals("GENERIC_MISS", result.message)
        assertNull(result.hint)
    }

    @Test
    fun `lets the model describe an animal in the photo and tell the player to take another picture`() = runTest {
        val engine = FakeInferenceEngine.scripted(
            verificationJson(false, "I see a cute bee, not a leaf.", hint = "Take another photo of a red leaf."),
        )

        val result = useCase(engine)("a red leaf", photo)

        assertEquals("I see a cute bee, not a leaf.", result.message)
        assertEquals("Take another photo of a red leaf.", result.hint)
    }

    @Test
    fun `loads the engine once and always releases it`() = runTest {
        val engine = FakeInferenceEngine.scripted(verificationJson(true, "Nice!"))
        useCase(engine)("a red leaf", photo)
        assertEquals(1, engine.loadCalls)
        assertEquals(1, engine.releaseCalls)
    }

    @Test
    fun `a missing model fails clearly and still releases the engine`() = runTest {
        val engine = FakeInferenceEngine.scripted("unused").apply { loadError = ModelNotAvailableException("missing") }

        val error = runCatching { useCase(engine)("a red leaf", photo) }.exceptionOrNull()

        assertTrue("Expected ModelNotAvailableException but got $error", error is ModelNotAvailableException)
        assertEquals(1, engine.releaseCalls)
    }

    @Test
    fun `cancelling stops the check and releases the engine`() = runTest {
        val engine = FakeInferenceEngine { _, _ -> awaitCancellation() }
        val job = launch { useCase(engine)("a red leaf", photo) }
        runCurrent()
        assertEquals(1, engine.requests.size)

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(1, engine.releaseCalls)
    }
}
