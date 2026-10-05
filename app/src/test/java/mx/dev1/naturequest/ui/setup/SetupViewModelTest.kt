package mx.dev1.naturequest.ui.setup

import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupViewModelTest {
    @Test
    fun `everything is pre-selected so one tap starts a hunt`() {
        val settings = SetupViewModel().state.value.toSettings()
        assertEquals(HuntSettings(PlaceType.PARK, HuntLength.MEDIUM, AgeRange.AGES_7_TO_9), settings)
    }

    @Test
    fun `each choice updates only its own part of the state`() {
        val vm = SetupViewModel()

        vm.selectPlace(PlaceType.FOREST)
        vm.selectLength(HuntLength.LONG)
        vm.selectAgeRange(AgeRange.AGES_4_TO_6)

        assertEquals(
            HuntSettings(PlaceType.FOREST, HuntLength.LONG, AgeRange.AGES_4_TO_6),
            vm.state.value.toSettings(),
        )
    }
}
