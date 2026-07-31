package com.example.voipapp.sdk.network

import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject

class SignalingClient {
    private val TAG = "SignalingClient"
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var serverUrl: String = "wss://echo.websocket.org"

    var onMediaPacketReceived: ((ByteArray) -> Unit)? = null
    var onSignalReceived: ((JSONObject) -> Unit)? = null

    fun setServerUrl(url: String) {
        this.serverUrl = url
    }

    fun connect(token: String) {
        val request = Request.Builder()
            .url(serverUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to signaling server")
                sendSignal("login", null, JSONObject().apply { put("token", token) })
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    onSignalReceived?.invoke(JSONObject(text))
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing signal: $text")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMediaPacketReceived?.invoke(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
            }
        })
    }

    fun sendSignal(type: String, target: String?, data: Any? = null) {
        val json = JSONObject().apply {
            put("type", type)
            if (target != null) put("target", target)
            if (data != null) put("data", data)
        }
        webSocket?.send(json.toString())
    }

    fun sendMediaPacket(data: ByteArray) {
        webSocket?.send(ByteString.of(*data))
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnected")
    }
}
