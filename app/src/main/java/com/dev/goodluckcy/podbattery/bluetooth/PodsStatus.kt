package com.dev.goodluckcy.podbattery.bluetooth

/**
 * 에어팟 한 쌍의 배터리 상태. 배터리 값은 0~100(10% 단위), 알 수 없으면 null.
 */
data class PodsStatus(
    val model: String,
    val left: Int?,
    val right: Int?,
    val case: Int?,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    /** 케이스 값이 이번에 받은 게 아니라 마지막으로 확인한 값인지. 이어폰이 케이스 밖에 있으면 케이스 배터리가 오지 않는다. */
    val caseStale: Boolean = false,
) {
    /** 왼쪽/오른쪽 중 낮은 값. 잠금화면 칩처럼 짧게 보여줄 때 사용. */
    val lowestPod: Int?
        get() = listOfNotNull(left, right).minOrNull()
}
