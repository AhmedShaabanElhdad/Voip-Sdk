package com.example.voipapp.sdk.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

class VoipAudioManager(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val TAG = "VoipAudioManager"

    fun setupForCall() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setAudioOutput(AudioOutput.EARPIECE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                // todo check if we need any implementation here in next PR
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    fun setAudioOutput(output: AudioOutput) {
        Log.d(TAG, "Setting audio output to: $output")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val deviceType = when (output) {
                AudioOutput.EARPIECE -> AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                AudioOutput.SPEAKER -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                AudioOutput.WIRED_HEADSET -> AudioDeviceInfo.TYPE_WIRED_HEADSET
                AudioOutput.BLUETOOTH -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }

            val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == deviceType }
                ?: if (output == AudioOutput.WIRED_HEADSET) {
                    audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
                } else null

            if (device != null) {
                audioManager.setCommunicationDevice(device)
            } else {
                // todo check the default later
                if (output != AudioOutput.EARPIECE) setAudioOutput(AudioOutput.EARPIECE)
            }
        } else {
            @Suppress("DEPRECATION")
            when (output) {
                AudioOutput.EARPIECE -> {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.stopBluetoothSco()
                }
                AudioOutput.SPEAKER -> {
                    audioManager.isSpeakerphoneOn = true
                    audioManager.stopBluetoothSco()
                }
                AudioOutput.BLUETOOTH -> {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.startBluetoothSco()
                }
                AudioOutput.WIRED_HEADSET -> {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.stopBluetoothSco()
                }
            }
        }
    }

    fun reset() {
        audioManager.mode = AudioManager.MODE_NORMAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.stopBluetoothSco()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
