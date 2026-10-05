package mx.dev1.naturequest.ui.setup

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mx.dev1.naturequest.domain.hunt.AgeRange
import mx.dev1.naturequest.domain.hunt.HuntLength
import mx.dev1.naturequest.domain.hunt.HuntSettings
import mx.dev1.naturequest.domain.hunt.PlaceType
import javax.inject.Inject

/** The setup form: three choices, all pre-selected so starting takes a single tap. */
data class SetupUiState(
    val place: PlaceType = PlaceType.PARK,
    val length: HuntLength = HuntLength.MEDIUM,
    val ageRange: AgeRange = AgeRange.AGES_7_TO_9,
) {
    fun toSettings() = HuntSettings(place, length, ageRange)
}

@HiltViewModel
class SetupViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun selectPlace(place: PlaceType) = _state.update { it.copy(place = place) }

    fun selectLength(length: HuntLength) = _state.update { it.copy(length = length) }

    fun selectAgeRange(ageRange: AgeRange) = _state.update { it.copy(ageRange = ageRange) }
}
