package mx.dev1.naturequest.data.verification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.dev1.naturequest.R
import mx.dev1.naturequest.domain.verification.VerificationFallbacks
import javax.inject.Inject

/** Safe feedback texts from string resources, so they follow the device language. */
class ResourceVerificationFallbacks @Inject constructor(
    @ApplicationContext private val context: Context,
) : VerificationFallbacks {
    override fun notSure(): String = context.getString(R.string.verify_fallback_not_sure)

    override fun genericMatch(): String = context.getString(R.string.verify_fallback_match)

    override fun genericMiss(): String = context.getString(R.string.verify_fallback_miss)
}
