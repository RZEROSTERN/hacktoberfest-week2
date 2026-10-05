package mx.dev1.naturequest.data.hunt

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.dev1.naturequest.R
import mx.dev1.naturequest.domain.hunt.FallbackItems
import javax.inject.Inject

/** The built-in safe hunt items, read from string resources so they follow the device language. */
class ResourceFallbackItems @Inject constructor(
    @ApplicationContext private val context: Context,
) : FallbackItems {
    override fun items(): List<String> =
        context.resources.getStringArray(R.array.fallback_hunt_items).toList()
}
