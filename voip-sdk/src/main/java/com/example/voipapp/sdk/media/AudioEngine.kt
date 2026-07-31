package com.example.voipapp.sdk.media

import android.annotation.SuppressLint
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioEngine {
    private val TAG = "AudioEngine"
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    val FRAME_SIZE = 640 // 20ms at 16kHz

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private val isRunning = AtomicBoolean(false)
    private var recordingThread: Thread? = null
    
    @Volatile
    private var isMuted = false

    var onAudioFrameCaptured: ((ByteArray) -> Unit)? = null
    
    /**
     * Callback for audio level updates (0.0 to 1.0)
     */
    var onAudioLevelUpdated: ((Float) -> Unit)? = null

    fun setMute(muted: Boolean) {
        this.isMuted = muted
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning.get()) return
        
        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
        audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG_IN)
                .build())
            .setBufferSizeInBytes(Math.max(minBufSize, FRAME_SIZE * 4))
            .build()

        setupAudioEffects(audioRecord!!.audioSessionId)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG_OUT)
                .build())
            .setBufferSizeInBytes(FRAME_SIZE * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        isRunning.set(true)
        audioRecord?.startRecording()
        audioTrack?.play()

        recordingThread = Thread({
            val buffer = ByteArray(FRAME_SIZE)
            while (isRunning.get()) {
                val read = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: -1
                if (read == FRAME_SIZE) {
                    val level = calculateAudioLevel(buffer)
                    onAudioLevelUpdated?.invoke(level)
                    
                    if (!isMuted) {
                        onAudioFrameCaptured?.invoke(buffer.copyOf())
                    }
                }
            }
        }, "AudioRecordingThread").apply { start() }
    }

    private fun calculateAudioLevel(buffer: ByteArray): Float {
        var sum = 0.0
        for (i in 0 until buffer.size step 2) {
            val sample = ((buffer[i+1].toInt() shl 8) or (buffer[i].toInt() and 0xff)).toShort()
            sum += sample * sample
        }
        val rms = sqrt(sum / (buffer.size / 2))
        // Normalize RMS to 0.0 - 1.0 range (32767 is max for 16-bit)
        return (rms / 32767.0).coerceIn(0.0, 1.0).toFloat()
    }

    private fun setupAudioEffects(sessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
        if (NoiseSuppressor.isAvailable()) noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
        if (AutomaticGainControl.isAvailable()) gainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
    }

    fun playAudio(data: ByteArray) {
        if (isRunning.get()) audioTrack?.write(data, 0, data.size)
    }

    fun stop() {
        isRunning.set(false)
        try { recordingThread?.join(500) } catch (e: Exception) {}
        echoCanceler?.release()
        noiseSuppressor?.release()
        gainControl?.release()
        audioRecord?.release()
        audioTrack?.release()
        audioRecord = null
        audioTrack = null
    }
}
