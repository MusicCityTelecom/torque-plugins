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
    fun blocksCurrentC1242EvenWhenNonSafetyOverrideIsEnabled() {
        val s = snapshot(C5Modules.EBCM, listOf("C1242"), listOf("C1242"))
        assertEquals(
            AutoDecision.CURRENT_SAFETY_FAULT_BLOCKED,
            MaintenancePolicy.decide(
                s,
                stationaryVerified = true,
                autoClearHistory = true,
                autoClearCurrentNonSafety = true
            )
        )
    }

    @Test
    fun clearsHistoryOnlyC1242WhenHistoryAutoClearIsEnabled() {
        val s = snapshot(C5Modules.EBCM, emptyList(), listOf("C1242"))
        assertEquals(
            AutoDecision.CLEAR_HISTORY_ONCE,
            MaintenancePolicy.decide(
                s,
                stationaryVerified = true,
                autoClearHistory = true,
                autoClearCurrentNonSafety = false
            )
        )
    }

    @Test
    fun leavesHistoryOnlyCodeWhenHistoryAutoClearIsDisabled() {
        val s = snapshot(C5Modules.RDCM, emptyList(), listOf("B2283"))
        assertEquals(
            AutoDecision.ACTIVE_FAULT_LOGGED,
            MaintenancePolicy.decide(
                s,
                stationaryVerified = true,
                autoClearHistory = false,
                autoClearCurrentNonSafety = false
            )
        )
    }

    @Test
    fun currentBodyFaultCanBeEnabledForOneShotClear() {
        val s = snapshot(C5Modules.BCM, listOf("B0502"), listOf("B0502"))
        assertEquals(
            AutoDecision.CLEAR_CURRENT_NON_SAFETY_ONCE,
            MaintenancePolicy.decide(
                s,
                stationaryVerified = true,
                autoClearHistory = true,
                autoClearCurrentNonSafety = true
            )
        )
    }

    @Test
    fun currentBodyFaultIsLoggedByDefault() {
        val s = snapshot(C5Modules.BCM, listOf("B0502"), listOf("B0502"))
        assertEquals(
            AutoDecision.ACTIVE_FAULT_LOGGED,
            MaintenancePolicy.decide(
                s,
                stationaryVerified = true,
                autoClearHistory = true,
                autoClearCurrentNonSafety = false
            )
        )
    }

    @Test
    fun noAutomaticClearWhenMoving() {
        val s = snapshot(C5Modules.RDCM, emptyList(), listOf("B2283"))
        assertEquals(
            AutoDecision.NOT_STATIONARY,
            MaintenancePolicy.decide(
                s,
                stationaryVerified = false,
                autoClearHistory = true,
                autoClearCurrentNonSafety = true
            )
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
            MaintenancePolicy.decide(
                s,
                stationaryVerified = true,
                autoClearHistory = true,
                autoClearCurrentNonSafety = true
            )
        )
    }
}
