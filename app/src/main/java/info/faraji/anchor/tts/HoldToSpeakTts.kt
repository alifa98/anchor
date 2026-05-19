package info.faraji.anchor.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * SAFETY-CRITICAL: this TTS will refuse to speak unless [permitSpeak] is true.
 *
 * The Anchor app's anti-hallucination contract is: "the AI only ever talks
 * to you when you are actively pressing the Listen button and the screen is
 * green." This class enforces the audio half of that contract — the UI
 * enforces the visual half by toggling [permitSpeak] on press / release.
 *
 * On release, any in-flight utterance is stopped immediately so the voice
 * cannot continue past the visual anchor.
 */
class HoldToSpeakTts(context: Context) {

    private val tag = "HoldToSpeakTts"
    private var tts: TextToSpeech? = null
    @Volatile private var ready: Boolean = false

    private val _permitSpeak = MutableStateFlow(false)
    val permitSpeak: StateFlow<Boolean> = _permitSpeak.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = (status == TextToSpeech.SUCCESS)
            if (ready) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }
                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }
                })
            } else {
                Log.w(tag, "TTS init failed: $status")
            }
        }
    }

    /**
     * Called by the UI when the user presses or releases the green button.
     * Releasing immediately stops any in-flight utterance.
     */
    fun setPermitSpeak(allowed: Boolean) {
        val previous = _permitSpeak.value
        _permitSpeak.value = allowed
        if (previous && !allowed) {
            stopNow()
        }
    }

    /**
     * Queue text to be spoken. If [permitSpeak] is false, the call is dropped
     * silently — there is no buffer of pending speech to leak out later.
     */
    fun speak(text: String) {
        if (!ready) {
            Log.w(tag, "speak() dropped: TTS not ready")
            return
        }
        if (!_permitSpeak.value) {
            Log.d(tag, "speak() dropped: not permitted (button not held)")
            return
        }
        if (text.isBlank()) return

        val utteranceId = "anchor-${System.nanoTime()}"
        Log.d(tag, "Speaking: ${text.take(20)}...")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /** Hard stop. Used on release of the Listen button and on shutdown. */
    fun stopNow() {
        runCatching { tts?.stop() }
        _isSpeaking.value = false
    }

    fun shutdown() {
        stopNow()
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }
}
