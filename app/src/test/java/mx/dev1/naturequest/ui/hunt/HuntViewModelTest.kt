package mx.dev1.naturequest.ui.hunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.dev1.naturequest.data.hunt.InMemoryHuntSession
import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.FallbackItems
import mx.dev1.naturequest.domain.hunt.GenerateHuntUseCase
import mx.dev1.naturequest.domain.hunt.HuntGenerationProgress
import mx.dev1.naturequest.domain.hunt.HuntItem
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.PlaceType
import mx.dev1.naturequest.domain.hunt.SafetyValidator
import mx.dev1.naturequest.domain.inference.ModelJsonParser
import mx.dev1.naturequest.domain.inference.ModelNotAvailableException
import mx.dev1.naturequest.testing.FakeInferenceEngine
import mx.dev1.naturequest.testing.FakeHuntTexts
import mx.dev1.naturequest.testing.FakePhotoStore
import mx.dev1.naturequest.testing.FakePromptSource
import mx.dev1.naturequest.testing.FakeSpeaker
import mx.dev1.naturequest.testing.FakeTimeSource
import mx.dev1.naturequest.testing.MainDispatcherRule
import mx.dev1.naturequest.testing.huntJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HuntViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = HuntSettings(PlaceType.FOREST, HuntLength.SHORT, AgeRange.AGES_4_TO_6)
    private val fallback = FallbackItems { listOf("Something round", "Something blue", "A tall tree") }
    private val session = InMemoryHuntSession(FakeTimeSource())
    private val speaker = FakeSpeaker()
    private val photoStore = FakePhotoStore()
    private val goodAnswer =
        huntJson("a red leaf", "a feather", "bark with moss", "something round", "a smooth stone", "a tall tree", "x")

    private fun viewModel(engine: FakeInferenceEngine) = HuntViewModel(
        settings,
        GenerateHuntUseCase(engine, FakePromptSource(), fallback, ModelJsonParser(), SafetyValidator()),
        session,
        photoStore,
        speaker,
        FakeHuntTexts(),
    )

    @Test
    fun `starts generating right away`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(huntJson("a")))
        assertEquals(HuntUiState.Generating(HuntGenerationProgress.LOADING_MODEL), vm.state.value)
    }

    @Test
    fun `shows the checklist and starts the hunt session when generation succeeds`() = runTest {
        val engine = FakeInferenceEngine.scripted(goodAnswer)
        val vm = viewModel(engine)

        advanceUntilIdle()

        val expected = listOf("A red leaf", "A feather", "Bark with moss", "Something round", "A smooth stone")
            .mapIndexed { index, text -> HuntItem(index, text) }
        assertEquals(HuntUiState.Ready(expected), vm.state.value)
        assertEquals(settings, session.state.value!!.settings)
        assertEquals(1, engine.releaseCalls)
    }

    @Test
    fun `the checklist updates when an item is found`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(goodAnswer))
        advanceUntilIdle()

        session.markFound(1, "/cache/item-1.jpg")
        advanceUntilIdle()

        val ready = vm.state.value as HuntUiState.Ready
        assertEquals(listOf(false, true, false, false, false), ready.items.map { it.found })
    }

    @Test
    fun `reports a missing model`() = runTest {
        val engine = FakeInferenceEngine.scripted("unused").apply { loadError = ModelNotAvailableException("missing") }
        val vm = viewModel(engine)

        advanceUntilIdle()

        assertEquals(HuntUiState.Failed(FailureReason.MODEL_MISSING), vm.state.value)
        assertNull(session.state.value)
    }

    @Test
    fun `reports an unexpected error and can retry`() = runTest {
        val engine = FakeInferenceEngine.scripted(goodAnswer).apply { loadError = IllegalStateException("boom") }
        val vm = viewModel(engine)
        advanceUntilIdle()
        assertEquals(HuntUiState.Failed(FailureReason.GENERIC), vm.state.value)

        engine.loadError = null
        vm.generate()
        advanceUntilIdle()

        assertTrue(vm.state.value is HuntUiState.Ready)
    }

    @Test
    fun `cancel stops generation and frees the model`() = runTest {
        val engine = FakeInferenceEngine { _, _ -> awaitCancellation() }
        val vm = viewModel(engine)
        advanceUntilIdle()
        assertTrue(vm.state.value is HuntUiState.Generating)

        vm.cancel()
        advanceUntilIdle()

        assertEquals(1, engine.releaseCalls)
        assertTrue(vm.state.value is HuntUiState.Generating) // no result, no error
    }

    private fun clearViewModel(vm: HuntViewModel) {
        // Clearing the ViewModelStore is what the navigation back stack does when the screen is left.
        val store = ViewModelStore()
        ViewModelProvider.create(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T =
                vm as T
        })[HuntViewModel::class]
        store.clear()
    }

    @Test
    fun `reads the list aloud as soon as it is ready`() = runTest {
        viewModel(FakeInferenceEngine.scripted(goodAnswer))
        advanceUntilIdle()

        assertEquals(
            listOf("READY: A red leaf|A feather|Bark with moss|Something round|A smooth stone"),
            speaker.spoken,
        )
    }

    @Test
    fun `can read the list again and stop reading`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(goodAnswer))
        advanceUntilIdle()

        vm.readAloud()
        assertEquals(2, speaker.spoken.size)
        assertTrue(vm.speaking.value)

        vm.stopReading()
        assertTrue(!vm.speaking.value)
    }

    @Test
    fun `finishing stops the voice and the clock but keeps the hunt for the summary`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(goodAnswer))
        advanceUntilIdle()
        session.markFound(0, "/cache/item-0.jpg")

        vm.finish()

        assertTrue(session.state.value!!.finished)
        assertEquals(1, session.state.value!!.foundCount)
        assertEquals(1, speaker.stopCalls)
    }

    @Test
    fun `leaving a finished hunt keeps it for the summary and does not cut the summary speech`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(goodAnswer))
        advanceUntilIdle()
        photoStore.saved[0] = byteArrayOf(1)
        vm.finish()
        val stopsBefore = speaker.stopCalls

        clearViewModel(vm)

        assertTrue(session.state.value != null)
        assertEquals(1, photoStore.saved.size)
        assertEquals(stopsBefore, speaker.stopCalls)
        assertEquals(0, speaker.releaseCalls)
    }

    @Test
    fun `leaving an unfinished hunt forgets it, deletes the photos and silences the voice`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(goodAnswer))
        advanceUntilIdle()
        photoStore.saved[0] = byteArrayOf(1)
        assertTrue(session.state.value != null)

        clearViewModel(vm)

        assertNull(session.state.value)
        assertTrue(photoStore.saved.isEmpty())
        assertEquals(1, photoStore.clearCalls)
        assertEquals(1, speaker.releaseCalls)
    }
}
