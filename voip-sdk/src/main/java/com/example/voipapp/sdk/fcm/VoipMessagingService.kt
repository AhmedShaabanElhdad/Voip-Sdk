package com.example.voipapp.sdk.fcm

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.voipapp.sdk.core.VoipService

// i think the BE will send type incoming_call -> get also data from caller like name
class VoipMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("VoipMessagingService", "From: ${remoteMessage.from}")

        if (remoteMessage.data.isNotEmpty()) {
            val type = remoteMessage.data["type"]
            if (type == "incoming_call") {
                val caller = remoteMessage.data["caller"] ?: "Unknown"
                handleIncomingCall(caller)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("VoipMessagingService", "Refreshed token: $token")
    }

    private fun handleIncomingCall(caller: String) {
        val intent = Intent(this, VoipService::class.java).apply {
            action = VoipService.ACTION_START_CALL
            putExtra(VoipService.EXTRA_DESTINATION, caller)
        }
        
        startForegroundService(intent)
    }
}
