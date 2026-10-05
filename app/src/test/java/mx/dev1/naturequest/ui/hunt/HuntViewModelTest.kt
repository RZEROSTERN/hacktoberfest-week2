package mx.dev1.naturequest.ui.hunt

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.FallbackItems
import mx.dev1.naturequest.domain.hunt.GenerateHuntUseCase
import mx.dev1.naturequest.domain.hunt.HuntGenerationProgress
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.PlaceType
import mx.dev1.naturequest.domain.hunt.SafetyValidator
import mx.dev1.naturequest.domain.inference.ModelJsonParser
import mx.dev1.naturequest.domain.inference.ModelNotAvailableException
import mx.dev1.naturequest.testing.FakeInferenceEngine
import mx.dev1.naturequest.testing.FakePromptSource
import mx.dev1.naturequest.testing.MainDispatcherRule
import mx.dev1.naturequest.testing.huntJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HuntViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = HuntSettings(PlaceType.FOREST, HuntLength.SHORT, AgeRange.AGES_4_TO_6)
    private val fallback = FallbackItems { listOf("Something round", "Something blue", "A tall tree") }

    private fun viewModel(engine: FakeInferenceEngine) = HuntViewModel(
        settings,
        GenerateHuntUseCase(engine, FakePromptSource(), fallback, ModelJsonParser(), SafetyValidator()),
    )

    @Test
    fun `starts generating right away`() = runTest {
        val vm = viewModel(FakeInferenceEngine.scripted(huntJson("a")))
        assertEquals(HuntUiState.Generating(HuntGenerationProgress.LOADING_MODEL), vm.state.value)
    }

    @Test
    fun `shows the list when generation succeeds`() = runTest {
        val engine = FakeInferenceEngine.scripted(
            huntJson("a red leaf", "a feather", "bark with moss", "something round", "a smooth stone", "a tall tree", "x"),
        )
        val vm = viewModel(engine)

        advanceUntilIdle()

        assertEquals(
            HuntUiState.Ready(listOf("A red leaf", "A feather", "Bark with moss", "Something round", "A smooth stone")),
            vm.state.value,
        )
        assertEquals(1, engine.releaseCalls)
    }

    @Test
    fun `reports a missing model`() = runTest {
        val engine = FakeInferenceEngine.scripted("unused").apply { loadError = ModelNotAvailableException("missing") }
        val vm = viewModel(engine)

        advanceUntilIdle()

        assertEquals(HuntUiState.Failed(FailureReason.MODEL_MISSING), vm.state.value)
    }

    @Test
    fun `reports an unexpected error and can retry`() = runTest {
        val engine = FakeInferenceEngine.scripted(
            huntJson("a red leaf", "a feather", "bark with moss", "something round", "a smooth stone", "a tall tree", "x"),
        ).apply { loadError = IllegalStateException("boom") }
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
}
