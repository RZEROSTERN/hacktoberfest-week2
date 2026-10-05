package mx.dev1.naturequest.domain.inference

import kotlinx.serialization.Serializable

/** Strict JSON the model must return when generating a hunt list. */
@Serializable
data class HuntListResponse(
    val items: List<String>,
)

/** Strict JSON the model must return when verifying a photo against a hunt item. */
@Serializable
data class VerificationResponse(
    val match: Boolean,
    val message: String,
    val hint: String = "",
)
