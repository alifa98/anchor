package com.example.psycho

import android.app.Application
import com.example.psycho.model.GemmaEngineHolder
import com.example.psycho.tts.HoldToSpeakTts

class PsychoApp : Application() {
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
        lateinit var instance: PsychoApp
            private set
    }
}
