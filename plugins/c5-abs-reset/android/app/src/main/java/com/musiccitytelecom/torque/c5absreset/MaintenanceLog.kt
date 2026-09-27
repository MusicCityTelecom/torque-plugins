package com.musiccitytelecom.torque.c5absreset

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object MaintenanceLog {
    private const val FILE_NAME = "c5-dtc-maintenance.jsonl"
    private const val MAX_LINES = 500

    fun append(context: Context, report: MaintenanceReport) {
        val file = File(context.filesDir, FILE_NAME)
        file.appendText(toJson(report).toString() + "\n")

        val lines = file.readLines()
        if (lines.size > MAX_LINES) {
            file.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
        }
    }

    fun recent(context: Context, count: Int = 20): List<String> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return file.readLines().takeLast(count)
    }

    fun summary(context: Context): String {
        val lines = recent(context, 10)
        if (lines.isEmpty()) return "No automatic maintenance reports saved yet."

        val parsed = lines.mapNotNull {
            try { JSONObject(it) } catch (_: Exception) { null }
        }

        val clears = parsed.sumOf { report ->
            val results = report.optJSONArray("results") ?: JSONArray()
            var count = 0
            for (i in 0 until results.length()) {
                if (results.optJSONObject(i)?.optBoolean("clearAttempted") == true) count++
            }
            count
        }

        val blocked = parsed.sumOf { report ->
            val results = report.optJSONArray("results") ?: JSONArray()
            var count = 0
            for (i in 0 until results.length()) {
                val decision = results.optJSONObject(i)?.optString("decision")
                if (decision == AutoDecision.CURRENT_SAFETY_FAULT_BLOCKED.name) count++
            }
            count
        }

        return "Saved automatic reports: ${parsed.size} recent • history clears: $clears • current safety faults left visible: $blocked"
    }

    private fun toJson(report: MaintenanceReport): JSONObject {
        val root = JSONObject()
            .put("timestampMs", report.timestampMs)
            .put("stationaryVerified", report.stationaryVerified)

        if (report.speedKph == null) {
            root.put("speedKph", JSONObject.NULL)
        } else {
            root.put("speedKph", report.speedKph)
        }

        val results = JSONArray()
        report.results.forEach { result ->
            val obj = JSONObject()
                .put("module", result.before.profile.key)
                .put("moduleName", result.before.profile.name)
                .put("address", result.before.profile.address)
                .put("decision", result.decision.name)
                .put("clearAttempted", result.clearAttempted)
                .put("clearAcknowledged", result.clearAcknowledged)
                .put("current", dtcs(result.before.current))
                .put("all", dtcs(result.before.all))
                .put("watchedCurrent", dtcs(result.before.currentWatched))
                .put("watchedHistoryOnly", dtcs(result.before.historyOnlyWatched))
                .put("rawCurrent", JSONArray(result.before.rawCurrent))
                .put("rawAll", JSONArray(result.before.rawAll))
                .put("rawClear", JSONArray(result.rawClear))

            result.after?.let {
                obj.put("afterCurrent", dtcs(it.current))
                obj.put("afterAll", dtcs(it.all))
            }

            results.put(obj)
        }

        root.put("results", results)
        return root
    }

    private fun dtcs(list: List<AbsDtc>): JSONArray {
        val array = JSONArray()
        list.forEach { dtc ->
            array.put(
                JSONObject()
                    .put("code", dtc.code)
                    .put("scope", dtc.scope)
                    .put("status", dtc.status ?: JSONObject.NULL)
            )
        }
        return array
    }
}
