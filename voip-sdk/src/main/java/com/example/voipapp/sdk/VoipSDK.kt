package com.example.voipapp.sdk

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import com.example.voipapp.sdk.core.VoipClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object VoipSDK {
    private var client: VoipClient? = null
    
    var config: Config? = null
        private set

    private val _localAudioLevel = MutableStateFlow(0f)
    val localAudioLevel: StateFlow<Float> = _localAudioLevel

    private val _remoteAudioLevel = MutableStateFlow(0f)
    val remoteAudioLevel: StateFlow<Float> = _remoteAudioLevel

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState

    private val _currentCallerName = MutableStateFlow<String?>(null)
    val currentCallerName: StateFlow<String?> = _currentCallerName

    enum class CallState {
        IDLE,
        INCOMING,
        OUTGOING,
        ACTIVE
    }

    data class Config(
        val signalingServerUrl: String,
        val udpPort: Int = 5004
    )

    fun initialize(context: Context, config: Config) {
        this.config = config
        client = VoipClient(context.applicationContext)
    }

    internal fun updateLocalAudioLevel(level: Float) {
        _localAudioLevel.value = level
    }

    internal fun updateRemoteAudioLevel(level: Float) {
        _remoteAudioLevel.value = level
    }

    internal fun updateCallState(state: CallState, callerName: String? = null) {
        _callState.value = state
        if (callerName != null) _currentCallerName.value = callerName
        if (state == CallState.IDLE) _currentCallerName.value = null
    }

    fun login(token: String) = client?.login(token)
    
    @RequiresPermission(Manifest.permission.CALL_PHONE)
    fun call(destination: String) {
        updateCallState(CallState.OUTGOING, destination)
        client?.call(destination)
    }
    
    fun answer(callId: String) {
        updateCallState(CallState.ACTIVE)
        client?.answer(callId)
    }
    
    fun reject(callId: String) {
        updateCallState(CallState.IDLE)
        client?.reject(callId)
    }
    
    fun hangup(callId: String) {
        updateCallState(CallState.IDLE)
        client?.hangup(callId)
    }
    
    fun setMute(muted: Boolean) = client?.setMute(muted)
    
    fun enableSpeaker(enabled: Boolean) = client?.enableSpeaker(enabled)
}
