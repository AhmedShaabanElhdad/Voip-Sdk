package com.example.voipapp.sdk.telecom

import android.R.attr.action
import android.content.Intent
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.example.voipapp.sdk.core.VoipService

class VoipConnectionService : ConnectionService() {

    private val TAG = "VoipConnService"

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "onCreateOutgoingConnection")

        val destination = request?.address?.schemeSpecificPart
        val intent = Intent(this, VoipService::class.java).apply {
            action = VoipService.ACTION_START_CALL
            putExtra(VoipService.EXTRA_DESTINATION, destination)
        }
        startService(intent)

        val service = VoipService.getInstance()
        return if (service != null) {
            VoipConnection(service).apply {
                setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
                setInitializing()
                setActive()
            }
        } else {
            Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "onCreateIncomingConnection")

        val service = VoipService.getInstance()
        return if (service != null) {
            VoipConnection(service).apply {
                setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
                setInitializing()
            }
        } else {
            // If the background service isn't ready, we fail the connection gracefully
            Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR, "Service unavailable"))
        }
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.e(TAG, "Incoming connection failed")
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.e(TAG, "Outgoing connection failed")
    }
}