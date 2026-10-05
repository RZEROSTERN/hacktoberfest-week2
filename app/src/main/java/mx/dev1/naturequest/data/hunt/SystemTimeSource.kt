package mx.dev1.naturequest.data.hunt

import mx.dev1.naturequest.domain.hunt.TimeSource
import javax.inject.Inject

class SystemTimeSource @Inject constructor() : TimeSource {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
