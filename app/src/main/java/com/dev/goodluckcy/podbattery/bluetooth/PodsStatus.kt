package com.dev.goodluckcy.podbattery.bluetooth

/**
 * 에어팟 한 쌍의 배터리 상태. 배터리 값은 0~100(%), 알 수 없으면 null.
 */
data class PodsStatus(
    val model: String,
    val left: Int?,
    val right: Int?,
    val case: Int?,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    /** 이 값을 받은 시각(epoch ms). */
    val updatedAt: Long = 0L,
) {
    /** 시각을 뺀 배터리 내용이 같은지. 같은 광고가 연달아 올 때 알림을 다시 그리지 않으려고 쓴다. */
    fun sameReadingAs(other: PodsStatus?): Boolean =
        other != null && copy(updatedAt = 0L) == other.copy(updatedAt = 0L)
}
