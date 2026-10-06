package mx.dev1.naturequest.domain.hunt

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.dev1.naturequest.domain.inference.ModelJsonParser
import mx.dev1.naturequest.domain.inference.ModelNotAvailableException
import mx.dev1.naturequest.testing.FakeInferenceEngine
import mx.dev1.naturequest.testing.FakePromptSource
import mx.dev1.naturequest.testing.huntJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class GenerateHuntUseCaseTest {
    private val validator = SafetyValidator()
    private val prompts = FakePromptSource()
    private val settings = HuntSettings(PlaceType.PARK, HuntLength.SHORT, AgeRange.AGES_7_TO_9) // 5 items
    private val fallback = FallbackItems {
        listOf(
            "Something round", "Something blue", "A tall tree", "A gray stone",
            "A yellow leaf", "Something striped", "A curved branch", "Something shiny",
        )
    }

    private fun useCase(engine: FakeInferenceEngine) =
        GenerateHuntUseCase(engine, prompts, fallback, ModelJsonParser(), validator)

    @Test
    fun `returns the requested number of valid items from the first answer`() = runTest {
        val engine = FakeInferenceEngine.scripted(
            huntJson("a red leaf", "a feather", "bark with moss", "something round", "a smooth stone", "a tall tree", "an extra"),
        )
        val result = useCase(engine)(settings)

        assertEquals(5, result.items.size)
        assertEquals(1, result.attempts)
        assertEquals(0, result.fallbackCount)
        assertEquals("A red leaf", result.items.first())
        assertEquals(1, engine.loadCalls)
        assertEquals(1, engine.releaseCalls)
    }

    @Test
    fun `rejects unsafe items and asks again, telling the model what to avoid`() = runTest {
        val engine = FakeInferenceEngine.scripted(
            huntJson("a red leaf", "a coiled snake", "pick a flower", "a feather", "orange fungus", "bark with moss", "something round"),
            huntJson("a smooth stone", "a tall tree"),
        )
        val result = useCase(engine)(settings)

        assertEquals(listOf("A red leaf", "A feather", "Bark with moss", "Something round", "A smooth stone"), result.items)
        assertEquals(2, result.attempts)
        assertEquals(0, result.fallbackCount)
        assertEquals(
            listOf("a coiled snake", "pick a flower", "orange fungus"),
            result.rejected.map { it.text },
        )
        // The second prompt asks only for what is missing (+2 spare) and lists what to avoid.
        assertEquals("3", prompts.rendered[1].second["count"])
        val avoid = prompts.rendered[1].second.getValue("avoid")
        assertTrue(avoid.contains("\"A red leaf\"") && avoid.contains("\"a coiled snake\""))
    }

    @Test
    fun `removes repeated items`() = runTest {
        val engine = FakeInferenceEngine.scripted(
            huntJson("a red leaf", "A RED LEAF!", "a feather", "a feather", "bark", "round stone", "a tall tree"),
        )
        val result = useCase(engine)(settings)

        assertEquals(result.items.map { it.lowercase() }.distinct().size, result.items.size)
        assertEquals(5, result.items.size)
    }

    @Test
    fun `retries once after invalid JSON`() = runTest {
        val engine = FakeInferenceEngine.scripted(
            "Sorry, I cannot do that.",
            huntJson("a red leaf", "a feather", "bark with moss", "something round", "a smooth stone", "a tall tree", "x"),
        )
        val result = useCase(engine)(settings)

        assertEquals(2, result.attempts)
        assertEquals(0, result.fallbackCount)
        assertEquals(5, result.items.size)
    }

    @Test
    fun `falls back to the safe list when the model keeps returning invalid JSON`() = runTest {
        val engine = FakeInferenceEngine.scripted("not json", "still not json")
        val result = useCase(engine)(settings)

        assertEquals(2, engine.requests.size) // one retry, then stop
        assertEquals(5, result.items.size)
        assertEquals(5, result.fallbackCount)
        assertEquals(listOf("Something round", "Something blue", "A tall tree", "A gray stone", "A yellow leaf"), result.items)
    }

    @Test
    fun `treats a native engine error like invalid output`() = runTest {
        val engine = FakeInferenceEngine { _, _ -> throw IllegalStateException("native failure") }
        val result = useCase(engine)(settings)

        assertEquals(5, result.items.size)
        assertEquals(5, result.fallbackCount)
        assertEquals(1, engine.releaseCalls)
    }

    @Test
    fun `fills the gap from the fallback list when the model only gives unsafe items`() = runTest {
        val engine = FakeInferenceEngine.scripted(huntJson("a snake", "a spider", "a red car", "pick a berry"))
        val result = useCase(engine)(settings)

        assertEquals(3, result.attempts) // the cap
        assertEquals(5, result.items.size)
        assertEquals(5, result.fallbackCount)
        assertTrue(result.items.all { validator.isSafe(it) })
    }

    @Test
    fun `mixes model items with fallback items when it is only a little short`() = runTest {
        val engine = FakeInferenceEngine.scripted(huntJson("a red leaf", "a feather", "a snake"))
        val result = useCase(engine)(settings)

        assertEquals(5, result.items.size)
        assertEquals(listOf("A red leaf", "A feather"), result.items.take(2))
        assertEquals(3, result.fallbackCount)
    }

    @Test
    fun `never returns an item that fails the validator`() = runTest {
        val engine = FakeInferenceEngine.scripted(
            huntJson("a snake", "a red leaf", "touch the bark", "a lake", "a feather", "a bus", "bark", "a tall tree", "something round"),
        )
        val result = useCase(engine)(settings)

        assertTrue(result.items.all { validator.isSafe(it) })
        assertFalse(result.items.any { it.contains("snake", ignoreCase = true) })
    }

    @Test
    fun `sends the place, month, age, device language and item count in the prompt`() = runTest {
        val engine = FakeInferenceEngine.scripted(huntJson("a", "b", "c", "d", "e", "f", "g"))
        useCase(engine)(
            HuntSettings(PlaceType.URBAN_WALK, HuntLength.LONG, AgeRange.AGES_4_TO_6),
            locale = Locale.forLanguageTag("es-MX"),
            date = LocalDate.of(2026, 10, 5),
        )

        val (name, values) = prompts.rendered.first()
        assertEquals("hunt_generation_v2", name)
        assertEquals("urban walk", values["place"])
        assertEquals("October", values["month"])
        assertEquals("4-6", values["age_range"])
        assertEquals("Spanish", values["language"])
        assertEquals("14", values["count"]) // 12 items + 2 spare
        assertEquals("nothing yet", values["avoid"])
    }

    @Test
    fun `uses a different sampling seed for every request so hunts vary`() = runTest {
        val engine = FakeInferenceEngine.scripted("not json", "not json again") // forces two requests
        useCase(engine)(settings)
        useCase(engine)(settings)

        val seeds = engine.requests.map { it.seed }
        assertEquals(4, seeds.size)
        assertEquals("Seeds must not repeat: $seeds", seeds.size, seeds.distinct().size)
    }

    @Test
    fun `asks for English on a phone whose language the app does not support`() = runTest {
        // The screens fall back to English there, and the safety word lists only know English and Spanish,
        // so the model must not be asked to write in French.
        val engine = FakeInferenceEngine.scripted(huntJson("a", "b", "c", "d", "e", "f", "g"))

        useCase(engine)(settings, locale = Locale.forLanguageTag("fr-FR"))

        assertEquals("English", prompts.rendered.first().second["language"])
    }

    @Test
    fun `reports progress while loading and writing`() = runTest {
        val progress = mutableListOf<HuntGenerationProgress>()
        useCase(FakeInferenceEngine.scripted(huntJson("a", "b", "c", "d", "e", "f", "g")))(
            settings, onProgress = { progress += it },
        )
        assertEquals(listOf(HuntGenerationProgress.LOADING_MODEL, HuntGenerationProgress.WRITING_LIST), progress)
    }

    @Test
    fun `a missing model fails clearly and still releases the engine`() = runTest {
        val engine = FakeInferenceEngine.scripted("unused").apply { loadError = ModelNotAvailableException("missing") }

        val error = runCatching { useCase(engine)(settings) }.exceptionOrNull()

        assertTrue("Expected ModelNotAvailableException but got $error", error is ModelNotAvailableException)
        assertEquals(1, engine.releaseCalls)
    }

    @Test
    fun `cancelling stops generation and releases the engine`() = runTest {
        val engine = FakeInferenceEngine { _, _ -> awaitCancellation() }
        val job = launch { useCase(engine)(settings) }
        runCurrent() // let it reach the model call
        assertEquals(1, engine.requests.size)

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(1, engine.releaseCalls)
    }
}
