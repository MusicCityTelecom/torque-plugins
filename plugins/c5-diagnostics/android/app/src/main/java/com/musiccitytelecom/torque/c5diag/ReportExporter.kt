package com.musiccitytelecom.torque.c5diag

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection

data class ReportFiles(val json: File, val csv: File)
data class UploadResult(val ok: Boolean, val statusCode: Int, val message: String)

object ReportExporter {
    private val valueColumns = listOf(
        "rpm", "speed_kph", "load_pct", "coolant_c",
        "stft_b1_pct", "ltft_b1_pct", "stft_b2_pct", "ltft_b2_pct",
        "timing_deg", "maf_gps", "throttle_pct", "module_v", "knock_retard_deg"
    )

    fun toJson(report: SessionReport): String {
        val root = JSONObject()
        root.put("format", "torque-c5-diagnostic-report")
        root.put("format_version", 1)

        root.put("vehicle", JSONObject().apply {
            put("vin", report.vin)
            put("profile", report.profile)
        })

        root.put("session", JSONObject().apply {
            put("id", report.id)
            put("started_at", Instant.ofEpochMilli(report.startedAtMs).toString())
            put("generated_at", Instant.ofEpochMilli(report.generatedAtMs).toString())
            put("sample_count", report.samples.size)
        })

        report.samples.lastOrNull()?.let { latest ->
            root.put("summary", snapshotJson(latest))
        }

        val samples = JSONArray()
        report.samples.forEach { samples.put(snapshotJson(it)) }
        root.put("samples", samples)
        return root.toString(2)
    }

    private fun snapshotJson(s: DiagnosticSnapshot): JSONObject {
        val obj = JSONObject()
        obj.put("timestamp", Instant.ofEpochMilli(s.timestampMs).toString())

        val values = JSONObject()
        s.values.forEach { (key, value) -> values.put(key, value) }
        obj.put("values", values)

        obj.put("misfire_current", intArrayJson(s.misfireCurrent))
        obj.put("misfire_history", intArrayJson(s.misfireHistory))
        obj.put("misfire_session", longArrayJson(s.misfireSession))
        obj.put("torque_full_permissions", s.fullTorquePermissions)

        s.wheelSpeeds?.let { w ->
            obj.put("wheels", JSONObject().apply {
                put("lf_kph", w.lfKph)
                put("rf_kph", w.rfKph)
                put("lr_kph", w.lrKph)
                put("rr_kph", w.rrKph)
                put("vss_kph", w.vssKph)
                put("aux", w.aux)
                put("mapping_status", if (w.candidate) "candidate" else "verified")
            })
        }
        if (!s.rawWheelResponse.isNullOrBlank()) {
            obj.put("raw_wheel_response", s.rawWheelResponse)
        }
        return obj
    }

    fun write(context: Context, report: SessionReport): ReportFiles {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "reports")
        if (!base.exists()) base.mkdirs()

        val stem = "c5diag-" + report.id
        val jsonFile = File(base, stem + ".diagnostic.json")
        val csvFile = File(base, stem + ".diagnostic.csv")

        jsonFile.writeText(toJson(report), Charsets.UTF_8)
        csvFile.writeText(toCsv(report), Charsets.UTF_8)
        return ReportFiles(jsonFile, csvFile)
    }

    fun toCsv(report: SessionReport): String {
        val header = mutableListOf(
            "timestamp",
            "wheel_lf_kph", "wheel_rf_kph", "wheel_lr_kph", "wheel_rr_kph", "wheel_vss_kph"
        )
        header.addAll(valueColumns)
        for (i in 1..8) header.add("misfire_c" + i + "_current")
        for (i in 1..8) header.add("misfire_c" + i + "_history")
        for (i in 1..8) header.add("misfire_c" + i + "_session")

        val out = StringBuilder()
        out.append(header.joinToString(",")).append('\n')

        report.samples.forEach { s ->
            val row = mutableListOf<String>()
            row.add(csv(Instant.ofEpochMilli(s.timestampMs).toString()))
            val w = s.wheelSpeeds
            row.add(w?.lfKph?.toString() ?: "")
            row.add(w?.rfKph?.toString() ?: "")
            row.add(w?.lrKph?.toString() ?: "")
            row.add(w?.rrKph?.toString() ?: "")
            row.add(w?.vssKph?.toString() ?: "")
            valueColumns.forEach { key -> row.add(s.values[key]?.toString() ?: "") }
            s.misfireCurrent.forEach { row.add(it.toString()) }
            s.misfireHistory.forEach { row.add(it.toString()) }
            s.misfireSession.forEach { row.add(it.toString()) }
            out.append(row.joinToString(",")).append('\n')
        }
        return out.toString()
    }

    private fun csv(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    private fun intArrayJson(values: IntArray): JSONArray =
        JSONArray().apply { values.forEach { put(it) } }

    private fun longArrayJson(values: LongArray): JSONArray =
        JSONArray().apply { values.forEach { put(it) } }
}

object WebhookExporter {
    fun upload(url: String, secret: String, json: String): UploadResult {
        if (!url.startsWith("https://", ignoreCase = true)) {
            return UploadResult(false, 0, "Webhook URL must use HTTPS")
        }
        if (secret.length < 24) {
            return UploadResult(false, 0, "Webhook secret must be at least 24 characters")
        }

        val timestamp = Instant.now().epochSecond.toString()
        val signature = hmacSha256(secret, timestamp + "." + json)

        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Torque-Timestamp", timestamp)
            setRequestProperty("X-Torque-Signature", "sha256=" + signature)
        }

        return try {
            connection.outputStream.use {
                it.write(json.toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val message = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            UploadResult(code in 200..299, code, message)
        } catch (e: Exception) {
            UploadResult(false, 0, e.message ?: e.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private fun hmacSha256(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }
}
