# C5 DTC Maintenance for Torque Pro

This plugin targets the 2004 Chevrolet Corvette C5 GM Class 2 / SAE J1850 VPW network.

Version 0.2 expands the original EBCM read/clear utility into a multi-module maintenance scanner for:

- `28` TCS/EBCM
- `40` BCM
- `99` HVAC
- `A1` RDCM

## Watched codes

Initial watched-code profile:

- EBCM: `C1242`
- BCM: `B0502`, `B0507`, `B2482`
- HVAC: `B0361`, `B0441` plus observation of `B0341`
- RDCM: `B2283`

The field transcription `B9502` is treated as likely `B0502` because B0502 is the documented C5 RH DRL relay-circuit DTC and is paired with B0507. The transcription `B03441` is ambiguous; B0441 is a documented C5 HVAC actuator-out-of-range DTC, so the plugin watches B0441 and also records B0341 if it is actually returned by the module.

All decoded DTCs and raw Class 2 replies are logged, not only the watched list, so the profile can be corrected from real vehicle responses.

## Automatic maintenance behavior

When Torque connects and full plugin permission is enabled, the plugin performs one automatic maintenance pass:

1. Read vehicle speed twice.
2. Require 0 km/h before any automatic clear operation.
3. Read current and all/history DTCs from each watched module.
4. Save the scan to the app's private JSONL maintenance log.
5. Clear a watched code automatically only when it is present in all/history and absent from the module's current-DTC response.
6. Re-read the module after a clear and log the result.

The plugin deliberately does not continuously erase active faults.

In particular, a current `C1242` is logged and left visible. That code is associated with the EBCM/BPMV pump-motor circuit and can cause the EBCM to disable ABS/TCS/Active Handling for the ignition cycle. The warning lamps are therefore useful fault-state indicators and are not automatically suppressed.

Current BCM/HVAC/RDCM watched faults are also logged instead of being erased in a loop. Once the condition is no longer current, the plugin can clean the history entry automatically on the next stationary pass.

## Manual EBCM clear

The manual EBCM-clear button remains available. It:

- requires full Torque plugin permission,
- verifies 0 km/h twice,
- reads EBCM DTCs,
- sends GM service `14`,
- checks for a positive `54` response,
- re-reads current EBCM DTCs.

If a current fault remains, the module can immediately set the code and dashboard warning again.

## Local report

Automatic passes are appended to:

    c5-dtc-maintenance.jsonl

inside the application's private files directory. The log is capped at 500 entries. Each entry records module, current/all DTCs, watched-code classification, clear decision, clear acknowledgement, post-clear DTCs, and raw responses.

## Torque setup

1. Install Torque Pro and connect to the Corvette normally.
2. Install the C5 DTC Maintenance APK.
3. In Torque plugin settings, enable **Allow full permissions** for this plugin.
4. Open the plugin. The automatic scan/history-cleanup pass starts after the Torque service connects.
5. Use **Scan modules** at any time for a fresh read-only scan.
6. Use **Manual EBCM clear** only when intentionally clearing the EBCM once.

## Build

From `plugins/c5-abs-reset/android`:

    gradle :app:testDebugUnitTest :app:assembleDebug


## Source-level switches

The main behavior switches are in:

    android/app/src/main/java/com/musiccitytelecom/torque/c5absreset/MaintenanceConfig.kt

They use simple `0` / `1` values so a custom build can be changed without tracing the policy code:

    AUTO_MAINTENANCE_ON_CONNECT = 1
    AUTO_CLEAR_HISTORY = 1
    AUTO_CLEAR_CURRENT_NON_SAFETY_ONCE = 0
    ONE_TAP_EBCM_CLEAR = 1
    SILENT_AUTOMATIC_PASS = 1

Setting `AUTO_CLEAR_CURRENT_NON_SAFETY_ONCE = 1` permits one automatic stationary clear attempt per app launch for the watched BCM/HVAC/RDCM current codes. It does not include current EBCM safety DTCs such as C1242.

`ONE_TAP_EBCM_CLEAR = 1` makes the manual EBCM button execute the existing one-shot stationary clear immediately instead of displaying a confirmation dialog.

The stationary interlock remains active for every clear request.
