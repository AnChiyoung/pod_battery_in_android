package com.dev.goodluckcy.podbattery.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dev.goodluckcy.podbattery.bluetooth.PodsStatus
import com.dev.goodluckcy.podbattery.bluetooth.aap.AapClient
import com.dev.goodluckcy.podbattery.bluetooth.aap.AapPackets
import com.dev.goodluckcy.podbattery.bluetooth.aap.AapSocketFactory
import com.dev.goodluckcy.podbattery.data.PodStatusRepository
import com.dev.goodluckcy.podbattery.notification.PodNotification

/**
 * 에어팟이 연결돼 있는 동안 AAP 연결로 배터리를 받아 알림(잠금화면/Now Bar)에 띄우는 서비스.
 * 연결이 끊기면 몇 번 재시도하고, 그래도 안 되면 종료한다.
 */
class PodMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var client: AapClient? = null
    private var device: BluetoothDevice? = null
    private var lastStatus: PodsStatus? = null
    private var retries = 0

    @SuppressLint("MissingPermission") // hasConnectPermission()으로 확인
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(
                this, PodNotification.ID, PodNotification.build(this, lastStatus), type,
            )
        } catch (e: Exception) {
            // 백그라운드 시작 제한 등으로 실패하면 조용히 종료
            Log.w(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }

        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (!hasConnectPermission(this) || adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "No permission or bluetooth off")
            stopSelf()
            return START_NOT_STICKY
        }

        val address = intent?.getStringExtra(EXTRA_ADDRESS)
        val target = address?.let { adapter.getRemoteDevice(it) } ?: findAirPods(adapter)
        if (target == null) {
            Log.w(TAG, "No paired AirPods")
            stopSelf()
            return START_NOT_STICKY
        }

        if (client == null || target != device) {
            device = target
            retries = 0
            connect(adapter, target)
        }
        PodStatusRepository.setMonitoring(true)
        return START_NOT_STICKY
    }

    private fun connect(adapter: BluetoothAdapter, target: BluetoothDevice) {
        client?.close()
        client = AapClient(adapter, target, object : AapClient.Listener {
            override fun onBattery(battery: AapPackets.Battery) {
                handler.post { onBattery(target, battery) }
            }

            override fun onClosed(error: Throwable?) {
                handler.post { onClientClosed(adapter, target) }
            }
        }).also { it.start() }
    }

    @SuppressLint("MissingPermission")
    private fun onBattery(target: BluetoothDevice, battery: AapPackets.Battery) {
        retries = 0
        // 케이스 배터리가 안 오면(이어폰이 케이스 밖) 마지막으로 확인한 값을 "이전 값"으로 보여준다.
        val previousCase = lastStatus?.case
        val caseStale = battery.case == null && previousCase != null
        val status = PodsStatus(
            // 사용자가 붙인 기기 이름을 그대로 보여주고, 없을 때만 기본 이름을 쓴다.
            model = target.name ?: DEFAULT_NAME,
            left = battery.left,
            right = battery.right,
            case = battery.case ?: previousCase,
            leftCharging = battery.leftCharging,
            rightCharging = battery.rightCharging,
            caseCharging = battery.caseCharging,
            caseStale = caseStale,
        )
        PodStatusRepository.update(status)
        if (status == lastStatus) return
        lastStatus = status
        PodNotification.notify(this, status)
    }

    private fun onClientClosed(adapter: BluetoothAdapter, target: BluetoothDevice) {
        if (client == null || target != device) return // 이미 정리됨/다른 기기로 바뀜
        if (retries >= MAX_RETRIES) {
            Log.w(TAG, "AAP reconnect gave up")
            stopSelf()
            return
        }
        retries++
        handler.postDelayed({
            if (device == target) connect(adapter, target)
        }, RETRY_DELAY_MS * retries)
    }

    @SuppressLint("MissingPermission")
    private fun findAirPods(adapter: BluetoothAdapter): BluetoothDevice? =
        adapter.bondedDevices?.firstOrNull { d ->
            d.uuids?.any { it == AapSocketFactory.AAP_UUID } == true
        }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        device = null
        client?.close()
        client = null
        PodStatusRepository.setMonitoring(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PodMonitorService"
        private const val DEFAULT_NAME = "Pod"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 3_000L
        const val ACTION_STOP = "com.dev.goodluckcy.podbattery.action.STOP"
        private const val EXTRA_ADDRESS = "address"

        /** [address]가 없으면 페어링된 에어팟 중 첫 번째에 연결한다. */
        fun start(context: Context, address: String? = null) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PodMonitorService::class.java)
                        .putExtra(EXTRA_ADDRESS, address),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Cannot start service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PodMonitorService::class.java))
        }

        fun hasConnectPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    }
}
