package com.dev.goodluckcy.podbattery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dev.goodluckcy.podbattery.bluetooth.ble.PodScanner
import com.dev.goodluckcy.podbattery.data.PodStatusRepository

/**
 * 백그라운드 스캔은 재부팅, 앱 업데이트, 블루투스 재시작 때 풀린다. 그런 때 다시 건다.
 * ACL_CONNECTED, BOOT_COMPLETED, MY_PACKAGE_REPLACED는 앱이 꺼져 있어도 매니페스트 리시버로 받을 수 있다.
 * (블루투스를 껐다 켜면 기기가 다시 연결되면서 ACL_CONNECTED가 온다.)
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (PodStatusRepository.isEnabled(context)) {
            PodScanner.start(context)
        }
    }
}
