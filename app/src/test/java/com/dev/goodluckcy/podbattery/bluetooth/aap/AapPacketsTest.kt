package com.dev.goodluckcy.podbattery.bluetooth.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AapPacketsTest {

    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun parsesBatteryCapturedFromAirPodsPro2() {
        // 실기기(AirPods Pro 2)에서 받은 패킷
        val b = AapPackets.parseBattery(
            hex("04 00 04 00 04 00 03 04 01 50 05 01 02 01 50 05 01 08 01 32 02 01"),
        )!!
        assertEquals(80, b.left)
        assertEquals(80, b.right)
        assertEquals(50, b.case)
        assertTrue(b.leftCharging)
        assertTrue(b.rightCharging)
        assertFalse(b.caseCharging)
    }

    @Test
    fun disconnectedPartIsNull() {
        val b = AapPackets.parseBattery(
            hex("04 00 04 00 04 00 02 04 01 64 02 01 08 01 00 04 01"),
        )!!
        assertEquals(100, b.left)
        assertFalse(b.leftCharging)
        assertNull(b.right)
        assertNull(b.case)
    }

    @Test
    fun ignoresOtherPackets() {
        assertNull(AapPackets.parseBattery(hex("04 00 04 00 09 00 0D 03 00 00 00")))
        assertNull(AapPackets.parseBattery(hex("01 00 04 00 00 00 01 00 03 00")))
        assertNull(AapPackets.parseBattery(hex("04 00 04 00 04 00 03 04 01")))
    }
}
