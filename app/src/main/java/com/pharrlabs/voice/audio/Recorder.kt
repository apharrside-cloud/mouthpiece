package com.pharrlabs.voice.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MicUnavailable(val reason: String) : Exception(reason)

/**
 * Captures 16 kHz mono PCM from the mic. Spike A showed plain AudioRecord(VOICE_RECOGNITION)
 * works when started from an accessibility overlay with another app in front, so no foreground
 * service is used. Call [start], later [stop] to get silence-trimmed PCM.
 */
class Recorder(private val context: Context) {
    private val sampleRate = 16000
    private val maxSamples = sampleRate * 120 // 120 s hard cap

    @Volatile private var record: AudioRecord? = null
    @Volatile private var capturing = false
    private var thread: Thread? = null
    private var buffer = ShortArray(sampleRate * 10)
    private var length = 0
    private val endpoint = EndpointTracker()

    /** RMS of the most recent 100 ms of audio, for the bubble level animation. */
    @Volatile var level: Double = 0.0
        private set

    @Synchronized
    fun start(): Result<Unit> {
        if (record != null) return Result.failure(MicUnavailable("already recording"))
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(MicUnavailable("microphone permission not granted"))
        }
        return try {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 4
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return Result.failure(MicUnavailable("microphone is busy or unavailable"))
            }
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                rec.release()
                return Result.failure(MicUnavailable("microphone is in use by another app"))
            }
            length = 0
            endpoint.reset()
            capturing = true
            record = rec
            thread = Thread({ captureLoop(rec) }, "voice-recorder").also { it.start() }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(MicUnavailable(t.message ?: "could not start microphone"))
        }
    }

    private fun captureLoop(rec: AudioRecord) {
        val chunk = ShortArray(sampleRate / 10)
        while (capturing && length < maxSamples) {
            val n = rec.read(chunk, 0, chunk.size)
            if (n <= 0) break
            synchronized(this) {
                if (length + n > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, length + n))
                System.arraycopy(chunk, 0, buffer, length, n)
                length += n
                val r = rms(chunk, n)
                level = r
                endpoint.onChunk(r)
            }
        }
    }

    /** True once the speaker has said something and then been quiet for [silenceMs] (0 = never). */
    @Synchronized
    fun silenceReached(silenceMs: Int): Boolean = endpoint.shouldStop(silenceMs, minSpeechMs = 400)

    private fun rms(buf: ShortArray, n: Int): Double {
        var sum = 0.0
        for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
        return kotlin.math.sqrt(sum / n)
    }

    /** Copy of everything captured so far (untrimmed), for transcribing finished phrases while recording continues. */
    @Synchronized
    fun copyAll(): ShortArray = buffer.copyOf(length)

    /** Stops capture and returns trimmed PCM (or untrimmed when [trim] is false); empty trimmed result means no real speech. */
    suspend fun stop(trim: Boolean = true): ShortArray = withContext(Dispatchers.Default) {
        val rec: AudioRecord?
        val t: Thread?
        synchronized(this@Recorder) {
            rec = record
            t = thread
            record = null
            thread = null
            capturing = false
        }
        t?.join(1000)
        runCatching { rec?.stop() }
        rec?.release()
        val raw = synchronized(this@Recorder) { buffer.copyOf(length) }
        if (trim) SilenceTrimmer.trim(raw) else raw
    }

    /** Cancels without returning audio. */
    suspend fun cancel() { stop() }
}
