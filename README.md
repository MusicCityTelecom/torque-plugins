# Torque Plugins

Open-source Torque Pro extensions and vehicle-specific diagnostic packs maintained by MusicCityTelecom.

## Plugins

### C5 Diagnostics

First plugin in this repository: a diagnostic dashboard and report exporter aimed initially at the 2004 Chevrolet Corvette C5 (LS1 / GM Class 2).

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

> **Important:** Enhanced GM PIDs vary by controller and calibration. The project intentionally distinguishes verified, candidate and unsupported PIDs. Wheel-speed PIDs for the 2004 C5 EBCM are not treated as verified until confirmed against the target vehicle/Tech 2 data.

Repository layout:

```
plugins/
  c5-diagnostics/
    android/          Android/Torque plugin
    pids/             Torque CSV PID packs
    server/           optional webhook receiver example
    docs/             protocol and validation notes
```

## Build

Open `plugins/c5-diagnostics/android` in Android Studio or build with Gradle after installing a compatible Android SDK/JDK.

## Safety

The diagnostic code is read-only by design. Discovery tooling is restricted to diagnostic read services and must not issue actuator-control, programming, security-access or write requests.

## License

Apache-2.0 unless otherwise noted.
