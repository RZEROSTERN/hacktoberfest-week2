package mx.dev1.naturequest.data.model

/**
 * What to download and how to check it. The URL is pinned to a specific revision of the Hugging Face
 * repo (not `main`) so the size and SHA-256 below stay true even if the repo is updated. The repo is
 * public and not gated: no token is needed, and none is embedded in the app.
 */
data class ModelSpec(
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    companion object {
        val GEMMA_4_E2B = ModelSpec(
            fileName = ModelStore.MODEL_FILE_NAME,
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
                "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm",
            sizeBytes = 2_588_147_712L,
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        )
    }
}
