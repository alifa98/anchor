package info.faraji.anchor.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import info.faraji.anchor.MainActivity
import info.faraji.anchor.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread

/**
 * Foreground service that owns the rolling 1-minute audio buffer.
 * Audio capture starts when the service starts and runs until the
 * service is explicitly stopped. The buffer can be snapshot at any
 * time without interrupting capture (a "quick clone" of the window).
 */
class AudioCaptureService : Service() {

    private val binder = LocalBinder()
    private val buffer =
        RollingPcmBuffer(sampleRateHz = SAMPLE_RATE, windowSeconds = WINDOW_SECONDS)
    private val _isCapturing = MutableStateFlow(false)
    private val _rmsLevel = MutableStateFlow(0f)

    @Volatile
    private var captureThread: Thread? = null

    @Volatile
    private var keepGoing: Boolean = false

    inner class LocalBinder : Binder() {
        val service: AudioCaptureService get() = this@AudioCaptureService
    }

    val isCapturing: StateFlow<Boolean> get() = _isCapturing.asStateFlow()
    val rmsLevel: StateFlow<Float> get() = _rmsLevel.asStateFlow()

    fun snapshotWav(): ByteArray = buffer.snapshotWav()
    fun consumeWav(): ByteArray = buffer.consumeWav()
    fun snapshotPcm(): ShortArray = buffer.snapshotPcm()
    val sampleRateHz: Int get() = buffer.sampleRateHz

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                startForegroundCompat()
                startCapture()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    private fun startCapture() {
        if (_isCapturing.value) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSize = maxOf(minBuf, SAMPLE_RATE) // ~1 sec headroom

        val audioSource = MediaRecorder.AudioSource.UNPROCESSED // Avoid any filter just get raw data

        val record = AudioRecord(
            audioSource,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }

        keepGoing = true
        _isCapturing.value = true
        record.startRecording()

        captureThread = thread(name = "AudioCapture", isDaemon = true) {
            val scratch = ShortArray(SAMPLE_RATE / 5) // 200 ms chunks
            try {
                while (keepGoing) {
                    val read = record.read(scratch, 0, scratch.size)
                    if (read > 0) {
                        buffer.append(scratch, 0, read)
                        _rmsLevel.value = computeRms(scratch, read)
                    } else if (read < 0) {
                        // ERROR_INVALID_OPERATION etc — give up.
                        break
                    }
                }
            } finally {
                try {
                    record.stop()
                } catch (_: Throwable) {
                }
                record.release()
                _isCapturing.value = false
                _rmsLevel.value = 0f
            }
        }
    }

    private fun stopCapture() {
        keepGoing = false
        captureThread?.join(500)
        captureThread = null
    }

    private fun startForegroundCompat() {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.audio_notification_title))
            .setContentText(getString(R.string.audio_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(tapIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.audio_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                    enableVibration(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun computeRms(buf: ShortArray, len: Int): Float {
        var sum = 0.0
        for (i in 0 until len) {
            val s = buf[i].toDouble()
            sum += s * s
        }
        val rms = Math.sqrt(sum / len)
        return (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        private const val CHANNEL_ID = "audio_capture"
        private const val NOTIF_ID = 1001
        const val ACTION_STOP = "info.faraji.anchor.audio.STOP"

        const val SAMPLE_RATE = 16_000
        const val WINDOW_SECONDS = 60

        fun start(context: Context) {
            val i = Intent(context, AudioCaptureService::class.java)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, AudioCaptureService::class.java).apply { action = ACTION_STOP }
            context.startService(i)
        }
    }
}
