package com.musiccitytelecom.torque.c5absreset

import kotlin.math.max

class AbsResetEngine(private val torque: TorqueClient) {
    companion object {
        private const val PCM_HEADER = "6C10F1"
        private const val EBCM_HEADER = "6C28F1"
        private const val BUS_GAP_MS = 300L
        private const val STATIONARY_VERIFY_MS = 750L
    }

    fun readVehicleSpeedKph(): Double? =
        ProtocolParser.parseVehicleSpeedKph(torque.query(PCM_HEADER, "010D"))

    fun readCodes(): AbsReadResult {
        if (!torque.hasFullPermissions()) return AbsReadResult(emptyList(), emptyList(), emptyList())
        val current = torque.query(EBCM_HEADER, "19C2FF00")
        Thread.sleep(BUS_GAP_MS)
        val all = torque.query(EBCM_HEADER, "19FFFF00")
        val merged = (
            ProtocolParser.parseDtcs(current, "current") +
            ProtocolParser.parseDtcs(all, "all/history")
        ).distinctBy { Pair(it.code, it.status) }.sortedBy { it.code }
        return AbsReadResult(merged, current, all)
    }

    fun clearWhenStationary(): AbsClearResult {
        if (!torque.hasFullPermissions()) {
            return AbsClearResult(false,
                "Torque full plugin permission is required for GM services 19 and 14.",
                null, null, emptyList(), emptyList())
        }

        val speed1 = readVehicleSpeedKph()
            ?: return AbsClearResult(false,
                "Unable to verify vehicle speed. Clear was not attempted.",
                null, null, emptyList(), emptyList())
        Thread.sleep(STATIONARY_VERIFY_MS)
        val speed2 = readVehicleSpeedKph()
            ?: return AbsClearResult(false,
                "Unable to verify vehicle speed a second time. Clear was not attempted.",
                speed1, null, emptyList(), emptyList())

        val observedSpeed = max(speed1, speed2)
        if (observedSpeed > 0.1) {
            return AbsClearResult(false,
                "Vehicle is moving (${String.format("%.1f", observedSpeed)} km/h). Clear is blocked.",
                observedSpeed, null, emptyList(), emptyList())
        }

        Thread.sleep(BUS_GAP_MS)
        val before = readCodes()
        Thread.sleep(BUS_GAP_MS)
        val clearRaw = torque.query(EBCM_HEADER, "14")
        val acknowledged = ProtocolParser.clearAcknowledged(clearRaw)

        if (!acknowledged) {
            val nrc = ProtocolParser.clearNegativeCode(clearRaw)
            val detail = if (nrc == null) {
                "No positive 0x54 response from the EBCM."
            } else {
                "EBCM rejected service 14 with NRC 0x${nrc.toString(16).uppercase().padStart(2, '0')}."
            }
            return AbsClearResult(false, detail, observedSpeed, before, emptyList(), clearRaw)
        }

        Thread.sleep(STATIONARY_VERIFY_MS)
        val afterRaw = torque.query(EBCM_HEADER, "19C2FF00")
        val afterCurrent = ProtocolParser.parseDtcs(afterRaw, "current")
        val message = if (afterCurrent.isEmpty()) {
            "EBCM acknowledged the clear. No current ABS/TCS DTC was returned on the verification read."
        } else {
            "EBCM acknowledged the clear, but current fault code(s) remain or immediately returned."
        }
        return AbsClearResult(true, message, observedSpeed, before, afterCurrent, clearRaw)
    }
}
