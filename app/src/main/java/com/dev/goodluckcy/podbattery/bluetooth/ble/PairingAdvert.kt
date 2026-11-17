package com.dev.goodluckcy.podbattery.bluetooth.ble

/**
 * 에어팟이 페어링 모드(케이스 표시등이 흰색으로 깜빡임)일 때 보내는 BLE 광고를 해석한다.
 *
 * Apple 제조사 데이터(회사 ID 0x004C) 형식:
 *   07 19 07 [모델 2바이트, 리틀엔디언] [상태] [왼쪽] [오른쪽] [케이스] ...(나머지는 암호화)
 * 배터리 바이트: 맨 위 비트 = 충전 중, 아래 7비트 = 잔량(%). 100을 넘으면 알 수 없음.
 * 상태의 0x20 비트가 꺼져 있으면 왼쪽/오른쪽 순서가 바뀐다(주 이어폰이 오른쪽일 때).
 *
 * 평소(페어링 모드가 아닐 때)에는 에어팟이 이 정보를 암호화하지 않은 채로 보내지 않는다.
 */
object PairingAdvert {

    const val APPLE_COMPANY_ID = 0x004C

    /** 스캔 필터용: 데이터 앞부분이 이 값이면 페어링 모드 광고. */
    val PREFIX = byteArrayOf(0x07, 0x19, 0x07)

    private const val MIN_SIZE = 8
    private const val STATUS_PRIMARY_LEFT = 0x20
    private const val CHARGING_BIT = 0x80
    private const val LEVEL_MASK = 0x7F

    data class Battery(
        val model: Int,
        val left: Int?,
        val right: Int?,
        val case: Int?,
        val leftCharging: Boolean,
        val rightCharging: Boolean,
        val caseCharging: Boolean,
    )

    /** [data]는 ScanRecord.getManufacturerSpecificData(0x004C) 값. 페어링 모드 광고가 아니면 null. */
    fun parse(data: ByteArray?): Battery? {
        if (data == null || data.size < MIN_SIZE) return null
        if (PREFIX.indices.any { data[it] != PREFIX[it] }) return null

        val model = (data[3].toInt() and 0xFF) or ((data[4].toInt() and 0xFF) shl 8)
        val status = data[5].toInt() and 0xFF
        val first = data[6].toInt() and 0xFF
        val second = data[7].toInt() and 0xFF
        val case = if (data.size > 8) data[8].toInt() and 0xFF else 0xFF

        val primaryLeft = status and STATUS_PRIMARY_LEFT != 0
        val left = if (primaryLeft) first else second
        val right = if (primaryLeft) second else first

        return Battery(
            model = model,
            left = level(left),
            right = level(right),
            case = level(case),
            leftCharging = charging(left),
            rightCharging = charging(right),
            caseCharging = charging(case),
        )
    }

    private fun level(b: Int): Int? = (b and LEVEL_MASK).takeIf { it <= 100 }

    private fun charging(b: Int): Boolean = level(b) != null && b and CHARGING_BIT != 0
}
