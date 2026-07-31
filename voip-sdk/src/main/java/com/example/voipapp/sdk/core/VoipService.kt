package com.example.voipapp.sdk.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.voipapp.sdk.Voip
import com.example.voipapp.sdk.media.AudioCodec
import com.example.voipapp.sdk.media.AudioEngine
import com.example.voipapp.sdk.media.AudioOutput
import com.example.voipapp.sdk.media.VoipAudioManager
import com.example.voipapp.sdk.network.SignalingClient
import com.example.voipapp.sdk.network.UdpAudioTransport
import kotlin.math.sqrt

class VoipService : Service() {

    companion object {
        const val ACTION_LOGIN = "com.example.voipapp.ACTION_LOGIN"
        const val ACTION_START_CALL = "com.example.voipapp.ACTION_START_CALL"
        const val ACTION_ANSWER_CALL = "com.example.voipapp.ACTION_ANSWER_CALL"
        const val ACTION_REJECT_CALL = "com.example.voipapp.ACTION_REJECT_CALL"
        const val ACTION_HANGUP_CALL = "com.example.voipapp.ACTION_HANGUP_CALL"
        const val ACTION_SET_MUTE = "com.example.voipapp.ACTION_SET_MUTE"
        const val ACTION_SET_SPEAKER = "com.example.voipapp.ACTION_SET_SPEAKER"
        const val ACTION_SET_AUDIO_OUTPUT = "com.example.voipapp.ACTION_SET_AUDIO_OUTPUT"
        
        const val EXTRA_DESTINATION = "EXTRA_DESTINATION"
        const val EXTRA_REMOTE_IP = "EXTRA_REMOTE_IP"
        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
        const val EXTRA_VALUE = "EXTRA_VALUE"
        const val EXTRA_TOKEN = "EXTRA_TOKEN"
        const val EXTRA_AUDIO_OUTPUT = "EXTRA_AUDIO_OUTPUT"
        
        private const val CHANNEL_ID = "VoipServiceChannel"
        private const val NOTIFICATION_ID = 1

        private var instance: VoipService? = null
        fun getInstance(): VoipService? = instance
    }

    private lateinit var audioEngine: AudioEngine
    private lateinit var signalingClient: SignalingClient
    private lateinit var audioManager: VoipAudioManager
    private lateinit var udpTransport: UdpAudioTransport

    private var remoteIp: String = "127.0.0.1"
    private var remotePort: Int = 5004

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioEngine = AudioEngine()
        signalingClient = SignalingClient()
        audioManager = VoipAudioManager(this)
        udpTransport = UdpAudioTransport()
        
        setupAudioPipeline()
        createNotificationChannel()
    }

    private fun setupAudioPipeline() {
        // Local audio (Microphone)
        audioEngine.onAudioFrameCaptured = { pcmData ->
            val encodedData = AudioCodec.encode(pcmData)
            udpTransport.sendPacket(encodedData, remoteIp, remotePort)
        }
        
        audioEngine.onAudioLevelUpdated = { level ->
            Voip.updateLocalAudioLevel(level)
        }

        // Remote audio (Network)
        udpTransport.onPacketReceived = { encodedData ->
            val pcmData = AudioCodec.decode(encodedData)
            val level = calculateLevel(pcmData)
            Voip.updateRemoteAudioLevel(level)
            audioEngine.playAudio(pcmData)
        }
    }

    private fun calculateLevel(pcmData: ByteArray): Float {
        var sum = 0.0
        for (i in 0 until pcmData.size step 2) {
            val sample = ((pcmData[i+1].toInt() shl 8) or (pcmData[i].toInt() and 0xff)).toShort()
            sum += sample * sample
        }
        val rms = sqrt(sum / (pcmData.size / 2))
        return (rms / 32767.0).coerceIn(0.0, 1.0).toFloat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOGIN -> {
                val token = intent.getStringExtra(EXTRA_TOKEN)
                token?.let { signalingClient.connect(it) }
            }
            ACTION_START_CALL -> {
                remoteIp = intent.getStringExtra(EXTRA_REMOTE_IP) ?: "127.0.0.1"
                val dest = intent.getStringExtra(EXTRA_DESTINATION)
                startCall(dest)
            }
            ACTION_ANSWER_CALL -> answerCallLocally()
            ACTION_REJECT_CALL -> rejectCallLocally()
            ACTION_HANGUP_CALL -> endCallLocally()
            ACTION_SET_MUTE -> {
                val muted = intent.getBooleanExtra(EXTRA_VALUE, false)
                audioEngine.setMute(muted)
            }
            ACTION_SET_SPEAKER -> {
                val enabled = intent.getBooleanExtra(EXTRA_VALUE, false)
                val output = if (enabled) AudioOutput.SPEAKER else AudioOutput.EARPIECE
                audioManager.setAudioOutput(output)
            }
            ACTION_SET_AUDIO_OUTPUT -> {
                val outputName = intent.getStringExtra(EXTRA_AUDIO_OUTPUT)
                try {
                    val output = AudioOutput.valueOf(outputName ?: AudioOutput.EARPIECE.name)
                    audioManager.setAudioOutput(output)
                } catch (e: Exception) {
                    Log.e("VoipService", "Invalid audio output: $outputName")
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startCall(destination: String?) {
        startForeground(NOTIFICATION_ID, createNotification("Calling $destination..."))
        audioManager.setupForCall()
        udpTransport.startListening(5004)
        signalingClient.sendSignal("offer", destination)
        audioEngine.start()
        Voip.updateCallState(Voip.CallState.ACTIVE, destination)
    }

    fun answerCallLocally() {
        startForeground(NOTIFICATION_ID, createNotification("Call Active"))
        audioManager.setupForCall()
        udpTransport.startListening(5004)
        audioEngine.start()
        signalingClient.sendSignal("answer", null)
        Voip.updateCallState(Voip.CallState.ACTIVE, "Remote User")
    }

    fun rejectCallLocally() {
        signalingClient.sendSignal("reject", null)
        Voip.updateCallState(Voip.CallState.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun endCallLocally() {
        audioEngine.stop()
        udpTransport.stop()
        signalingClient.disconnect()
        audioManager.reset()
        Voip.updateCallState(Voip.CallState.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoIP Call")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "VoIP Service Channel", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
