package com.dev.goodluckcy.podbattery.data

import com.dev.goodluckcy.podbattery.bluetooth.PodsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 서비스가 받은 최신 배터리 상태를 화면/알림/위젯이 함께 보도록 들고 있는 곳. */
object PodStatusRepository {

    private val _status = MutableStateFlow<PodsStatus?>(null)
    val status: StateFlow<PodsStatus?> = _status.asStateFlow()

    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    fun update(status: PodsStatus) {
        _status.value = status
    }

    fun setMonitoring(active: Boolean) {
        _monitoring.value = active
        if (!active) _status.value = null
    }
}
