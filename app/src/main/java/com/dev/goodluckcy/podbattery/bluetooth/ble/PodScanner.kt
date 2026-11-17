package com.dev.goodluckcy.podbattery.bluetooth.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.dev.goodluckcy.podbattery.service.PodScanReceiver

/**
 * 에어팟 페어링 모드 광고를 기다리는 백그라운드 BLE 스캔.
 * PendingIntent로 등록해서 앱이 꺼져 있어도 시스템이 결과를 [PodScanReceiver]로 보내 준다.
 * 블루투스를 끄거나 재부팅하면 스캔이 풀리므로 [com.dev.goodluckcy.podbattery.service.BluetoothConnectionReceiver]에서 다시 건다.
 */
object PodScanner {

    private const val TAG = "PodScanner"
    private const val REQUEST_CODE = 1
    private const val REPORT_DELAY_MS = 2_000L

    val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )

    fun hasPermissions(context: Context): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** 스캔을 건다. 블루투스가 꺼져 있거나 권한이 없으면 false. */
    @SuppressLint("MissingPermission") // hasPermissions()로 확인
    fun start(context: Context): Boolean {
        if (!hasPermissions(context)) return false
        val adapter = context.getSystemService(BluetoothManager::class.java)
            ?.adapter?.takeIf { it.isEnabled } ?: return false
        val scanner = adapter.bluetoothLeScanner ?: return false

        val filter = ScanFilter.Builder()
            .setManufacturerData(
                PairingAdvert.APPLE_COMPANY_ID,
                PairingAdvert.PREFIX,
                ByteArray(PairingAdvert.PREFIX.size) { 0xFF.toByte() },
            )
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            // 페어링 모드 동안 광고가 초당 수십 번 와서, 칩이 지원하면 모아서 받아 앱을 덜 깨운다.
            .apply { if (adapter.isOffloadedScanBatchingSupported) setReportDelay(REPORT_DELAY_MS) }
            .build()
        return try {
            val code = scanner.startScan(listOf(filter), settings, pendingIntent(context))
            if (code != 0) Log.w(TAG, "startScan returned $code")
            code == 0
        } catch (e: Exception) {
            Log.w(TAG, "startScan failed", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(context: Context) {
        if (!hasPermissions(context)) return
        val scanner = context.getSystemService(BluetoothManager::class.java)
            ?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return
        runCatching { scanner.stopScan(pendingIntent(context)) }
    }

    // 시스템이 스캔 결과를 extra로 채워 넣어야 해서 MUTABLE이어야 한다.
    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, PodScanReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}
