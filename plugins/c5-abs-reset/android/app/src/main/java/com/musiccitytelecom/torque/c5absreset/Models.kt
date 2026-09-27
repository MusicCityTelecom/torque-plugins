package com.musiccitytelecom.torque.c5absreset

data class AbsDtc(val code: String, val status: Int?, val scope: String)
data class AbsReadResult(
    val dtcs: List<AbsDtc>,
    val rawCurrent: List<String>,
    val rawAll: List<String>
)
data class AbsClearResult(
    val acknowledged: Boolean,
    val message: String,
    val speedKph: Double?,
    val before: AbsReadResult?,
    val afterCurrent: List<AbsDtc>,
    val rawClear: List<String>
)
