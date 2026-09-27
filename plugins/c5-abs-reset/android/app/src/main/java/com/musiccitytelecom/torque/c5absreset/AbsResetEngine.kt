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
        val speedKph: Double?,
        val source: String
    )

    private data class MotionSample(
        val stationary: Boolean,
        val speedKph: Double,
        val source: String
    )

    fun readVehicleSpeedKph(): Double? =
        ProtocolParser.parseVehicleSpeedKph(torque.query(PCM_HEADER, "010D"))

    private fun readMotionSample(): MotionSample? {
        val pcmSpeed = readVehicleSpeedKph()
        if (pcmSpeed != null) {
            return MotionSample(
                stationary = pcmSpeed <= 0.1,
                speedKph = pcmSpeed,
                source = "PCM PID 010D"
            )
        }

        Thread.sleep(BUS_GAP_MS)
        val wheelRaw = torque.query(C5Modules.EBCM.header, "2A0120")
        val wheels = ProtocolParser.parseWheelSpeedsKph(wheelRaw) ?: return null
        val observed = wheels.maxOrNull() ?: return null

        return MotionSample(
            stationary = wheels.all { it <= 0.1 },
            speedKph = observed,
            source = "EBCM wheel speeds"
        )
    }

    fun verifyStationary(): StationaryCheck {
        val sample1 = readMotionSample()
            ?: return StationaryCheck(false, null, "no usable PCM or EBCM speed response")

        Thread.sleep(STATIONARY_VERIFY_MS)

        val sample2 = readMotionSample()
            ?: return StationaryCheck(false, sample1.speedKph, sample1.source)

        val observed = max(sample1.speedKph, sample2.speedKph)
        val verified = sample1.stationary && sample2.stationary
        val source = if (sample1.source == sample2.source) {
            sample1.source
        } else {
            sample1.source + " + " + sample2.source
        }

        return StationaryCheck(verified, observed, source)
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

            val shouldClear = decision == AutoDecision.CLEAR_HISTORY_ONCE ||
                decision == AutoDecision.CLEAR_CURRENT_NON_SAFETY_ONCE

            if (shouldClear) {
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
                "Unable to verify vehicle speed from PCM or EBCM wheel data. Clear was not attempted."
            } else {
                "Vehicle movement detected (${String.format("%.1f", stationary.speedKph)} km/h via ${stationary.source}). Clear is blocked."
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
            "EBCM acknowledged the clear at 0 km/h via ${stationary.source}. No current ABS/TCS DTC was returned."
        } else {
            "EBCM acknowledged the clear at 0 km/h via ${stationary.source}, but a current EBCM fault remains or immediately returned."
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
