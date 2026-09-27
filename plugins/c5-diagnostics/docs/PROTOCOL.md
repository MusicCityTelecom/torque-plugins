# Protocol notes

## Vehicle network

The 2004 C5 uses GM Class 2 serial diagnostics over SAE J1850 VPW. The initial profile uses the common Class 2 physical addressing convention:

- 10: PCM / powertrain controller
- 28: ABS / EBCM
- F1: diagnostic tester

Powertrain requests therefore use 6C10F1. Candidate EBCM requests use 6C28F1.

## Torque remote API

The Android application binds to Torque Pro's remote service and uses sendCommandGetResponse(header, command) for read-only diagnostic requests. The local AIDL definition intentionally includes only the methods up through sendCommandGetResponse in the same order as Torque's published API, so Binder transaction numbering remains compatible.

## Misfire service 22 requests

The following current/history mappings are used by the initial profile:

| Cylinder | Current (P59 corrected default) | History |
|---|---|---|
| 1 | 221206 | 221201 |
| 2 | 221205 | 221202 |
| 3 | 221207 | 221203 |
| 4 | 221208 | 221204 |
| 5 | 2211EA | 2211F8 |
| 6 | 2211EB | 2211F9 |
| 7 | 2211EC | 2211FA |
| 8 | 2211ED | 2211FB |

The application setting can swap the current C1/C2 assignments back to the common generic GM table order.

Mode 22 positive replies are parsed by locating 62 <pid-high> <pid-low> in the returned byte stream. This works whether Torque includes the Class 2 header in the response string or strips it.

## Candidate wheel-speed packet

Request:

    header  6C28F1
    command 2A0120

Expected packet signature:

    ... 6A 20 LF RF LR RR VSS AUX ...

The parser deliberately searches for 6A20 and consumes exactly the next six bytes. This handles the observed GM Class 2 response layout where the 01 selector from the request is not echoed because of the J1850 payload-size limit.

For v0.1, each speed byte is interpreted as km/h with scale 1.0. The UI labels this channel Candidate until the C5 is field-validated.

## Standard Mode 01 channels

The plugin uses ordinary OBD-II Mode 01 data where possible:

- 010C RPM
- 010D vehicle speed
- 0104 calculated engine load
- 0105 coolant temperature
- 0106 / 0107 bank 1 short/long fuel trim
- 0108 / 0109 bank 2 short/long fuel trim
- 0110 MAF
- 0111 throttle position
- 010E ignition timing
- 0142 control-module voltage

GM knock retard is queried separately with 2211A6.

## Scheduler

Only one request is in flight at a time. The scheduler caps request initiation to roughly one request per 90 ms and assigns faster periods to current misfire and combined wheel data. Lower-priority history/support channels are allowed to slip rather than generate concurrent traffic.

This is intentional for the relatively low-bandwidth Class 2 bus.


## Additional C5-oriented channels

Two useful GM enhanced channels are included because they have been field-tested on C5 applications:

- Transmission fluid temperature: 221940, A - 40 degrees C.
- Engine oil pressure: request 22115C01. After the 62 11 5C 01 response prefix, the next byte is converted to kPa with raw * 4.326 - 110.313, then to psi.

The transmission channel naturally remains empty on vehicles/controllers that do not support it. The oil-temperature PID commonly circulated for GM vehicles is intentionally not promoted as verified here because C5 reports show inconsistent correlation.

## Reference material

- Torque plugin documentation: https://wiki.torque-bhp.com/view/PluginDocumentation
- Torque forum plugin binding example: https://torque-bhp.com/community/main-forum/new-plugin-support3rd-party-add-ons-pids-etc/
- Corvette community PID discussion: https://www.corvetteforum.com/forums/autocrossing-and-roadracing/3486449-scan-tools-and-smart-phones-discussion.html
- C5 oil pressure / trans temperature field notes: https://racechrono.com/forum/d/1942-1942
