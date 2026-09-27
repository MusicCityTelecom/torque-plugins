# C5 Diagnostics for Torque Pro

C5 Diagnostics is the first plugin in the torque-plugins repository. It is initially targeted at the 2004 Chevrolet Corvette C5 using GM Class 2 / J1850 VPW diagnostics. The first validation vehicle is an early 2004 build; its VIN is intentionally not published in this public repository.

## Current feature set

- Eight-cylinder current misfire counters
- Eight-cylinder history misfire counters
- Session misfire deltas and totals
- Four-wheel speed dashboard using a single GM EBCM packet when supported
- Raw EBCM response capture so unverified mappings can be field-validated
- RPM, vehicle speed, engine load, coolant temperature, intake temperature, MAP, MAF, throttle, ignition timing, fuel trims, knock retard, fuel level and module voltage
- C5-oriented engine oil pressure and automatic-transmission fluid temperature channels when supported by the PCM
- JSON and CSV reports
- Android share-sheet export
- Optional signed HTTPS webhook upload
- Configurable VIN and P59 cylinder 1/2 current-counter mapping
- Read-only diagnostic request scheduler designed to avoid flooding the Class 2 bus

## Wheel-speed status

The plugin contains a candidate GM Class 2 EBCM query:

- Header: 6C28F1
- Request: 2A0120
- Expected positive response prefix: 6A20
- Candidate payload order: LF, RF, LR, RR, VSS, auxiliary byte

This pattern is documented in field captures from another GM Class 2 vehicle, but it is not claimed as verified for the 2004 C5 yet. The plugin therefore labels wheel data as candidate until it has been checked on the target Corvette against known vehicle speed and, ideally, a Tech 2 or equivalent scan tool.

Torque's published remote API restricts diagnostic modes outside its normal allow-list. Mode 2A therefore requires enabling Torque's "Allow full permissions" option for this plugin. Standard Mode 01 and GM Mode 22 data can still operate without that additional permission.

The application deliberately does not guess individual EBCM PIDs when a mapping has not been verified.

## Build

Open plugins/c5-diagnostics/android in Android Studio, or from that directory run:

    gradle :app:assembleDebug

The CI workflow installs the Android SDK and builds the debug APK on pushes and pull requests.

## Install / test

1. Install Torque Pro and connect Torque to the vehicle normally.
2. Build and install the C5 Diagnostics APK.
3. In Torque's plugin settings, enable full permissions for C5 Diagnostics if you want the candidate EBCM wheel-speed packet.
4. Open Torque Pro and launch C5 Diagnostics from the plugin/activity list, or launch the C5 Diagnostics app icon.
5. Tap Start.
6. First validate engine RPM and vehicle speed against Torque's normal gauges.
7. For the wheel packet, perform a slow, straight-line test in a safe location. All four wheel values should track each other and track vehicle speed.
8. If wheel values do not validate, save/export a report. The report includes the raw EBCM response for analysis.
9. Do not use diagnostic data while driving in a way that distracts from vehicle operation.

## Misfire mapping

GM community PID tables disagree about the current counter assignment for cylinders 1 and 2 on some P59 applications. The plugin has a setting named "Use P59 C1/C2 corrected mapping".

With that setting enabled (default for the initial Corvette profile):

- Cylinder 1 current = 221206
- Cylinder 2 current = 221205

With it disabled, the common generic GM table order is used:

- Cylinder 1 current = 221205
- Cylinder 2 current = 221206

History counters are not swapped.

## Reports and off-site upload

Reports can be exported locally as JSON and CSV. A report may also be POSTed to an HTTPS webhook.

The client sends:

- Content-Type: application/json
- X-Torque-Timestamp: Unix timestamp
- X-Torque-Signature: sha256=<HMAC-SHA256(timestamp + "." + raw_body)>

The shared secret is entered in plugin settings and is never committed to this repository. The optional PHP receiver for server2 is in server/.

Reports contain the VIN only if the user explicitly enters one in plugin settings.

## Scope

Version 0.1 is intentionally read-only. It does not perform module programming, actuator commands, security access, DTC clearing, relearns, or configuration writes.


## 0.1.1 field-test fix

The first in-car test showed Torque returning valid Class 2 data followed by an adapter status trailer on the same response string, for example:

    6A200000000000<DATA ERROR

The parser now preserves the valid hex frame before the '<' delimiter and ignores the status trailer. Misfire counters also remain '--' until a real PID response has been parsed, rather than displaying a misleading default zero.
