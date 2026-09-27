# C5 DTC Maintenance protocol notes

## Module addresses

The C5 on-board diagnostic display identifies the modules by their Class 2 node IDs. This plugin currently targets:

| Module | Node | Header |
|---|---:|---|
| TCS/EBCM | 28 | 6C28F1 |
| BCM | 40 | 6C40F1 |
| HVAC | 99 | 6C99F1 |
| RDCM | A1 | 6CA1F1 |

The PCM remains at node `10`, using header `6C10F1`.

## DTC reads

Each module is queried with:

    19C2FF00    current DTCs
    19FFFF00    all/history DTCs

Responses are decoded from positive service `59`.

The parser accepts compact or space-separated Torque responses and ignores an adapter status trailer after a `<` delimiter.

## Clear behavior

The module-specific diagnostic-memory clear request is:

    14

A positive response is expected to contain service `54`.

Automatic clearing is intentionally constrained. A watched code must:

- appear in the all/history read,
- not appear in the current-DTC read,
- have a valid current-DTC response from the module,
- be processed while two vehicle-speed samples both verify 0 km/h.

Only one clear is attempted for that module during the automatic pass.

## Current safety DTC handling

`C1242` is treated as safety-critical. If returned as a current EBCM DTC, automatic clearing is blocked and the condition is logged. The manual one-shot EBCM-clear function remains available with explicit user confirmation and stationary verification.

## Watched profile

- EBCM: C1242
- BCM: B0502, B0507, B2482
- HVAC: B0361, B0441; B0341 retained as an observation alias pending an exact in-car capture
- RDCM: B2283

The raw scan is always retained so an ambiguous transcription can be corrected from the actual module response.

## References

- Torque remote API: https://torque-bhp.com/api/ITorqueService.aidl
- Torque plugin documentation: https://wiki.torque-bhp.com/view/PluginDocumentation
- C5 module/DTC reference: https://www.corvetteactioncenter.com/tech/knowledgebase/article/36/2004-corvette-diagnostic-trouble-codes-225.html
- GM VPW service-19/service-14 procedure discussion: https://gmtnation.com/forums/threads/how-to-read-abs-tccm-pcm-codes-using-a-terminal-obdii-interface.4947/
