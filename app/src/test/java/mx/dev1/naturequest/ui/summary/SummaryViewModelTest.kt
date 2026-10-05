package mx.dev1.naturequest.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.dev1.naturequest.data.hunt.InMemoryHuntSession
import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.Medal
import mx.dev1.naturequest.domain.hunt.PlaceType
import mx.dev1.naturequest.testing.FakeGallerySaver
import mx.dev1.naturequest.testing.FakeHuntTexts
import mx.dev1.naturequest.testing.FakePhotoStore
import mx.dev1.naturequest.testing.FakeSpeaker
import mx.dev1.naturequest.testing.FakeTimeSource
import mx.dev1.naturequest.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SummaryViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val clock = FakeTimeSource()
    private val session = InMemoryHuntSession(clock)
    private val photoStore = FakePhotoStore()
    private val speaker = FakeSpeaker()
    private val settings = HuntSettings(PlaceType.PARK, HuntLength.SHORT, AgeRange.AGES_7_TO_9)

    private fun finishedHunt(found: List<Int>, minutes: Long = 23) {
        session.start(settings, listOf("A red leaf", "A feather", "Bark", "Something round", "A stone"))
        found.forEach { session.markFound(it, "/cache/item-$it.jpg") }
        clock.now += minutes * 60_000L + 5_000L
        session.finish()
    }

    private fun viewModel(gallery: FakeGallerySaver = FakeGallerySaver()) =
        SummaryViewModel(session, photoStore, speaker, FakeHuntTexts(), gallery)

    private fun SummaryViewModel.ready() = state.value as SummaryUiState.Ready

    @Test
    fun `shows the result, the time outside and the medal`() = runTest {
        finishedHunt(found = listOf(0, 1, 2))

        val ready = viewModel().ready()

        assertEquals("found 3 of 5", ready.foundText)
        assertEquals("23 min outside", ready.timeText)
        assertEquals(Medal.SILVER, ready.medal)
        assertEquals("medal silver", ready.medalName)
        assertEquals(listOf(true, true, true, false, false), ready.items.map { it.found })
        assertEquals(listOf("/cache/item-0.jpg", "/cache/item-1.jpg", "/cache/item-2.jpg"), ready.photoPaths)
    }

    @Test
    fun `finding everything is gold`() = runTest {
        finishedHunt(found = listOf(0, 1, 2, 3, 4))
        assertEquals(Medal.GOLD, viewModel().ready().medal)
    }

    @Test
    fun `going out and finding nothing still earns bronze`() = runTest {
        finishedHunt(found = emptyList(), minutes = 0)

        val ready = viewModel().ready()

        assertEquals(Medal.BRONZE, ready.medal)
        assertEquals("0 min outside", ready.timeText)
        assertTrue(ready.photoPaths.isEmpty())
    }

    @Test
    fun `reads the summary aloud`() = runTest {
        finishedHunt(found = listOf(0, 1))
        viewModel()
        assertEquals(listOf("SUMMARY 2/5 23min BRONZE"), speaker.spoken)
    }

    @Test
    fun `with no hunt it shows an empty state and says nothing`() = runTest {
        val vm = viewModel()
        assertEquals(SummaryUiState.Empty, vm.state.value)
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun `nothing is saved to the gallery unless the player asks`() = runTest {
        finishedHunt(found = listOf(0, 1))
        val gallery = FakeGallerySaver()

        val vm = viewModel(gallery)
        advanceUntilIdle()

        assertTrue(gallery.requests.isEmpty())
        assertEquals(SaveState.Idle, vm.ready().save)
    }

    @Test
    fun `save to gallery sends exactly the photos of the finds`() = runTest {
        finishedHunt(found = listOf(1, 3))
        val gallery = FakeGallerySaver()
        val vm = viewModel(gallery)

        vm.saveToGallery()
        advanceUntilIdle()

        assertEquals(listOf(listOf("/cache/item-1.jpg", "/cache/item-3.jpg")), gallery.requests)
        assertEquals(SaveState.Saved(2), vm.ready().save)
    }

    @Test
    fun `saving twice does not save twice`() = runTest {
        finishedHunt(found = listOf(0))
        val gallery = FakeGallerySaver()
        val vm = viewModel(gallery)

        vm.saveToGallery()
        advanceUntilIdle()
        vm.saveToGallery()
        advanceUntilIdle()

        assertEquals(1, gallery.requests.size)
    }

    @Test
    fun `a failed save can be tried again`() = runTest {
        finishedHunt(found = listOf(0))
        var attempt = 0
        val gallery = FakeGallerySaver { if (++attempt == 1) 0 else it.size }
        val vm = viewModel(gallery)

        vm.saveToGallery()
        advanceUntilIdle()
        assertEquals(SaveState.Failed, vm.ready().save)

        vm.saveToGallery()
        advanceUntilIdle()
        assertEquals(SaveState.Saved(1), vm.ready().save)
    }

    @Test
    fun `an error while saving is reported, not thrown`() = runTest {
        finishedHunt(found = listOf(0))
        val vm = viewModel(FakeGallerySaver { throw IllegalStateException("disk full") })

        vm.saveToGallery()
        advanceUntilIdle()

        assertEquals(SaveState.Failed, vm.ready().save)
    }

    @Test
    fun `there is nothing to save when no photos were kept`() = runTest {
        finishedHunt(found = emptyList())
        val gallery = FakeGallerySaver()
        val vm = viewModel(gallery)

        vm.saveToGallery()
        advanceUntilIdle()

        assertTrue(gallery.requests.isEmpty())
    }

    @Test
    fun `closing the summary forgets the hunt, deletes the cached photos and frees the voice`() = runTest {
        finishedHunt(found = listOf(0))
        photoStore.saved[0] = byteArrayOf(1)
        val vm = viewModel()

        val store = ViewModelStore()
        ViewModelProvider.create(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T =
                vm as T
        })[SummaryViewModel::class]
        store.clear()

        assertNull(session.state.value)
        assertTrue(photoStore.saved.isEmpty())
        assertEquals(1, speaker.releaseCalls)
    }
}
