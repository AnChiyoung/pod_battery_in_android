package com.dev.goodluckcy.podbattery.bluetooth.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingAdvertTest {

    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    // 실기기(AirPods Pro 3, 모델 0x2027)에서 받은 광고. 뒤 18바이트는 암호화된 부분이라 생략하지 않고 그대로 둔다.
    private val bothInCase = hex("07 19 07 27 20 35 D0 D0 10 72 D6 8C C7 98 F8 FC D6 77 CE E3 8A B4 6D 6D F9 FA B1")
    private val leftOut = hex("07 19 07 27 20 31 50 D0 10 12 D6 8C C7 98 F8 FC D6 77 CE E3 8A B4 6D 6D F9 FA B1")

    @Test
    fun parsesBothPodsChargingInCase() {
        val b = PairingAdvert.parse(bothInCase)!!
        assertEquals(0x2027, b.model)
        assertEquals(80, b.left)
        assertEquals(80, b.right)
        assertEquals(16, b.case)
        assertTrue(b.leftCharging)
        assertTrue(b.rightCharging)
        assertFalse(b.caseCharging)
    }

    @Test
    fun leftPodTakenOutStopsCharging() {
        val b = PairingAdvert.parse(leftOut)!!
        assertEquals(80, b.left)
        assertFalse(b.leftCharging)
        assertTrue(b.rightCharging)
    }

    @Test
    fun primaryRightSwapsOrder() {
        // 상태 0x20 비트가 꺼지면 첫 바이트가 오른쪽
        val b = PairingAdvert.parse(hex("07 19 07 27 20 15 50 D0 10"))!!
        assertEquals(80, b.right)
        assertFalse(b.rightCharging)
        assertTrue(b.leftCharging)
    }

    @Test
    fun unknownLevelIsNull() {
        val b = PairingAdvert.parse(hex("07 19 07 27 20 35 FF D0 7F"))!!
        assertNull(b.left)
        assertFalse(b.leftCharging)
        assertNull(b.case)
    }

    @Test
    fun ignoresOtherAdverts() {
        assertNull(PairingAdvert.parse(hex("12 02 00 02")))
        assertNull(PairingAdvert.parse(hex("07 19 01 27 20 35 D0 D0 10")))
        assertNull(PairingAdvert.parse(null))
    }
}
