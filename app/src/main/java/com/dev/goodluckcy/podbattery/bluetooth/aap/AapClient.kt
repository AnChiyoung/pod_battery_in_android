package com.dev.goodluckcy.podbattery.bluetooth.aap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log

/**
 * 에어팟과 AAP 연결을 하나 유지하며 배터리 패킷을 받는다.
 * 연결이 끊기면 [Listener.onClosed]를 한 번 부르고 끝난다. 재연결은 호출하는 쪽에서 새 인스턴스로 한다.
 */
class AapClient(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val listener: Listener,
) {
    interface Listener {
        fun onBattery(battery: AapPackets.Battery)
        fun onClosed(error: Throwable?)
    }

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var closed = false

    fun start() {
        Thread(::run, "AapClient").apply {
            isDaemon = true
            start()
        }
    }

    fun close() {
        closed = true
        runCatching { socket?.close() }
    }

    @SuppressLint("MissingPermission") // 서비스에서 BLUETOOTH_CONNECT 확인 후 호출
    private fun run() {
        var error: Throwable? = null
        try {
            val s = AapSocketFactory.create(adapter, device)
            socket = s
            if (closed) return
            s.connect()
            Log.i(TAG, "AAP connected")

            // 에어팟은 handshake를 처리할 시간이 필요해서, 나머지 요청은 잠시 기다렸다 보낸다.
            Thread({ sendInitSequence(s) }, "AapClient-init").apply {
                isDaemon = true
                start()
            }

            val input = s.inputStream
            val buffer = ByteArray(4096)
            while (!closed) {
                val n = input.read(buffer)
                if (n <= 0) break
                val packet = buffer.copyOf(n)
                AapPackets.parseBattery(packet)?.let {
                    Log.d(TAG, "battery raw=${packet.joinToString(" ") { b -> "%02X".format(b) }} -> $it")
                    listener.onBattery(it)
                }
            }
        } catch (e: Throwable) {
            if (!closed) error = e
        } finally {
            runCatching { socket?.close() }
            Log.i(TAG, "AAP closed", error)
            listener.onClosed(error)
        }
    }

    private fun sendInitSequence(s: BluetoothSocket) {
        try {
            val out = s.outputStream
            out.write(AapPackets.HANDSHAKE)
            Thread.sleep(HANDSHAKE_DELAY_MS)
            out.write(AapPackets.SET_FEATURES)
            Thread.sleep(STEP_DELAY_MS)
            out.write(AapPackets.REQUEST_NOTIFICATIONS)
        } catch (e: Throwable) {
            if (!closed) Log.w(TAG, "init sequence failed", e)
        }
    }

    companion object {
        private const val TAG = "AapClient"
        private const val HANDSHAKE_DELAY_MS = 2_000L
        private const val STEP_DELAY_MS = 500L
    }
}
