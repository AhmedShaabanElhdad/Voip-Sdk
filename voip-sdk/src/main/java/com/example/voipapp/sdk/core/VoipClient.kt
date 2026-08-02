package com.example.voipapp.sdk.core

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.example.voipapp.sdk.media.AudioOutput
import com.example.voipapp.sdk.telecom.VoipConnectionService

class VoipClient(private val context: Context) {

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    private val phoneAccountHandle: PhoneAccountHandle

    init {
        val componentName = ComponentName(context, VoipConnectionService::class.java)
        phoneAccountHandle = PhoneAccountHandle(componentName, "VoipAppAccount")
        registerPhoneAccount()
    }

    private fun registerPhoneAccount() {
        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "VoipApp")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()
        telecomManager.registerPhoneAccount(phoneAccount)
    }

    fun login(token: String) {
        val intent = Intent(context, VoipService::class.java).apply {
            action = VoipService.ACTION_LOGIN
            putExtra(VoipService.EXTRA_TOKEN, token)
        }
        context.startService(intent)
    }

    @RequiresPermission(anyOf = [Manifest.permission.CALL_PHONE, Manifest.permission.MANAGE_OWN_CALLS])
    fun call(destination: String) {
        val uri = Uri.fromParts("tel", destination, null)
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
        }
        try {
            telecomManager.placeCall(uri, extras)
        } catch (e: SecurityException) {
            val intent = Intent(context, VoipService::class.java).apply {
                action = VoipService.ACTION_START_CALL
                putExtra(VoipService.EXTRA_DESTINATION, destination)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun answer(callId: String) {
        val intent = Intent(context, VoipService::class.java).apply {
            action = VoipService.ACTION_ANSWER_CALL
            putExtra(VoipService.EXTRA_CALL_ID, callId)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun reject(callId: String) {
        val intent = Intent(context, VoipService::class.java).apply {
            action = VoipService.ACTION_REJECT_CALL
            putExtra(VoipService.EXTRA_CALL_ID, callId)
        }
        context.startService(intent)
    }

    fun hangup(callId: String) {
        val intent = Intent(context, VoipService::class.java).apply {
            action = VoipService.ACTION_HANGUP_CALL
            putExtra(VoipService.EXTRA_CALL_ID, callId)
        }
        context.startService(intent)
    }

    fun setMute(muted: Boolean) {
        val intent = Intent(context, VoipService::class.java).apply {
            action = VoipService.ACTION_SET_MUTE
            putExtra(VoipService.EXTRA_VALUE, muted)
        }
        context.startService(intent)
    }

    fun enableSpeaker(enabled: Boolean) {
        val intent = Intent(context, VoipService::class.java).apply {
            action = VoipService.ACTION_SET_SPEAKER
            putExtra(VoipService.EXTRA_VALUE, enabled)
        }
        context.startService(intent)
    }

    fun setAudioOutput(output: AudioOutput) {
        val intent = Intent(context, VoipService::class.java).apply {
            action = VoipService.ACTION_SET_AUDIO_OUTPUT
            putExtra(VoipService.EXTRA_AUDIO_OUTPUT, output.name)
        }
        context.startService(intent)
    }
}
