# C5 ABS/TCS reset protocol notes

The target 2004 Corvette C5 uses GM Class 2 diagnostics over SAE J1850 VPW. The existing C5 Diagnostics plugin has already received a valid EBCM response from address `0x28`.

The clear sequence is intentionally narrow:

    header 6C28F1
    19C2FF00    current EBCM DTCs
    19FFFF00    all/history EBCM DTCs
    14          clear EBCM diagnostic information

A successful service-14 request is expected to produce positive service `54`.

Before service 14, the plugin requests standard vehicle speed (`010D`) from the PCM twice, 750 ms apart. Both reads must succeed and report 0 km/h.

Torque requires full plugin permission for diagnostic modes outside its normal unrestricted list, so this plugin checks that permission before service 19 or service 14.

References:

- https://torque-bhp.com/api/ITorqueService.aidl
- https://wiki.torque-bhp.com/view/PluginDocumentation
- https://gmtnation.com/forums/threads/how-to-read-abs-tccm-pcm-codes-using-a-terminal-obdii-interface.4947/
