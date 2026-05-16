package com.example.psycho.model

import android.content.Context
import java.io.File

/**
 * Where the Gemma .litertlm artifact and its chat template live on disk,
 * plus the URLs they are downloaded from. Files are kept in `filesDir`
 * so they survive across app updates.
 */
object ModelAssets {
    const val MODEL_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    const val TEMPLATE_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/chat_template.jinja"

    private const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
    private const val TEMPLATE_FILE = "chat_template.jinja"

    fun modelDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun modelFile(context: Context): File = File(modelDir(context), MODEL_FILE)
    fun templateFile(context: Context): File = File(modelDir(context), TEMPLATE_FILE)

    fun isModelInstalled(context: Context): Boolean {
        val m = modelFile(context)
        // Heuristic: real model is ~2.5 GB. Anything < 2 GB is likely partial or corrupted.
        return m.exists() && m.length() > 2_000_000_000L
    }
}
