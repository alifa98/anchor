package info.faraji.anchor.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lock-free single-producer / single-consumer ring buffer of 16-bit PCM samples.
 * Holds the last [windowSeconds] of audio at [sampleRateHz]. Older samples are
 * silently overwritten.
 *
 * snapshot() may be called from any thread; it copies out the current window
 * without blocking the producer for more than the duration of the copy.
 */
class RollingPcmBuffer(
    val sampleRateHz: Int = 16_000,
    private val windowSeconds: Int = 60,
) {
    private val capacity: Int = sampleRateHz * windowSeconds
    private val ring: ShortArray = ShortArray(capacity)

    @Volatile private var writeIndex: Int = 0
    @Volatile private var totalWritten: Long = 0L

    /** Append samples produced by AudioRecord. Producer thread only. */
    fun append(src: ShortArray, offset: Int, length: Int) = synchronized(this) {
        if (length <= 0) return
        var remaining = length
        var srcPos = offset
        var w = writeIndex
        while (remaining > 0) {
            val chunk = minOf(remaining, capacity - w)
            System.arraycopy(src, srcPos, ring, w, chunk)
            w = (w + chunk) % capacity
            srcPos += chunk
            remaining -= chunk
        }
        writeIndex = w
        totalWritten += length
    }

    /** Number of samples currently filled (caps at capacity). */
    val filledSamples: Int get() = synchronized(this) {
        minOf(totalWritten, capacity.toLong()).toInt()
    }

    /**
     * Take a copy of the current window in chronological order (oldest first).
     * Returned array length == filledSamples.
     */
    fun snapshotPcm(): ShortArray = synchronized(this) {
        val w = writeIndex
        val total = totalWritten
        val filled = minOf(total, capacity.toLong()).toInt()
        val out = ShortArray(filled)
        if (filled == 0) return out
        if (total < capacity) {
            // Buffer not yet full: data is [0, w)
            System.arraycopy(ring, 0, out, 0, filled)
        } else {
            // Buffer full: chronological start is at writeIndex
            val tail = capacity - w
            System.arraycopy(ring, w, out, 0, tail)
            System.arraycopy(ring, 0, out, tail, w)
        }
        return out
    }

    /** WAV-encoded snapshot (16-bit PCM, mono). */
    fun snapshotWav(): ByteArray = synchronized(this) {
        val pcm = snapshotPcm()
        return encodeWav(pcm, sampleRateHz)
    }

    fun consumeWav(): ByteArray = synchronized(this) {
        val wav = snapshotWav()
        reset()
        return wav
    }

    fun reset() = synchronized(this) {
        writeIndex = 0
        totalWritten = 0L
    }
}

private fun encodeWav(pcm: ShortArray, sampleRateHz: Int): ByteArray {
    val byteCount = pcm.size * 2
    val out = ByteArrayOutputStream(44 + byteCount)
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(36 + byteCount)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16)                       // PCM chunk size
    header.putShort(1)                      // PCM format
    header.putShort(1)                      // mono
    header.putInt(sampleRateHz)
    header.putInt(sampleRateHz * 2)         // byte rate (mono * 2 bytes)
    header.putShort(2)                      // block align
    header.putShort(16)                     // bits per sample
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(byteCount)
    out.write(header.array())

    val samples = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
    for (s in pcm) samples.putShort(s)
    out.write(samples.array())
    return out.toByteArray()
}
