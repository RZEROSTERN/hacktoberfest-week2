package mx.dev1.naturequest.ui.verify

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.dev1.naturequest.data.hunt.InMemoryHuntSession
import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.PlaceType
import mx.dev1.naturequest.domain.hunt.SafetyValidator
import mx.dev1.naturequest.domain.inference.ModelJsonParser
import mx.dev1.naturequest.domain.inference.ModelNotAvailableException
import mx.dev1.naturequest.domain.verification.VerificationOutcome
import mx.dev1.naturequest.domain.verification.VerifyPhotoUseCase
import mx.dev1.naturequest.testing.FakeInferenceEngine
import mx.dev1.naturequest.testing.FakePhotoPreprocessor
import mx.dev1.naturequest.testing.FakeSpeaker
import mx.dev1.naturequest.testing.FakeTimeSource
import mx.dev1.naturequest.testing.FakePhotoStore
import mx.dev1.naturequest.testing.FakePromptSource
import mx.dev1.naturequest.testing.FakeVerificationFallbacks
import mx.dev1.naturequest.testing.MainDispatcherRule
import mx.dev1.naturequest.testing.verificationJson
import mx.dev1.naturequest.ui.hunt.FailureReason
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VerifyViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val speaker = FakeSpeaker()
    private val session = InMemoryHuntSession(FakeTimeSource()).apply {
        start(HuntSettings(PlaceType.PARK, HuntLength.SHORT, AgeRange.AGES_7_TO_9), listOf("A red leaf", "A feather"))
    }
    private val preprocessor = FakePhotoPreprocessor()
    private val photoStore = FakePhotoStore()

    private fun viewModel(engine: FakeInferenceEngine, itemId: Int = 0) = VerifyViewModel(
        itemId,
        session,
        preprocessor,
        VerifyPhotoUseCase(engine, FakePromptSource(), ModelJsonParser(), SafetyValidator(), FakeVerificationFallbacks()),
        photoStore,
        speaker,
    )

    @Test
    fun `starts at the camera and knows which item is being looked for`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted("unused"), itemId = 1)
        assertEquals(VerifyUiState.Capturing, vm.state.value)
        assertEquals("A feather", vm.itemText)
    }

    @Test
    fun `a matching photo checks the item off and keeps the small photo`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(verificationJson(true, "Great red leaf!")))

        vm.onPhotoCaptured(raw = byteArrayOf(5, 5, 5), rotationDegrees = 90)
        advanceUntilIdle()

        val result = vm.state.value as VerifyUiState.Result
        assertEquals(VerificationOutcome.MATCH, result.verification.outcome)
        assertTrue(session.state.value!!.items[0].found)
        assertFalse(session.state.value!!.items[1].found)
        assertEquals("/cache/item-0.jpg", session.state.value!!.items[0].photoPath)
        // The camera rotation reaches the preprocessor, and only the shrunk photo is stored.
        assertEquals(90, preprocessor.calls.single().second)
        assertArrayEquals(preprocessor.prepared, photoStore.saved.getValue(0))
    }

    @Test
    fun `a photo that does not match leaves the item unchecked and stores nothing`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(verificationJson(false, "That is a stone.", "Look up!")))

        vm.onPhotoCaptured(byteArrayOf(1), 0)
        advanceUntilIdle()

        val result = vm.state.value as VerifyUiState.Result
        assertEquals(VerificationOutcome.NO_MATCH, result.verification.outcome)
        assertEquals("Look up!", result.verification.hint)
        assertTrue(session.state.value!!.items.none { it.found })
        assertTrue(photoStore.saved.isEmpty())
    }

    @Test
    fun `an unsure answer does not check the item off either`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted("garbage", "more garbage"))

        vm.onPhotoCaptured(byteArrayOf(1), 0)
        advanceUntilIdle()

        val result = vm.state.value as VerifyUiState.Result
        assertEquals(VerificationOutcome.NOT_SURE, result.verification.outcome)
        assertTrue(session.state.value!!.items.none { it.found })
    }

    @Test
    fun `shows the photo while it is being checked`() = runTest {
        val vm = viewModel(FakeInferenceEngine { _, _ -> awaitCancellation() })

        vm.onPhotoCaptured(byteArrayOf(1), 0)
        advanceUntilIdle()

        val checking = vm.state.value as VerifyUiState.Checking
        assertArrayEquals(preprocessor.prepared, checking.photo)
    }

    @Test
    fun `cancel stops checking, frees the model and returns to the camera`() = runTest {
        val engine = FakeInferenceEngine { _, _ -> awaitCancellation() }
        val vm = viewModel(engine)
        vm.onPhotoCaptured(byteArrayOf(1), 0)
        advanceUntilIdle()

        vm.cancel()
        advanceUntilIdle()

        assertEquals(VerifyUiState.Capturing, vm.state.value)
        assertEquals(1, engine.releaseCalls)
        assertTrue(session.state.value!!.items.none { it.found })
    }

    @Test
    fun `keeps quiet while the camera is open and reads the feedback aloud`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(verificationJson(false, "That is a stone.", "Look up!")))
        assertEquals(1, speaker.stopCalls) // silence on entering the camera

        vm.onPhotoCaptured(byteArrayOf(1), 0)
        advanceUntilIdle()

        assertEquals(listOf("That is a stone. Look up!"), speaker.spoken)
    }

    @Test
    fun `a match is read aloud without a hint`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(verificationJson(true, "Great red leaf!", "ignored")))

        vm.onPhotoCaptured(byteArrayOf(1), 0)
        advanceUntilIdle()

        assertEquals(listOf("Great red leaf!"), speaker.spoken)
    }

    @Test
    fun `cancel and retake silence the voice`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(verificationJson(false, "Not a leaf.")))
        vm.onPhotoCaptured(byteArrayOf(1), 0)
        advanceUntilIdle()
        val before = speaker.stopCalls

        vm.retake()
        vm.cancel()

        assertEquals(before + 2, speaker.stopCalls)
    }

    @Test
    fun `retake goes back to the camera`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(verificationJson(false, "Not a leaf.")))
        vm.onPhotoCaptured(byteArrayOf(1), 0)
        advanceUntilIdle()

        vm.retake()

        assertEquals(VerifyUiState.Capturing, vm.state.value)
    }

    @Test
    fun `reports a missing model`() = runTest {
        val engine = FakeInferenceEngine.scripted("unused").apply { loadError = ModelNotAvailableException("missing") }
        val vm = viewModel(engine)

        vm.onPhotoCaptured(byteArrayOf(1), 0)
        advanceUntilIdle()

        assertEquals(VerifyUiState.Failed(FailureReason.MODEL_MISSING), vm.state.value)
    }

    @Test
    fun `reports an unexpected error`() = runTest {
        val engine = FakeInferenceEngine.scripted("unused").apply { loadError = IllegalStateException("boom") }
        val vm = viewModel(engine)

        vm.onPhotoCaptured(byteArrayOf(1), 0)
        advanceUntilIdle()

        assertEquals(VerifyUiState.Failed(FailureReason.GENERIC), vm.state.value)
    }
}
