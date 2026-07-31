package com.example.voipapp.sdk.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 *  UDP Transport for low-latency media streams.
 */
class UdpAudioTransport {
    private val TAG = "UdpAudioTransport"
    private var socket: DatagramSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var receiveThread: Thread? = null
    private val sendExecutor = Executors.newSingleThreadExecutor()

    var onPacketReceived: ((ByteArray) -> Unit)? = null

    fun startListening(port: Int) {
        if (isRunning.get()) return
        try {
            socket = DatagramSocket(port)
            isRunning.set(true)
            
            receiveThread = Thread({
                val buffer = ByteArray(2048)
                while (isRunning.get()) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket?.receive(packet)
                        val data = packet.data.copyOfRange(0, packet.length)
                        onPacketReceived?.invoke(data)
                    } catch (e: Exception) {
                        if (isRunning.get()) Log.e(TAG, "UDP receive error", e)
                    }
                }
            }, "UdpReceiveThread").apply { start() }
            Log.d(TAG, "UDP Transport started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start UDP socket", e)
        }
    }

    fun sendPacket(data: ByteArray, remoteIp: String, remotePort: Int) {
        if (!isRunning.get()) return
        sendExecutor.execute {
            try {
                val address = InetAddress.getByName(remoteIp)
                val packet = DatagramPacket(data, data.size, address, remotePort)
                socket?.send(packet)
            } catch (e: Exception) {
                Log.e(TAG, "UDP send error", e)
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        socket?.close()
        socket = null
        sendExecutor.shutdown()
        try { receiveThread?.join(500) } catch (e: Exception) {}
        receiveThread = null
    }
}
