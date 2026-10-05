package mx.dev1.naturequest.domain.speech

import kotlinx.coroutines.flow.StateFlow
import mx.dev1.naturequest.domain.hunt.Medal

/** Reads text aloud with the phone's own speech engine. Silent if the language has no voice. */
interface Speaker {
    val speaking: StateFlow<Boolean>

    /** Says [text], replacing anything already being said. */
    fun speak(text: String)

    fun stop()

    /** Frees the speech engine. The next [speak] starts it again. */
    fun release()
}

/** Texts the hunt shows and says, in the device language (from string resources). */
interface HuntTexts {
    /** Invitation read when the hunt list is ready: the items, then "put the phone away". */
    fun huntReadySpeech(items: List<String>): String

    fun summarySpeech(found: Int, total: Int, minutesOutside: Int, medal: Medal): String

    fun timeOutside(minutesOutside: Int): String

    fun foundOf(found: Int, total: Int): String

    fun medalName(medal: Medal): String
}
