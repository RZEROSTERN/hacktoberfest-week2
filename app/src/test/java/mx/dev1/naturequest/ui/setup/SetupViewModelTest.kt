package mx.dev1.naturequest.ui.setup

import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.PlaceType
import mx.dev1.naturequest.domain.model.ModelAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupViewModelTest {
    private class FakeAvailability(var available: Boolean = true) : ModelAvailability {
        override fun isAvailable() = available

        override fun partialBytes() = 0L
    }

    private val availability = FakeAvailability()

    private fun viewModel() = SetupViewModel(availability)

    @Test
    fun `everything is pre-selected so one tap starts a hunt`() {
        val settings = viewModel().state.value.toSettings()
        assertEquals(HuntSettings(PlaceType.PARK, HuntLength.MEDIUM, AgeRange.AGES_7_TO_9), settings)
    }

    @Test
    fun `each choice updates only its own part of the state`() {
        val vm = viewModel()

        vm.selectPlace(PlaceType.FOREST)
        vm.selectLength(HuntLength.LONG)
        vm.selectAgeRange(AgeRange.AGES_4_TO_6)

        assertEquals(
            HuntSettings(PlaceType.FOREST, HuntLength.LONG, AgeRange.AGES_4_TO_6),
            vm.state.value.toSettings(),
        )
    }

    @Test
    fun `offers the download instead of starting while the model is missing, and notices when it arrives`() {
        availability.available = false
        val vm = viewModel()
        assertEquals(false, vm.state.value.modelReady)

        availability.available = true // the download screen finished
        vm.refreshModel()

        assertEquals(true, vm.state.value.modelReady)
    }
}
