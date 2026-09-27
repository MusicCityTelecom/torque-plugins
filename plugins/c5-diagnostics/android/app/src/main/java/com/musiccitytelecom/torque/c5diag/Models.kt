package com.musiccitytelecom.torque.c5diag

data class WheelSpeeds(
    val lfKph: Double,
    val rfKph: Double,
    val lrKph: Double,
    val rrKph: Double,
    val vssKph: Double,
    val aux: Int,
    val raw: String,
    val candidate: Boolean = true
)

data class DiagnosticSnapshot(
    val timestampMs: Long,
    val values: Map<String, Double>,
    val misfireCurrent: IntArray,
    val misfireHistory: IntArray,
    val misfireSession: LongArray,
    val wheelSpeeds: WheelSpeeds?,
    val rawWheelResponse: String?,
    val fullTorquePermissions: Boolean
)

data class SessionReport(
    val id: String,
    val startedAtMs: Long,
    val generatedAtMs: Long,
    val vin: String,
    val profile: String,
    val samples: List<DiagnosticSnapshot>
)
