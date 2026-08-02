package com.example.voipapp.sdk.telecom

import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log
import com.example.voipapp.sdk.core.VoipService


class VoipConnection(private val service: VoipService) : Connection() {

    override fun onShowIncomingCallUi() {
        Log.d("VoipConnection", "onShowIncomingCallUi")
    }

    override fun onCallAudioStateChanged(state: CallAudioState?) {
        Log.d("VoipConnection", "onCallAudioStateChanged: $state")
    }

    override fun onAnswer() {
        Log.d("VoipConnection", "onAnswer")
        setActive()
        service.answerCallLocally()
    }

    override fun onReject() {
        Log.d("VoipConnection", "onReject")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        service.rejectCallLocally()
        destroy()
    }

    override fun onDisconnect() {
        Log.d("VoipConnection", "onDisconnect")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        service.endCallLocally()
        destroy()
    }

    override fun onAbort() {
        Log.d("VoipConnection", "onAbort")
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        service.endCallLocally()
        destroy()
    }

    override fun onHold() {
        setOnHold()
    }

    override fun onUnhold() {
        setActive()
    }
}
