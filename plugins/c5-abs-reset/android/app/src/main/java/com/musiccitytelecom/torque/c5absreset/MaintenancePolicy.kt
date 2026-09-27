package com.musiccitytelecom.torque.c5absreset

object MaintenancePolicy {
    fun decide(
        snapshot: ModuleDtcSnapshot,
        stationaryVerified: Boolean
    ): AutoDecision {
        val currentWatched = snapshot.currentWatched
        val historyOnly = snapshot.historyOnlyWatched

        if (currentWatched.isEmpty() && historyOnly.isEmpty()) {
            return AutoDecision.NO_WATCHED_CODE
        }

        if (!snapshot.currentReadResponded) {
            return AutoDecision.CURRENT_READ_UNVERIFIED
        }

        if (snapshot.profile.safetyCritical && currentWatched.isNotEmpty()) {
            return AutoDecision.CURRENT_SAFETY_FAULT_BLOCKED
        }

        if (currentWatched.isNotEmpty()) {
            return AutoDecision.ACTIVE_FAULT_LOGGED
        }

        if (!stationaryVerified) {
            return AutoDecision.NOT_STATIONARY
        }

        if (historyOnly.isNotEmpty()) {
            return AutoDecision.CLEAR_HISTORY_ONCE
        }

        return AutoDecision.NO_WATCHED_CODE
    }
}
