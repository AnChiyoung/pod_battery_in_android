package com.dev.goodluckcy.podbattery.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import com.dev.goodluckcy.podbattery.bluetooth.PodsStatus
import com.dev.goodluckcy.podbattery.bluetooth.ble.PairingAdvert
import com.dev.goodluckcy.podbattery.bluetooth.ble.PodScanner
import com.dev.goodluckcy.podbattery.data.PodStatusRepository
import com.dev.goodluckcy.podbattery.notification.PodNotification

/**
 * 백그라운드 스캔([PodScanner]) 결과를 받아 배터리를 저장하고 알림을 갱신한다.
 *
 * 페어링 모드 광고는 에어팟의 실제 주소로 나오므로, 폰에 페어링된 기기와 주소가 같은 것을 우선한다.
 * 페어링되지 않은 기기는 바로 옆(신호 강함)에 있을 때만 받아들인다.
 */
class PodScanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val error = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
        if (error != 0) {
            Log.w(TAG, "scan error $error")
            return
        }
        val results = IntentCompat.getParcelableArrayListExtra(
            intent, BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java,
        ) ?: return

        val bonded = bondedNames(context)
        val best = results
            .mapNotNull { r ->
                val data = r.scanRecord?.getManufacturerSpecificData(PairingAdvert.APPLE_COMPANY_ID)
                PairingAdvert.parse(data)?.let { r to it }
            }
            .maxByOrNull { (r, _) -> (if (r.device.address in bonded) BONDED_BONUS else 0) + r.rssi }
            ?: return
        val (result, battery) = best
        val name = bonded[result.device.address]
        if (name == null && result.rssi < NEARBY_RSSI) return

        val status = PodsStatus(
            model = name ?: DEFAULT_NAME,
            left = battery.left,
            right = battery.right,
            case = battery.case,
            leftCharging = battery.leftCharging,
            rightCharging = battery.rightCharging,
            caseCharging = battery.caseCharging,
            updatedAt = System.currentTimeMillis(),
        )
        if (PodStatusRepository.save(context, status)) {
            PodNotification.notify(context, status)
        }
    }

    @SuppressLint("MissingPermission") // hasPermissions()로 확인
    private fun bondedNames(context: Context): Map<String, String> {
        if (!PodScanner.hasPermissions(context)) return emptyMap()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyMap()
        return adapter.bondedDevices.orEmpty().associate { it.address to (it.name ?: DEFAULT_NAME) }
    }

    companion object {
        private const val TAG = "PodScanReceiver"
        private const val DEFAULT_NAME = "Pod"
        private const val BONDED_BONUS = 1000
        private const val NEARBY_RSSI = -60
    }
}
