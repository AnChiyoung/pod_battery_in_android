package com.dev.goodluckcy.podbattery.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.dev.goodluckcy.podbattery.bluetooth.aap.AapSocketFactory

/**
 * 에어팟이 연결/해제될 때 모니터링 서비스를 켜고 끈다.
 * ACL_CONNECTED/DISCONNECTED는 앱이 꺼져 있어도 매니페스트 리시버로 받을 수 있는 예외 브로드캐스트다.
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(
            intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java,
        ) ?: return
        if (!isAirPods(context, device)) return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> PodMonitorService.start(context, device.address)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> PodMonitorService.stop(context)
        }
    }

    @SuppressLint("MissingPermission") // 아래에서 확인
    private fun isAirPods(context: Context, device: BluetoothDevice): Boolean {
        if (!PodMonitorService.hasConnectPermission(context)) return false
        // AAP 서비스 UUID를 광고하는 기기 = 에어팟/비츠 계열
        return device.uuids?.any { it == AapSocketFactory.AAP_UUID } == true
    }
}
