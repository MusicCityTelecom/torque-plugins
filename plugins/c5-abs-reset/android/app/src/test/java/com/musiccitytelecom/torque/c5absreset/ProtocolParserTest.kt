package com.musiccitytelecom.torque.c5absreset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolParserTest {
    @Test fun decodesClass2ChassisDtc() {
        val dtcs = ProtocolParser.parseDtcs(listOf("6C F1 28 59 52 26 10 7A"), "current")
        assertEquals("C1226", dtcs.single().code)
        assertEquals(0x10, dtcs.single().status)
    }
    @Test fun acceptsCompactTorqueResponseWithTrailer() {
        val dtcs = ProtocolParser.parseDtcs(listOf("6CF12859523310<DATA ERROR"), "current")
        assertEquals("C1233", dtcs.single().code)
    }
    @Test fun recognizesClearAck() {
        assertTrue(ProtocolParser.clearAcknowledged(listOf("6C F1 28 54 A1")))
        assertTrue(ProtocolParser.clearAcknowledged(listOf("54")))
        assertFalse(ProtocolParser.clearAcknowledged(listOf("7F 14 22")))
    }
    @Test fun parsesVehicleSpeed() {
        assertEquals(32.0,
            ProtocolParser.parseVehicleSpeedKph(listOf("6C F1 10 41 0D 20 88")),
            0.0)
    }
}
