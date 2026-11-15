package com.dev.goodluckcy.podbattery.bluetooth.aap

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.ParcelUuid
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.UUID

/**
 * 에어팟 AAP 채널(BR/EDR L2CAP, PSM 0x1001) 소켓을 만든다.
 * 공개 API에 BR/EDR L2CAP 소켓이 없어서 숨겨진 BluetoothSocket 생성자를 리플렉션으로 호출한다.
 * 생성자 시그니처가 안드로이드 버전마다 달라서 파라미터 타입 순서로 인자를 채운다.
 */
object AapSocketFactory {

    val AAP_UUID: ParcelUuid = ParcelUuid(UUID.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a"))
    private const val PSM_AAP = 0x1001
    private const val TYPE_L2CAP = 3

    @Volatile private var exempted = false

    fun create(adapter: BluetoothAdapter, device: BluetoothDevice): BluetoothSocket {
        if (!exempted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothSocket;")
            exempted = true
        }

        var lastError: Throwable? = null
        val constructors = BluetoothSocket::class.java.declaredConstructors
            .filter { BluetoothDevice::class.java in it.parameterTypes }
            .sortedBy { it.parameterCount }

        for (c in constructors) {
            // 순서: [adapter] [device] type [fd] auth encrypt [device] port uuid [mitm min16 ...]
            val types = c.parameterTypes
            val uuidIndex = types.indexOf(ParcelUuid::class.java)
            var typeSet = false
            var portSet = false
            var seenBoolean = false
            val args = types.mapIndexed { i, t ->
                when (t) {
                    Int::class.javaPrimitiveType -> when {
                        !typeSet -> { typeSet = true; TYPE_L2CAP }
                        !seenBoolean -> -1 // fd
                        !portSet -> { portSet = true; PSM_AAP }
                        else -> 0 // dataPath, maxRxPacketSize 등
                    }
                    Long::class.javaPrimitiveType -> 0L
                    Boolean::class.javaPrimitiveType -> {
                        seenBoolean = true
                        i < uuidIndex // auth/encrypt = true, uuid 뒤(mitm 등)는 false
                    }
                    BluetoothAdapter::class.java -> adapter
                    BluetoothDevice::class.java -> device
                    ParcelUuid::class.java -> AAP_UUID
                    else -> null
                }
            }.toTypedArray()
            try {
                c.isAccessible = true
                return c.newInstance(*args) as BluetoothSocket
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw IllegalStateException("AAP 소켓을 만들 수 없음", lastError)
    }
}
