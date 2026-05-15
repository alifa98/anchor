package com.example.psycho.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    object Idle : DownloadState()
    data class Progress(
        val bytesDone: Long,
        val totalBytes: Long,
        val downloadedMb: Double,
        val totalMb: Double,
        val fraction: Float,
        val mbPerSec: Double,
    ) : DownloadState()
    object Done : DownloadState()
    data class Failed(val message: String, val resumable: Boolean) : DownloadState()
}

/**
 * Resumable streaming downloader with progress callbacks. Writes to a `.part`
 * file and atomically renames it to the final path on success. Re-running
 * download() picks up from any existing `.part` file using HTTP Range.
 */
class ModelDownloader(private val context: Context) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming download
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun download(): Flow<DownloadState> = callbackFlow {
        val emitProgress: (Long, Long, Double) -> Unit = { done, total, mbps ->
            val frac = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
            trySend(
                DownloadState.Progress(
                    bytesDone = done,
                    totalBytes = total,
                    downloadedMb = done / 1_048_576.0,
                    totalMb = total / 1_048_576.0,
                    fraction = frac,
                    mbPerSec = mbps,
                )
            )
        }

        try {
            // 1. Tiny chat template first (no progress reporting needed).
            downloadFile(ModelAssets.TEMPLATE_URL, ModelAssets.templateFile(context)) { _, _, _ -> }
            // 2. The big model.
            downloadFile(ModelAssets.MODEL_URL, ModelAssets.modelFile(context), emitProgress)
            trySend(DownloadState.Done)
        } catch (e: IOException) {
            trySend(DownloadState.Failed(e.message ?: "Network error", resumable = true))
        } catch (e: Throwable) {
            trySend(DownloadState.Failed(e.message ?: "Unknown error", resumable = true))
        } finally {
            close()
        }
        awaitClose { /* nothing to dispose */ }
    }.flowOn(Dispatchers.IO)

    /** Synchronous resumable download into a .part file with progress callback. */
    private fun downloadFile(
        url: String,
        finalFile: File,
        onProgress: (done: Long, total: Long, mbps: Double) -> Unit,
    ) {
        if (finalFile.exists() && finalFile.length() > 1_000L) return // already done

        val partFile = File(finalFile.parentFile, finalFile.name + ".part")
        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        val req = Request.Builder()
            .url(url)
            .apply { if (existingBytes > 0) header("Range", "bytes=$existingBytes-") }
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                throw IOException("HTTP ${resp.code} for $url")
            }
            val body = resp.body ?: throw IOException("Empty body")
            val contentLen = body.contentLength()
            val total = if (contentLen >= 0) contentLen + existingBytes else -1L

            partFile.parentFile?.mkdirs()
            val sink = java.io.RandomAccessFile(partFile, "rw")
            sink.seek(existingBytes)

            val buf = ByteArray(64 * 1024)
            var done = existingBytes
            val source = body.byteStream()
            val startNs = System.nanoTime()
            var lastReport = 0L

            try {
                while (true) {
                    val n = source.read(buf)
                    if (n == -1) break
                    sink.write(buf, 0, n)
                    done += n
                    val now = System.nanoTime()
                    if (now - lastReport > 200_000_000L) { // every 200 ms
                        val elapsedSec = (now - startNs) / 1_000_000_000.0
                        val mbps = if (elapsedSec > 0) (done - existingBytes) / 1_048_576.0 / elapsedSec else 0.0
                        onProgress(done, total, mbps)
                        lastReport = now
                    }
                }
            } finally {
                sink.close()
                source.close()
            }

            // Final progress tick.
            onProgress(done, total, 0.0)

            if (total > 0 && done < total) {
                throw IOException("Truncated download: $done/$total bytes")
            }
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                throw IOException("Failed to finalize ${finalFile.name}")
            }
        }
    }

    fun deletePartials() {
        val dir = ModelAssets.modelDir(context)
        dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }
}
