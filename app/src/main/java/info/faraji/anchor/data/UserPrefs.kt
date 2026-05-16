package info.faraji.anchor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "anchor_prefs")

object UserPrefs {
    private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
    private val KEY_TTS_ENABLED = booleanPreferencesKey("tts_enabled")

    fun onboardedFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDED] == true }

    fun ttsEnabledFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_TTS_ENABLED] != false } // default true

    suspend fun setOnboarded(context: Context, value: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDED] = value }
    }

    suspend fun setTtsEnabled(context: Context, value: Boolean) {
        context.dataStore.edit { it[KEY_TTS_ENABLED] = value }
    }
}
