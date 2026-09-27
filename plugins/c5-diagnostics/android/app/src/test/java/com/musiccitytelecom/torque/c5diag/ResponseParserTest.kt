package com.musiccitytelecom.torque.c5diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ResponseParserTest {
    @Test
    fun parsesMode22WithClass2Header() {
        val payload = ResponseParser.parseMode22(
            listOf("6C F1 10 62 12 06 0A"),
            0x1206,
            1
        )
        assertNotNull(payload)
        assertEquals(10, payload!![0].toInt() and 0xFF)
    }

    @Test
    fun parsesCompactCombinedWheelPacket() {
        val wheel = ResponseParser.parseWheelPacket(
            listOf("6CF1286A200B0C0D0E0F")
        )
        assertNotNull(wheel)
        assertEquals(11.0, wheel!!.lfKph, 0.0)
        assertEquals(12.0, wheel.rfKph, 0.0)
        assertEquals(13.0, wheel.lrKph, 0.0)
        assertEquals(14.0, wheel.rrKph, 0.0)
        assertEquals(15.0, wheel.vssKph, 0.0)
        assertEquals(-1, wheel.aux)
    }

    @Test
    fun parsesWheelPacketBeforeTorqueDataErrorTrailer() {
        val wheel = ResponseParser.parseWheelPacket(
            listOf("6A200000000000<DATA ERROR")
        )
        assertNotNull(wheel)
        assertEquals(0.0, wheel!!.lfKph, 0.0)
        assertEquals(0.0, wheel.rfKph, 0.0)
        assertEquals(0.0, wheel.lrKph, 0.0)
        assertEquals(0.0, wheel.rrKph, 0.0)
    }

    @Test
    fun parsesMode01BeforeTorqueDataErrorTrailer() {
        val payload = ResponseParser.parseMode01(
            listOf("6CF110410C1AF8<DATA ERROR"),
            0x0C,
            2
        )
        assertNotNull(payload)
        assertEquals(0x1AF8, ResponseParser.u16(payload!!))
    }

    @Test
    fun ignoresAdapterStatusText() {
        assertNull(ResponseParser.cleanHexLine("SEARCHING..."))
        assertNull(ResponseParser.cleanHexLine("NO DATA"))
        assertNull(ResponseParser.cleanHexLine("<DATA ERROR"))
    }

    @Test
    fun parsesMode01() {
        val payload = ResponseParser.parseMode01(
            listOf("6C F1 10 41 0C 1A F8"),
            0x0C,
            2
        )
        assertNotNull(payload)
        assertEquals(0x1AF8, ResponseParser.u16(payload!!))
    }
}
