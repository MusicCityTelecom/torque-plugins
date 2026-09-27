# Torque Plugins

Open-source Torque Pro extensions and vehicle-specific diagnostic packs maintained by MusicCityTelecom.

## Plugins

### C5 Diagnostics

Diagnostic dashboard and report exporter aimed initially at the 2004 Chevrolet Corvette C5 (LS1 / GM Class 2).

Current scope:
- 8-cylinder current/history misfire monitoring
- Misfire deltas, session totals, cylinder ranking and event capture
- Standard OBD-II supporting data: RPM, load, coolant, trims, MAP, MAF, throttle, spark, speed and voltage
- GM-enhanced supporting data where validated
- Four-wheel speed display architecture with an EBCM validation/discovery path
- CSV/JSON report export
- Optional signed HTTPS webhook upload for off-site storage
- VIN-aware vehicle profiles
- Torque Pro PID-provider integration

### C5 ABS/TCS Reset

A separate C5 plugin for EBCM diagnostics and one-shot ABS/traction-control DTC clearing.

Current scope:
- GM Class 2 EBCM service-19 DTC reads
- EBCM-targeted service-14 clear
- Two-sample 0 km/h interlock before a clear
- Post-clear current-DTC verification
- Raw Class 2 response display for field validation
- Explicit user confirmation before every clear

The plugin deliberately does not continuously erase a returning ABS/TCS fault. Clearing diagnostic memory can turn the warnings off only when the underlying fault is no longer active.

Repository layout:

```
plugins/
  c5-diagnostics/
    android/          Android/Torque diagnostic plugin
    pids/             Torque CSV PID packs
    server/           optional webhook receiver example
    docs/             protocol and validation notes
  c5-abs-reset/
    android/          Android/Torque EBCM read/clear plugin
    docs/             Class 2 clear sequence and safety notes
```

## Build

Each plugin is an independent Android project. Open its `android` directory in Android Studio or run Gradle from that directory after installing a compatible Android SDK/JDK.

## Safety

`c5-diagnostics` remains read-only. `c5-abs-reset` performs a narrowly scoped EBCM diagnostic-memory clear only after a DTC read and a two-sample stationary check, and only after explicit user confirmation. A current ABS/TCS fault may immediately set the code and warning again.

## License

Apache-2.0 unless otherwise noted.
