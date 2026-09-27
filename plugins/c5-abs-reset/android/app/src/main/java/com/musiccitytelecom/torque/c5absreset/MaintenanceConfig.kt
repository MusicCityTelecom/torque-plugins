package com.musiccitytelecom.torque.c5absreset

/**
 * Source-level behavior switches.
 *
 * Change 0 -> 1 or 1 -> 0, rebuild the APK, and reinstall it.
 *
 * These switches intentionally do not include a continuous active-EBCM
 * safety-fault suppressor. A current C1242 can still be cleared manually
 * with the one-shot EBCM clear while the car is verified stationary.
 */
object MaintenanceConfig {
    // Run one maintenance pass after Torque connects.
    const val AUTO_MAINTENANCE_ON_CONNECT = 1

    // Automatically clear watched history-only DTCs while stationary.
    const val AUTO_CLEAR_HISTORY = 1

    // Optional: one automatic clear attempt per app launch for CURRENT
    // BCM/HVAC/RDCM watched DTCs. EBCM safety DTCs are excluded.
    const val AUTO_CLEAR_CURRENT_NON_SAFETY_ONCE = 0

    // Skip the confirmation dialog on the manual EBCM button.
    // The two-sample stationary interlock still applies.
    const val ONE_TAP_EBCM_CLEAR = 1

    // Keep the automatic pass quiet; results remain available in the module log.
    const val SILENT_AUTOMATIC_PASS = 1
}
