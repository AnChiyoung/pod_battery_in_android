package com.dev.goodluckcy.podbattery.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.dev.goodluckcy.podbattery.MainActivity
import com.dev.goodluckcy.podbattery.R
import com.dev.goodluckcy.podbattery.bluetooth.PodsStatus
import com.dev.goodluckcy.podbattery.service.PodMonitorService

/**
 * 잠금화면에 보이는 진행 중 알림.
 *
 * Android 16+ (One UI 8+)에서는 Live Updates(승격된 진행 중 알림)를 요청해
 * 삼성 Now Bar / 상태바 칩에도 보이도록 한다. 시스템이 거부하면 일반 진행 중 알림으로 남는다.
 */
object PodNotification {

    const val ID = 1001
    private const val CHANNEL_ID = "pod_battery"

    // Android 16 Live Updates 키. 상수 대신 문자열을 써서 compileSdk/라이브러리 버전에 묶이지 않게 한다.
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"

    fun build(context: Context, status: PodsStatus?): Notification {
        ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            context, 1,
            Intent(context, PodMonitorService::class.java)
                .setAction(PodMonitorService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = status?.model ?: "에어팟에 연결하는 중"
        val text = status?.let(::describe) ?: "잠시 후 배터리가 표시됩니다"

        val extras = Bundle().apply {
            putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            status?.lowestPod?.let { putCharSequence(EXTRA_SHORT_CRITICAL_TEXT, "$it%") }
        }

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC) // 잠금화면에서 내용까지 표시
            .addAction(
                Notification.Action.Builder(null, "중지", stop).build(),
            )
            .addExtras(extras)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    fun notify(context: Context, status: PodsStatus) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !manager.areNotificationsEnabled()
        ) return
        manager.notify(ID, build(context, status))
    }

    private fun describe(status: PodsStatus): String {
        fun part(label: String, value: Int?, charging: Boolean): String? =
            value?.let { "$label $it%" + if (charging) "⚡" else "" }

        return listOfNotNull(
            part("왼쪽", status.left, status.leftCharging),
            part("오른쪽", status.right, status.rightCharging),
            part(if (status.caseStale) "케이스(이전)" else "케이스", status.case, status.caseCharging),
        ).joinToString("  ·  ").ifEmpty { "배터리 정보 없음" }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        // Live Updates는 IMPORTANCE_MIN 채널에서는 승격되지 않으므로 DEFAULT + 무음으로 만든다.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_pod_battery),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }
}
