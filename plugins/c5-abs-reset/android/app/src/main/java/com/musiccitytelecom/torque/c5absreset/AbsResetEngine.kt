package com.musiccitytelecom.torque.c5absreset

import kotlin.math.max

class AbsResetEngine(private val torque: TorqueClient) {
    companion object {
        private const val PCM_HEADER = "6C10F1"
        private const val BUS_GAP_MS = 300L
        private const val STATIONARY_VERIFY_MS = 750L
    }

    data class StationaryCheck(
        val verified: Boolean,
        val speedKph: Double?
    )

    fun readVehicleSpeedKph(): Double? =
        ProtocolParser.parseVehicleSpeedKph(torque.query(PCM_HEADER, "010D"))

    fun verifyStationary(): StationaryCheck {
        val speed1 = readVehicleSpeedKph() ?: return StationaryCheck(false, null)
        Thread.sleep(STATIONARY_VERIFY_MS)
        val speed2 = readVehicleSpeedKph() ?: return StationaryCheck(false, speed1)
        val observed = max(speed1, speed2)
        return StationaryCheck(observed <= 0.1, observed)
    }

    fun readCodes(profile: ModuleProfile): ModuleDtcSnapshot {
        if (!torque.hasFullPermissions()) {
            return ModuleDtcSnapshot(
                profile = profile,
                current = emptyList(),
                all = emptyList(),
                rawCurrent = emptyList(),
                rawAll = emptyList()
            )
        }

        val currentRaw = torque.query(profile.header, "19C2FF00")
        Thread.sleep(BUS_GAP_MS)
        val allRaw = torque.query(profile.header, "19FFFF00")

        return ModuleDtcSnapshot(
            profile = profile,
            current = ProtocolParser.parseDtcs(currentRaw, "current"),
            all = ProtocolParser.parseDtcs(allRaw, "all/history"),
            rawCurrent = currentRaw,
            rawAll = allRaw
        )
    }

    fun runAutomaticMaintenance(): MaintenanceReport {
        if (!torque.hasFullPermissions()) {
            return MaintenanceReport(
                timestampMs = System.currentTimeMillis(),
                speedKph = null,
                stationaryVerified = false,
                results = C5Modules.ALL.map { profile ->
                    val snap = readCodes(profile)
                    ModuleMaintenanceResult(
                        before = snap,
                        decision = AutoDecision.CURRENT_READ_UNVERIFIED,
                        clearAttempted = false,
                        clearAcknowledged = false,
                        rawClear = emptyList(),
                        after = null
                    )
                }
            )
        }

        val stationary = verifyStationary()
        val results = mutableListOf<ModuleMaintenanceResult>()

        for (profile in C5Modules.ALL) {
            Thread.sleep(BUS_GAP_MS)
            val before = readCodes(profile)
            val decision = MaintenancePolicy.decide(before, stationary.verified)

            if (decision == AutoDecision.CLEAR_HISTORY_ONCE) {
                Thread.sleep(BUS_GAP_MS)
                val clearRaw = torque.query(profile.header, "14")
                val ack = ProtocolParser.clearAcknowledged(clearRaw)

                Thread.sleep(STATIONARY_VERIFY_MS)
                val after = readCodes(profile)

                results += ModuleMaintenanceResult(
                    before = before,
                    decision = decision,
                    clearAttempted = true,
                    clearAcknowledged = ack,
                    rawClear = clearRaw,
                    after = after
                )
            } else {
                results += ModuleMaintenanceResult(
                    before = before,
                    decision = decision,
                    clearAttempted = false,
                    clearAcknowledged = false,
                    rawClear = emptyList(),
                    after = null
                )
            }
        }

        return MaintenanceReport(
            timestampMs = System.currentTimeMillis(),
            speedKph = stationary.speedKph,
            stationaryVerified = stationary.verified,
            results = results
        )
    }

    fun clearEbcmManuallyWhenStationary(): AbsClearResult {
        if (!torque.hasFullPermissions()) {
            return AbsClearResult(
                false,
                "Torque full plugin permission is required for GM services 19 and 14.",
                null,
                null,
                emptyList(),
                emptyList()
            )
        }

        val stationary = verifyStationary()
        if (!stationary.verified) {
            val detail = if (stationary.speedKph == null) {
                "Unable to verify vehicle speed. Clear was not attempted."
            } else {
                "Vehicle is moving (${String.format("%.1f", stationary.speedKph)} km/h). Clear is blocked."
            }
            return AbsClearResult(
                false,
                detail,
                stationary.speedKph,
                null,
                emptyList(),
                emptyList()
            )
        }

        Thread.sleep(BUS_GAP_MS)
        val before = readCodes(C5Modules.EBCM)
        Thread.sleep(BUS_GAP_MS)
        val clearRaw = torque.query(C5Modules.EBCM.header, "14")
        val acknowledged = ProtocolParser.clearAcknowledged(clearRaw)

        if (!acknowledged) {
            val nrc = ProtocolParser.clearNegativeCode(clearRaw)
            val detail = if (nrc == null) {
                "No positive 0x54 response from the EBCM."
            } else {
                "EBCM rejected service 14 with NRC 0x${nrc.toString(16).uppercase().padStart(2, '0')}."
            }
            return AbsClearResult(
                false,
                detail,
                stationary.speedKph,
                before,
                emptyList(),
                clearRaw
            )
        }

        Thread.sleep(STATIONARY_VERIFY_MS)
        val after = readCodes(C5Modules.EBCM)
        val message = if (after.current.isEmpty()) {
            "EBCM acknowledged the clear. No current ABS/TCS DTC was returned."
        } else {
            "EBCM acknowledged the clear, but a current EBCM fault remains or immediately returned."
        }

        return AbsClearResult(
            true,
            message,
            stationary.speedKph,
            before,
            after.current,
            clearRaw
        )
    }
}
