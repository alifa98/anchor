package info.faraji.anchor.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import info.faraji.anchor.AnchorApp
import info.faraji.anchor.audio.AudioCaptureService
import info.faraji.anchor.data.UserPrefs
import info.faraji.anchor.model.EngineState
import info.faraji.anchor.tts.HoldToSpeakTts
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app: AnchorApp = application as AnchorApp
    private val tts: HoldToSpeakTts = app.tts

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    val engineState: StateFlow<EngineState> = app.engineHolder.state

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

    private val _listenButtonHeld = MutableStateFlow(false)
    val listenButtonHeld: StateFlow<Boolean> = _listenButtonHeld.asStateFlow()

    val ttsEnabled: StateFlow<Boolean> = UserPrefs.ttsEnabledFlow(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private var boundService: AudioCaptureService? = null
    private var pollJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as? AudioCaptureService.LocalBinder)?.service ?: return
            boundService = svc
            pollJob?.cancel()
            pollJob = viewModelScope.launch {
                launch { svc.isCapturing.collect { _isCapturing.value = it } }
                launch { svc.rmsLevel.collect { _rmsLevel.value = it } }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            _isCapturing.value = false
        }
    }

    init {
        bindIfRunning()
    }

    private fun bindIfRunning() {
        val ctx: Context = getApplication()
        ctx.bindService(
            Intent(ctx, AudioCaptureService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    fun startCapture() {
        val ctx: Context = getApplication()
        AudioCaptureService.start(ctx)
        // bindService was already called in init; service will connect.
    }

    fun stopCapture() {
        val ctx: Context = getApplication()
        AudioCaptureService.stop(ctx)
    }

    fun verifyNow() {
        val svc = boundService ?: return
        if (_isVerifying.value) return
        tts.stopNow()
        val wav = svc.consumeWav()
        _isVerifying.value = true
        _transcript.value = ""
        viewModelScope.launch {
            val result = app.engineHolder.verifyAudio(wav) { partial ->
                _transcript.value = partial
            }
            _isVerifying.value = false
            result.onSuccess { fullText ->
                _transcript.value = fullText
                if (ttsEnabled.value) tts.speak(fullText)
            }.onFailure { t ->
                _transcript.value = "Could not run the model: ${t.message}"
            }
        }
    }

    fun setListenButtonHeld(held: Boolean) {
        _listenButtonHeld.value = held
        tts.setPermitSpeak(held)
        if (held && ttsEnabled.value && _transcript.value.isNotBlank()) {
            tts.speak(_transcript.value)
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { UserPrefs.setTtsEnabled(getApplication(), enabled) }
        if (!enabled) {
            setListenButtonHeld(false)
            tts.stopNow()
        }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unbindService(connection) }
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                MainViewModel(app)
            }
        }
    }
}
