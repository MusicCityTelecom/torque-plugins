package com.musiccitytelecom.torque.c5absreset

data class AbsDtc(
    val code: String,
    val status: Int?,
    val scope: String
)

data class ModuleProfile(
    val key: String,
    val name: String,
    val address: String,
    val watchedCodes: Set<String>,
    val safetyCritical: Boolean
) {
    val header: String
        get() = "6C" + address + "F1"
}

object C5Modules {
    val EBCM = ModuleProfile(
        key = "EBCM",
        name = "28 TCS/EBCM",
        address = "28",
        watchedCodes = setOf("C1242"),
        safetyCritical = true
    )

    val BCM = ModuleProfile(
        key = "BCM",
        name = "40 BCM",
        address = "40",
        watchedCodes = setOf("B0502", "B0507", "B2482"),
        safetyCritical = false
    )

    val HVAC = ModuleProfile(
        key = "HVAC",
        name = "99 HVAC",
        address = "99",
        watchedCodes = setOf("B0361", "B0441", "B0341"),
        safetyCritical = false
    )

    val RDCM = ModuleProfile(
        key = "RDCM",
        name = "A1 RDCM",
        address = "A1",
        watchedCodes = setOf("B2283"),
        safetyCritical = false
    )

    val ALL = listOf(EBCM, BCM, HVAC, RDCM)
}

data class ModuleDtcSnapshot(
    val profile: ModuleProfile,
    val current: List<AbsDtc>,
    val all: List<AbsDtc>,
    val rawCurrent: List<String>,
    val rawAll: List<String>
) {
    val currentReadResponded: Boolean
        get() = rawCurrent.isNotEmpty()

    val allReadResponded: Boolean
        get() = rawAll.isNotEmpty()

    val currentWatched: List<AbsDtc>
        get() = current.filter { it.code in profile.watchedCodes }

    val historyOnlyWatched: List<AbsDtc>
        get() {
            val active = current.map { it.code }.toSet()
            return all.filter {
                it.code in profile.watchedCodes && it.code !in active
            }
        }
}

enum class AutoDecision {
    NO_WATCHED_CODE,
    NOT_STATIONARY,
    CURRENT_READ_UNVERIFIED,
    CURRENT_SAFETY_FAULT_BLOCKED,
    ACTIVE_FAULT_LOGGED,
    CLEAR_HISTORY_ONCE,
    CLEAR_CURRENT_NON_SAFETY_ONCE
}

data class ModuleMaintenanceResult(
    val before: ModuleDtcSnapshot,
    val decision: AutoDecision,
    val clearAttempted: Boolean,
    val clearAcknowledged: Boolean,
    val rawClear: List<String>,
    val after: ModuleDtcSnapshot?
)

data class MaintenanceReport(
    val timestampMs: Long,
    val speedKph: Double?,
    val stationaryVerified: Boolean,
    val results: List<ModuleMaintenanceResult>
)

data class AbsClearResult(
    val acknowledged: Boolean,
    val message: String,
    val speedKph: Double?,
    val before: ModuleDtcSnapshot?,
    val afterCurrent: List<AbsDtc>,
    val rawClear: List<String>
)
