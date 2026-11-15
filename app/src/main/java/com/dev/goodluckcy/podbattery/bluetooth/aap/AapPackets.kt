package com.dev.goodluckcy.podbattery.bluetooth.aap

/**
 * AAP(Apple Accessory Protocol) 패킷 정의와 해석.
 *
 * 배터리 패킷: 04 00 04 00 04 00 [개수] ([부위][01][잔량][상태][01]) × 개수
 * - 부위: 0x04 왼쪽, 0x02 오른쪽, 0x08 케이스
 * - 상태: 0x01 충전 중, 0x02 충전 안 함, 0x04 연결 안 됨.
 *   실기기에서 케이스 안의 이어폰이 0x05로 온 적이 있어, 0x01 비트를 충전으로 본다.
 */
object AapPackets {

    val HANDSHAKE = hex("00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00")
    val SET_FEATURES = hex("04 00 04 00 4D 00 D7 00 00 00 00 00 00 00")
    val REQUEST_NOTIFICATIONS = hex("04 00 04 00 0F 00 FF FF FF FF")

    private const val PART_RIGHT = 0x02
    private const val PART_LEFT = 0x04
    private const val PART_CASE = 0x08
    private const val STATE_CHARGING_BIT = 0x01
    private const val STATE_DISCONNECTED = 0x04

    data class Battery(
        val left: Int? = null,
        val right: Int? = null,
        val case: Int? = null,
        val leftCharging: Boolean = false,
        val rightCharging: Boolean = false,
        val caseCharging: Boolean = false,
    )

    fun parseBattery(p: ByteArray): Battery? {
        if (p.size < 7) return null
        if (p[0] != 0x04.toByte() || p[1] != 0x00.toByte() ||
            p[4] != 0x04.toByte() || p[5] != 0x00.toByte()
        ) return null
        val count = p[6].toInt() and 0xFF
        if (count == 0 || p.size < 7 + count * 5) return null

        var battery = Battery()
        for (i in 0 until count) {
            val o = 7 + i * 5
            val part = p[o].toInt() and 0xFF
            val state = p[o + 3].toInt() and 0xFF
            val level = (p[o + 2].toInt() and 0xFF)
                .takeIf { state != STATE_DISCONNECTED && it in 0..100 }
            val charging = level != null && (state and STATE_CHARGING_BIT) != 0
            battery = when (part) {
                PART_LEFT -> battery.copy(left = level, leftCharging = charging)
                PART_RIGHT -> battery.copy(right = level, rightCharging = charging)
                PART_CASE -> battery.copy(case = level, caseCharging = charging)
                else -> battery
            }
        }
        return battery
    }

    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
