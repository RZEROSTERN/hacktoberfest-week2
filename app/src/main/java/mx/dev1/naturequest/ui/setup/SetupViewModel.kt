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
import mx.dev1.naturequest.domain.model.ModelAvailability
import javax.inject.Inject

/** The setup form: three choices, all pre-selected so starting takes a single tap. */
data class SetupUiState(
    val place: PlaceType = PlaceType.PARK,
    val length: HuntLength = HuntLength.MEDIUM,
    val ageRange: AgeRange = AgeRange.AGES_7_TO_9,
    /** False until the AI model is on the phone: then the screen offers to download it instead of starting. */
    val modelReady: Boolean = true,
) {
    fun toSettings() = HuntSettings(place, length, ageRange)
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val modelAvailability: ModelAvailability,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState(modelReady = modelAvailability.isAvailable()))
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    /** Call when the screen comes back (for example after the download screen). */
    fun refreshModel() = _state.update { it.copy(modelReady = modelAvailability.isAvailable()) }

    fun selectPlace(place: PlaceType) = _state.update { it.copy(place = place) }

    fun selectLength(length: HuntLength) = _state.update { it.copy(length = length) }

    fun selectAgeRange(ageRange: AgeRange) = _state.update { it.copy(ageRange = ageRange) }
}
