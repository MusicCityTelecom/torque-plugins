# C5 ABS/TCS Reset for Torque Pro

This plugin targets the 2004 Chevrolet Corvette C5 GM Class 2 / SAE J1850 VPW network. It reads the ABS/EBCM diagnostic memory and can perform a single user-confirmed EBCM clear while the vehicle is stationary.

The dashboard warnings it addresses are the ABS lamp, traction/active-handling lamp, and DIC messages such as `SERVICE ABS` and `SERVICE TRACTION SYS`.

## Features

- EBCM address `0x28` over the same Class 2 network already validated by the C5 Diagnostics plugin.
- GM service `19` current and all/history DTC reads.
- GM service `14` clear after the DTC read.
- Two valid 0 km/h speed samples before any clear request.
- Post-clear current-DTC verification.
- Raw Torque/Class 2 responses for field validation.

A successful clear can switch the warning lamps off only if the underlying fault is no longer present. If the EBCM still detects a wheel-speed, power/ground, wiring, steering/yaw, module, or other fault, the warning can return immediately.

This plugin deliberately does **not** continuously or automatically erase a returning ABS/TCS fault.

## Torque setup

1. Install Torque Pro and connect to the Corvette.
2. Install this APK.
3. In Torque plugin settings, enable **Allow full permissions** for **C5 ABS/TCS Reset**.
4. Open the plugin and use **Read ABS codes**.
5. With the car stopped, use **Clear ABS/TCS** for one read-then-clear cycle.

## Protocol

- PCM header: `6C10F1`
- EBCM header: `6C28F1`
- Current DTCs: `19C2FF00`
- All/history DTCs: `19FFFF00`
- Clear diagnostic information: `14`
- Positive clear response service: `54`

Build from `plugins/c5-abs-reset/android` with:

    gradle :app:testDebugUnitTest :app:assembleDebug
