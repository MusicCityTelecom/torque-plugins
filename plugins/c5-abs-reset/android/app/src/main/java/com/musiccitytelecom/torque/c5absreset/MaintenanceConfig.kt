package com.musiccitytelecom.torque.c5absreset

/**
 * Source-level behavior switches.
 *
 * Change 0 -> 1 or 1 -> 0, rebuild the APK, and reinstall it.
 *
 * Current EBCM/C1242 clearing can be automated for a single attempt while
 * stationary, but the stationary interlock is intentionally not configurable.
 */
object MaintenanceConfig {
    // Run one maintenance pass after Torque connects.
    const val AUTO_MAINTENANCE_ON_CONNECT = 1

    // Automatically clear watched history-only DTCs while stationary.
    const val AUTO_CLEAR_HISTORY = 1

    // Optional: one automatic clear attempt per app launch for CURRENT
    // BCM/HVAC/RDCM watched DTCs.
    const val AUTO_CLEAR_CURRENT_NON_SAFETY_ONCE = 0

    // Optional: one automatic clear attempt per app launch for a CURRENT
    // watched EBCM code (currently C1242), but only after two stationary
    // samples verify 0 km/h.
    const val AUTO_CLEAR_CURRENT_C1242_WHEN_STATIONARY_ONCE = 0

    // Skip the confirmation dialog on the manual EBCM button.
    // The two-sample stationary interlock still applies.
    const val ONE_TAP_EBCM_CLEAR = 1

    // Keep the automatic pass quiet; results remain available in the module log.
    const val SILENT_AUTOMATIC_PASS = 1
}
