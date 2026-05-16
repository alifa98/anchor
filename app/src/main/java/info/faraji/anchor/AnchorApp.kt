package info.faraji.anchor

import android.app.Application
import info.faraji.anchor.model.GemmaEngineHolder
import info.faraji.anchor.tts.HoldToSpeakTts

class AnchorApp : Application() {
    lateinit var engineHolder: GemmaEngineHolder
        private set
    lateinit var tts: HoldToSpeakTts
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        engineHolder = GemmaEngineHolder(this)
        tts = HoldToSpeakTts(this)
    }

    override fun onTerminate() {
        engineHolder.shutdown()
        tts.shutdown()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: AnchorApp
            private set
    }
}
