package info.faraji.anchor.model

import android.content.Context
import android.util.Log
import java.io.File
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class EngineState {
    object Unloaded : EngineState()
    object Loading : EngineState()
    object Ready : EngineState()
    data class Error(val message: String) : EngineState()
}

/**
 * Wraps the LiteRT-LM [Engine] with on-demand load / unload to conserve
 * memory and battery. Always call from a coroutine — all native work
 * happens on [Dispatchers.Default].
 *
 * Lifecycle:
 *   ensureLoaded() — load the model, GPU first, fall back to CPU
 *   verifyAudio()  — run inference on a WAV byte[] window
 *   scheduleUnload() — unload after [idleUnloadMs] of inactivity
 *   shutdown()     — synchronous teardown for process exit
 */
class GemmaEngineHolder(
    private val context: Context,
    private val idleUnloadMs: Long = 90_000L,
) {
    private val tag = "GemmaEngineHolder"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var unloadJob: Job? = null

    private val _state = MutableStateFlow<EngineState>(EngineState.Unloaded)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    suspend fun ensureLoaded(forceNewConversation: Boolean = false): Boolean = mutex.withLock {
        cancelPendingUnload()
        if (engine != null) {
            if (forceNewConversation) {
            // TODO: Maybe later for follow up button we will not need to reset the conversation
                runCatching { conversation?.close() }
                conversation = withContext(Dispatchers.Default) {
                    engine?.createConversation(buildConversationConfig())
                }
            }
            return@withLock true
        }
        if (!ModelAssets.isModelInstalled(context)) {
            _state.value = EngineState.Error("Model not installed")
            return@withLock false
        }

        _state.value = EngineState.Loading
        try {
            // use Multi-Token Prediction (MTP) for better performance.
//            ExperimentalFlags.enableSpeculativeDecoding = true

            val modelPath = ModelAssets.modelFile(context).absolutePath
            val cacheDir = context.cacheDir.absolutePath

            val newEngine = withContext(Dispatchers.Default) {
                buildEngine(modelPath, cacheDir, preferGpu = true)
            }
            engine = newEngine
            conversation = withContext(Dispatchers.Default) {
                newEngine.createConversation(buildConversationConfig())
            }
            _state.value = EngineState.Ready
            true
        } catch (t: Throwable) {
            Log.e(tag, "Engine load failed", t)
            // Tear down partial state and surface the error.
            runCatching { conversation?.close() }
            runCatching { engine?.close() }
            conversation = null
            engine = null
            _state.value = EngineState.Error(t.message ?: "Failed to load model")
            false
        }
    }

    /**
     * Send the latest 1-minute window straight to the multimodal model
     * as audio bytes and ask for an objective description.
     */
    suspend fun verifyAudio(
        wavBytes: ByteArray,
        userPrompt: String? = null,
        onToken: (String) -> Unit,
    ): Result<String> = runCatching {
        // Start a fresh conversation for every explicit verification request
        if (!ensureLoaded(forceNewConversation = true)) error("Model not ready")
        val conv = conversation ?: error("Conversation missing")

        val text = userPrompt ?: DEFAULT_VERIFY_PROMPT
        val responseText = withContext(Dispatchers.Default) {
            val message = conv.sendMessage(
                Contents.of(
                    Content.AudioBytes(wavBytes),
                    Content.Text(text),
                )
            )
            extractText(message)
        }
        onToken(responseText)
        scheduleUnload()
        responseText
    }

    /** Conversational follow-up the user can ask while holding the green button. */
    suspend fun chat(prompt: String, onToken: (String) -> Unit): Result<String> = runCatching {
        if (!ensureLoaded()) error("Model not ready")
        val conv = conversation ?: error("Conversation missing")
        val out = withContext(Dispatchers.Default) {
            extractText(conv.sendMessage(prompt))
        }
        onToken(out)
        scheduleUnload()
        out
    }

    private fun extractText(message: Message): String =
        message.contents.contents.filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }

    fun scheduleUnload() {
        cancelPendingUnload()
        unloadJob = scope.launch {
            delay(idleUnloadMs)
            mutex.withLock { unloadInternal() }
        }
    }

    private fun cancelPendingUnload() {
        unloadJob?.cancel()
        unloadJob = null
    }

    private fun unloadInternal() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        _state.value = EngineState.Unloaded
        Log.i(tag, "Engine unloaded (idle)")
    }

    /** Fully shut down — call from Application.onTerminate or process exit hooks. */
    fun shutdown() {
        cancelPendingUnload()
        unloadInternal()
    }

    private fun buildEngine(modelPath: String, cacheDir: String, preferGpu: Boolean): Engine {
        val backend = if (preferGpu) Backend.GPU() else Backend.CPU()
        Log.i(tag, "Attempting to build engine with backend: ${if (preferGpu) "GPU" else "CPU"}")

        // separate cache subdirs to avoid backend conflicts if one fails.
        val subCacheDir = File(cacheDir, if (preferGpu) "gpu" else "cpu").apply { mkdirs() }

        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            audioBackend = Backend.CPU(),  // Audio tower usually stays on CPU
            cacheDir = subCacheDir.absolutePath,
            maxNumTokens = 3072, // ~1784 (audio) + ~300 (prompts) + 256 (output) = ~2340
        )
        val e = Engine(config)
        try {
            e.initialize() // ~10 s, blocks; we are on Dispatchers.Default already.
        } catch (t: Throwable) {
            runCatching { e.close() }
            if (preferGpu) {
                Log.w(tag, "GPU init failed, falling back to CPU: ${t.message}")
                return buildEngine(modelPath, cacheDir, preferGpu = false)
            }
            throw t
        }
        return e
    }

    private fun buildConversationConfig() = ConversationConfig(
        systemInstruction = Contents.of(SYSTEM_PROMPT),
        initialMessages = emptyList(),
        tools = emptyList(),
        automaticToolCalling = false,
        samplerConfig = SamplerConfig(
            topK = 40,
            topP = 0.9,
            temperature = 0.0,
            seed = 0,
        ),
    )

    companion object {
        // Safety-critical prompt: the model must stay strictly objective.
        // It must NOT validate, embellish, or speculate — it is the user's
        // reality-check, not their conversation partner.
        private val SYSTEM_PROMPT = """
            You are an objective audio observer for a person who may
            experience auditory hallucinations. The user gives you a recent
            clip of audio from their environment. Listen to it and
            label what is actually there.

            Format your answer as 1–3 short, factual lines. Examples of the
            style we want:
              - "Room noise only — a faint hum, no speech, no events."
              - "Two voices in conversation. They are taking to each other."
              - "Short sudden sound of falling on floor" 
              - "A single door knock around the 10-second mark, then silence."
              - "Music playing in the background, no speech."
              - "Footsteps and a door closing. No voices."

            Rules:
            - Name the sounds you hear (speech, knock, footsteps, music,
              clapping, door, traffic, silence, etc.). Count voices when you
              can. Quote any clear words you hear in single quotes.
            - If the user describes something you do NOT hear, say so
              directly: "I do not hear that."
            - No reassurance, no speculation, no emotional framing.
            - Do not roleplay. Do not pretend to be anyone else.
        """.trimIndent()
//         TODO: having command like - "Two voices in conversation. One says 'hi' near the start." as example. For now, the model is not capturing the conversation transcript correctly.

        private const val DEFAULT_VERIFY_PROMPT =
            "Listen to this audio clip and tell me exactly what is in it."
    }
}
