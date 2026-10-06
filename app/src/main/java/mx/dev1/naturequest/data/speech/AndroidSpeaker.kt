package mx.dev1.naturequest.data.speech

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.dev1.naturequest.domain.language.AppLanguage
import mx.dev1.naturequest.domain.speech.Speaker
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [Speaker] backed by Android's TextToSpeech. The engine is started on the first [speak] and shut
 * down by [release]. It speaks in the device language, prefers a voice that works offline, and stays
 * silent (the screen still shows everything) if the phone has no voice for the language.
 */
@Singleton
class AndroidSpeaker @Inject constructor(
    @ApplicationContext private val context: Context,
) : Speaker {
    private val lock = Any()
    private var engine: TextToSpeech? = null
    private var ready = false
    private var unavailable = false
    private var pending: String? = null

    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    override fun speak(text: String) {
        synchronized(lock) {
            if (unavailable) return
            if (engine == null) start()
            if (ready) say(text) else pending = text
        }
    }

    override fun stop() {
        synchronized(lock) {
            pending = null
            engine?.stop()
            _speaking.value = false
        }
    }

    override fun release() {
        synchronized(lock) {
            pending = null
            engine?.shutdown()
            engine = null
            ready = false
            unavailable = false
            _speaking.value = false
        }
    }

    private fun start() {
        engine = TextToSpeech(context) { status -> onInit(status) }.also {
            it.setOnUtteranceProgressListener(progressListener)
            it.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        }
    }

    private fun onInit(status: Int) = synchronized(lock) {
        val tts = engine ?: return@synchronized
        if (status != TextToSpeech.SUCCESS || !selectLanguage(tts)) {
            Log.w(TAG, "No speech available (init status=$status, locale=${Locale.getDefault()})")
            unavailable = true
            pending = null
            return@synchronized
        }
        preferOfflineVoice(tts)
        ready = true
        pending?.let { say(it) }
        pending = null
    }

    /**
     * Speaks the app's language: the device language (keeping its region, e.g. es-MX) when the app supports
     * it, otherwise the fallback language, so the voice always matches the text on screen.
     */
    private fun selectLanguage(tts: TextToSpeech): Boolean {
        val device = Locale.getDefault()
        val language = AppLanguage.resolve(device)
        val base = Locale.forLanguageTag(language.code)
        val preferred = if (device.language == language.code) device else base
        return listOf(preferred, base).any { locale ->
            when (tts.setLanguage(locale)) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> false
                else -> true
            }
        }
    }

    /** Photos and hunts never leave the phone, and neither should a network speech request. */
    private fun preferOfflineVoice(tts: TextToSpeech) {
        val current = tts.voice
        if (current != null && !current.isNetworkConnectionRequired) return
        val language = AppLanguage.resolve(Locale.getDefault()).code
        tts.voices
            ?.filter { it.locale.language == language && !it.isNetworkConnectionRequired }
            ?.maxByOrNull { it.quality }
            ?.let { tts.voice = it }
    }

    private fun say(text: String) {
        val limit = TextToSpeech.getMaxSpeechInputLength()
        val result = engine?.speak(text.take(limit), TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result != TextToSpeech.SUCCESS) Log.w(TAG, "speak() failed with $result")
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Log.d(TAG, "speaking")
            _speaking.value = true
        }

        override fun onDone(utteranceId: String?) {
            Log.d(TAG, "done speaking")
            _speaking.value = false
        }

        @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, errorCode)"))
        override fun onError(utteranceId: String?) {
            _speaking.value = false
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.w(TAG, "speech error $errorCode")
            _speaking.value = false
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            _speaking.value = false
        }
    }

    private companion object {
        const val TAG = "NQ_SPEECH"
        const val UTTERANCE_ID = "nature-quest"
    }
}
