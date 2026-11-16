package com.dev.goodluckcy.podbattery.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.dev.goodluckcy.podbattery.MainActivity
import com.dev.goodluckcy.podbattery.R
import com.dev.goodluckcy.podbattery.bluetooth.PodsStatus
import com.dev.goodluckcy.podbattery.service.PodMonitorService

/**
 * 잠금화면에 보이는 진행 중 알림.
 *
 * 배터리를 받은 뒤에는 직접 그린 레이아웃(아이콘 + 퍼센트 + 게이지)으로 보여준다.
 * 커스텀 레이아웃 알림은 Now Bar / 상태바 칩으로 승격되지 않는다.
 */
object PodNotification {

    const val ID = 1001
    private const val CHANNEL_ID = "pod_battery"

    private const val LOW_BATTERY = 20
    private val COLOR_NORMAL = Color.parseColor("#34C759")
    private val COLOR_LOW = Color.parseColor("#FF3B30")

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

        val title = status?.model ?: "Pod에 연결하는 중"
        val text = status?.let(::describe) ?: "잠시 후 배터리가 표시됩니다"

        // 제목/본문은 커스텀 레이아웃을 못 그리는 곳(접근성, 일부 잠금화면 모드)에서 쓰인다.
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC) // 잠금화면에서 내용까지 표시
            .addAction(
                Notification.Action.Builder(null, "중지", stop).build(),
            )

        if (status == null) {
            builder.setStyle(Notification.BigTextStyle().bigText(text))
        } else {
            builder
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomContentView(views(context, status, big = false))
                .setCustomBigContentView(views(context, status, big = true))
        }

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

    private fun views(context: Context, status: PodsStatus, big: Boolean): RemoteViews {
        val layout = if (big) R.layout.notification_pods_big else R.layout.notification_pods
        return RemoteViews(context.packageName, layout).apply {
            bindPart(this, big, R.id.left_percent, R.id.left_bolt, R.id.left_bar, status.left, status.leftCharging)
            bindPart(this, big, R.id.right_percent, R.id.right_bolt, R.id.right_bar, status.right, status.rightCharging)
            bindPart(this, big, R.id.case_percent, R.id.case_bolt, R.id.case_bar, status.case, status.caseCharging)
            // 케이스 값이 이전 값이면 아이콘을 흐리게
            setInt(R.id.case_icon, "setImageAlpha", if (status.caseStale) 90 else 255)
            setTextViewText(R.id.case_label, if (status.caseStale) "케이스(이전)" else "케이스")
            if (big) setTextViewText(R.id.model, status.model)
        }
    }

    private fun bindPart(
        views: RemoteViews,
        big: Boolean,
        percentId: Int,
        boltId: Int,
        barId: Int,
        value: Int?,
        charging: Boolean,
    ) {
        views.setTextViewText(percentId, value?.let { "$it%" } ?: "--")
        views.setViewVisibility(boltId, if (charging) View.VISIBLE else View.GONE)
        val low = value != null && value <= LOW_BATTERY && !charging
        if (low) views.setTextColor(percentId, COLOR_LOW)
        // 접힌 레이아웃에는 게이지가 없다. 없는 뷰를 건드리면 알림이 깨지므로 펼친 쪽에서만.
        if (big) {
            views.setProgressBar(barId, 100, value ?: 0, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(
                    barId, "setProgressTintList",
                    ColorStateList.valueOf(if (low) COLOR_LOW else COLOR_NORMAL),
                )
            }
        }
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
        // 이미 있는 채널이면 이름만 갱신된다(중요도 등 사용자가 바꾼 설정은 유지).
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
