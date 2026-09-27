package com.musiccitytelecom.torque.c5absreset

import org.junit.Assert.assertEquals
import org.junit.Test

class MaintenancePolicyTest {
    private fun snapshot(
        profile: ModuleProfile,
        current: List<String>,
        all: List<String>,
        currentResponded: Boolean = true
    ): ModuleDtcSnapshot {
        return ModuleDtcSnapshot(
            profile = profile,
            current = current.map { AbsDtc(it, 0x10, "current") },
            all = all.map { AbsDtc(it, 0x00, "all/history") },
            rawCurrent = if (currentResponded) listOf("59") else emptyList(),
            rawAll = listOf("59")
        )
    }

    @Test
    fun blocksCurrentC1242FromAutomaticClear() {
        val s = snapshot(C5Modules.EBCM, listOf("C1242"), listOf("C1242"))
        assertEquals(
            AutoDecision.CURRENT_SAFETY_FAULT_BLOCKED,
            MaintenancePolicy.decide(s, stationaryVerified = true)
        )
    }

    @Test
    fun stillClassifiesCurrentC1242WhenStationaryCheckFails() {
        val s = snapshot(C5Modules.EBCM, listOf("C1242"), listOf("C1242"))
        assertEquals(
            AutoDecision.CURRENT_SAFETY_FAULT_BLOCKED,
            MaintenancePolicy.decide(s, stationaryVerified = false)
        )
    }

    @Test
    fun clearsHistoryOnlyC1242OnceWhenStationary() {
        val s = snapshot(C5Modules.EBCM, emptyList(), listOf("C1242"))
        assertEquals(
            AutoDecision.CLEAR_HISTORY_ONCE,
            MaintenancePolicy.decide(s, stationaryVerified = true)
        )
    }

    @Test
    fun logsCurrentBodyFaultInsteadOfLoopClearing() {
        val s = snapshot(C5Modules.BCM, listOf("B0502"), listOf("B0502"))
        assertEquals(
            AutoDecision.ACTIVE_FAULT_LOGGED,
            MaintenancePolicy.decide(s, stationaryVerified = true)
        )
    }

    @Test
    fun clearsHistoryOnlyBodyFaultOnce() {
        val s = snapshot(C5Modules.BCM, emptyList(), listOf("B0502"))
        assertEquals(
            AutoDecision.CLEAR_HISTORY_ONCE,
            MaintenancePolicy.decide(s, stationaryVerified = true)
        )
    }

    @Test
    fun failsClosedWhenCurrentReadDidNotRespond() {
        val s = snapshot(
            C5Modules.BCM,
            current = emptyList(),
            all = listOf("B0502"),
            currentResponded = false
        )
        assertEquals(
            AutoDecision.CURRENT_READ_UNVERIFIED,
            MaintenancePolicy.decide(s, stationaryVerified = true)
        )
    }
}
