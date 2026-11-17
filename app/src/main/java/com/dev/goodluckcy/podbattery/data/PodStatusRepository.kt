package com.dev.goodluckcy.podbattery.data

import android.content.Context
import androidx.core.content.edit
import com.dev.goodluckcy.podbattery.bluetooth.PodsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 마지막으로 확인한 배터리와 자동 확인 설정을 들고 있는 곳.
 * 스캔 결과는 앱이 꺼져 있을 때도 오므로 SharedPreferences에 같이 저장한다.
 */
object PodStatusRepository {

    private const val PREFS = "pod_status"
    private const val KEY_ENABLED = "scan_enabled"
    private const val NONE = -1

    /** 같은 값이라도 이 시간이 지나면 다시 저장해 "n분 전" 표시를 갱신한다. */
    private const val REFRESH_MS = 60_000L

    private val _status = MutableStateFlow<PodsStatus?>(null)
    val status: StateFlow<PodsStatus?> = _status.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = prefs(context)
        _enabled.value = p.getBoolean(KEY_ENABLED, false)
        if (p.contains("updatedAt")) {
            _status.value = PodsStatus(
                model = p.getString("model", null) ?: "Pod",
                left = p.getInt("left", NONE).takeIf { it != NONE },
                right = p.getInt("right", NONE).takeIf { it != NONE },
                case = p.getInt("case", NONE).takeIf { it != NONE },
                leftCharging = p.getBoolean("leftCharging", false),
                rightCharging = p.getBoolean("rightCharging", false),
                caseCharging = p.getBoolean("caseCharging", false),
                updatedAt = p.getLong("updatedAt", 0L),
            )
        }
        loaded = true
    }

    /** 새 값을 저장한다. 직전과 같은 값이 방금 저장됐다면 건너뛰고 false. */
    fun save(context: Context, status: PodsStatus): Boolean {
        load(context)
        val previous = _status.value
        if (status.sameReadingAs(previous) && status.updatedAt - previous!!.updatedAt < REFRESH_MS) return false
        _status.value = status
        prefs(context).edit {
            putString("model", status.model)
            putInt("left", status.left ?: NONE)
            putInt("right", status.right ?: NONE)
            putInt("case", status.case ?: NONE)
            putBoolean("leftCharging", status.leftCharging)
            putBoolean("rightCharging", status.rightCharging)
            putBoolean("caseCharging", status.caseCharging)
            putLong("updatedAt", status.updatedAt)
        }
        return true
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        load(context)
        _enabled.value = enabled
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun isEnabled(context: Context): Boolean {
        load(context)
        return _enabled.value
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
