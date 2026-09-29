package com.musiccitytelecom.torque.c5absreset

object MaintenancePolicy {
    fun decide(
        snapshot: ModuleDtcSnapshot,
        stationaryVerified: Boolean,
        autoClearHistory: Boolean = MaintenanceConfig.AUTO_CLEAR_HISTORY == 1,
        autoClearCurrentNonSafety: Boolean =
            MaintenanceConfig.AUTO_CLEAR_CURRENT_NON_SAFETY_ONCE == 1,
        autoClearCurrentC1242WhenStationary: Boolean =
            MaintenanceConfig.AUTO_CLEAR_CURRENT_C1242_WHEN_STATIONARY_ONCE == 1
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
            if (!stationaryVerified) {
                return AutoDecision.NOT_STATIONARY
            }

            return if (
                autoClearCurrentC1242WhenStationary &&
                currentWatched.all { it.code == "C1242" }
            ) {
                AutoDecision.CLEAR_CURRENT_C1242_WHEN_STATIONARY_ONCE
            } else {
                AutoDecision.CURRENT_SAFETY_FAULT_BLOCKED
            }
        }

        if (currentWatched.isNotEmpty()) {
            if (!stationaryVerified) {
                return AutoDecision.NOT_STATIONARY
            }

            return if (autoClearCurrentNonSafety) {
                AutoDecision.CLEAR_CURRENT_NON_SAFETY_ONCE
            } else {
                AutoDecision.ACTIVE_FAULT_LOGGED
            }
        }

        if (!stationaryVerified) {
            return AutoDecision.NOT_STATIONARY
        }

        if (historyOnly.isNotEmpty()) {
            return if (autoClearHistory) {
                AutoDecision.CLEAR_HISTORY_ONCE
            } else {
                AutoDecision.ACTIVE_FAULT_LOGGED
            }
        }

        return AutoDecision.NO_WATCHED_CODE
    }
}
