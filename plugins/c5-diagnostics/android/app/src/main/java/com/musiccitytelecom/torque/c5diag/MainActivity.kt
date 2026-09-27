package com.musiccitytelecom.torque.c5diag

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var torque: TorqueClient
    private var torqueConnected = false
    private var engine: DiagnosticEngine? = null
    private var settings = PluginSettings("", "", "", true)

    private lateinit var statusText: TextView
    private lateinit var vehicleText: TextView
    private lateinit var wheelStatusText: TextView
    private lateinit var rawWheelText: TextView
    private lateinit var supportText: TextView
    private lateinit var sessionText: TextView
    private lateinit var startStopButton: Button
    private lateinit var uploadButton: Button

    private val misfireViews = mutableListOf<TextView>()
    private val wheelViews = mutableListOf<TextView>()
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        vehicleText = findViewById(R.id.vehicleText)
        wheelStatusText = findViewById(R.id.wheelStatusText)
        rawWheelText = findViewById(R.id.rawWheelText)
        supportText = findViewById(R.id.supportText)
        sessionText = findViewById(R.id.sessionText)
        startStopButton = findViewById(R.id.startStopButton)
        uploadButton = findViewById(R.id.uploadButton)

        buildDynamicRows()

        torque = TorqueClient(this)
        startStopButton.setOnClickListener { toggleSession() }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            stopSession()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.exportButton).setOnClickListener {
            exportAndShare()
        }

        uploadButton.setOnClickListener {
            uploadReport()
        }
    }

    override fun onResume() {
        super.onResume()
        settings = SettingsStore.load(this)
        updateVehicleText()

        torque.bind { connected, message ->
            runOnUiThread {
                torqueConnected = connected
                statusText.text = message
                if (connected && !torque.hasFullPermissions()) {
                    statusText.text = message + " — full plugin permission OFF"
                }
            }
        }
    }

    override fun onDestroy() {
        stopSession()
        torque.unbind()
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun buildDynamicRows() {
        val misfireContainer = findViewById<LinearLayout>(R.id.misfireContainer)
        for (i in 1..8) {
            val tv = TextView(this).apply {
                text = "C" + i + "  current --   history --   session --"
                textSize = 16f
                setPadding(0, 6, 0, 6)
            }
            misfireContainer.addView(tv)
            misfireViews.add(tv)
        }

        val wheelContainer = findViewById<LinearLayout>(R.id.wheelContainer)
        listOf("LF", "RF", "LR", "RR").forEach { name ->
            val tv = TextView(this).apply {
                text = name + "  -- mph  /  -- km/h"
                textSize = 16f
                setPadding(0, 6, 0, 6)
            }
            wheelContainer.addView(tv)
            wheelViews.add(tv)
        }
    }

    private fun toggleSession() {
        if (engine?.isRunning() == true) {
            stopSession()
            return
        }

        if (!torqueConnected) {
            Toast.makeText(this, "Torque Pro is not connected to the plugin service", Toast.LENGTH_LONG).show()
            return
        }

        settings = SettingsStore.load(this)
        val newEngine = DiagnosticEngine(torque, settings) { snapshot ->
            runOnUiThread { render(snapshot) }
        }
        engine = newEngine
        newEngine.start()
        startStopButton.text = getString(R.string.stop)
        sessionText.text = "Diagnostic session running"
    }

    private fun stopSession() {
        engine?.let {
            if (it.isRunning()) it.stop()
        }
        startStopButton.text = getString(R.string.start)
    }

    private fun render(s: DiagnosticSnapshot) {
        statusText.text = if (s.fullTorquePermissions) {
            "Torque connected — full plugin permission enabled"
        } else {
            "Torque connected — full permission OFF; enable it in Torque plugin settings for wheel data"
        }

        for (i in 0 until 8) {
            misfireViews[i].text = String.format(
                Locale.US,
                "C%d  current %d   history %d   session +%d",
                i + 1,
                s.misfireCurrent[i],
                s.misfireHistory[i],
                s.misfireSession[i]
            )
        }

        val w = s.wheelSpeeds
        if (w != null) {
            wheelStatusText.text =
                "Candidate EBCM 6A20 packet matched — verify against known speed before treating as confirmed"
            val vals = doubleArrayOf(w.lfKph, w.rfKph, w.lrKph, w.rrKph)
            val names = arrayOf("LF", "RF", "LR", "RR")
            for (i in vals.indices) {
                wheelViews[i].text = String.format(
                    Locale.US,
                    "%s  %.1f mph  /  %.1f km/h",
                    names[i],
                    vals[i] * 0.621371,
                    vals[i]
                )
            }
        } else {
            wheelStatusText.text = if (!s.fullTorquePermissions) {
                "Wheel packet requires Torque: Settings > Plugins > allow full permissions for this plugin"
            } else {
                "No valid 6A20 wheel packet received yet; raw response will be retained if the EBCM replies"
            }
        }

        rawWheelText.text = if (s.rawWheelResponse.isNullOrBlank()) {
            ""
        } else {
            "EBCM raw: " + s.rawWheelResponse
        }

        supportText.text = buildString {
            append("RPM: ").append(fmt(s.values["rpm"], 0)).append('\n')
            append("Vehicle: ").append(fmtMph(s.values["speed_kph"])).append('\n')
            append("Load: ").append(fmtPct(s.values["load_pct"])).append('\n')
            append("Coolant: ").append(fmtTempF(s.values["coolant_c"])).append('\n')
            append("STFT B1/B2: ")
                .append(fmtPct(s.values["stft_b1_pct"])).append(" / ")
                .append(fmtPct(s.values["stft_b2_pct"])).append('\n')
            append("LTFT B1/B2: ")
                .append(fmtPct(s.values["ltft_b1_pct"])).append(" / ")
                .append(fmtPct(s.values["ltft_b2_pct"])).append('\n')
            append("MAF: ").append(fmt(s.values["maf_gps"], 2)).append(" g/s\n")
            append("Throttle: ").append(fmtPct(s.values["throttle_pct"])).append('\n')
            append("Timing: ").append(fmt(s.values["timing_deg"], 1)).append(" deg\n")
            append("Knock retard: ").append(fmt(s.values["knock_retard_deg"], 1)).append(" deg\n")
            append("Module voltage: ").append(fmt(s.values["module_v"], 2)).append(" V")
        }

        sessionText.text = "Session misfires: " + s.misfireSession.sum()
    }

    private fun exportAndShare() {
        val report = engine?.currentReport()
        if (report == null || report.samples.isEmpty()) {
            Toast.makeText(this, "Start a session and collect data first", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val files = ReportExporter.write(this, report)
            val uris = arrayListOf(
                FileProvider.getUriForFile(this, "com.musiccitytelecom.torque.c5diag.files", files.json),
                FileProvider.getUriForFile(this, "com.musiccitytelecom.torque.c5diag.files", files.csv)
            )
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, "C5 diagnostic report " + report.id)
            }
            startActivity(Intent.createChooser(intent, "Export diagnostic report"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: " + (e.message ?: "unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    private fun uploadReport() {
        val report = engine?.currentReport()
        if (report == null || report.samples.isEmpty()) {
            Toast.makeText(this, "Start a session and collect data first", Toast.LENGTH_LONG).show()
            return
        }

        settings = SettingsStore.load(this)
        if (settings.webhookUrl.isBlank() || settings.webhookSecret.isBlank()) {
            Toast.makeText(this, "Configure the HTTPS webhook and secret in Settings", Toast.LENGTH_LONG).show()
            return
        }

        val json = ReportExporter.toJson(report)
        uploadButton.isEnabled = false
        ioExecutor.execute {
            val result = WebhookExporter.upload(settings.webhookUrl, settings.webhookSecret, json)
            runOnUiThread {
                uploadButton.isEnabled = true
                val msg = if (result.ok) {
                    "Report uploaded (HTTP " + result.statusCode + ")"
                } else {
                    "Upload failed: " + result.message
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateVehicleText() {
        vehicleText.text = if (settings.vin.isBlank()) {
            "2004 Corvette C5 — VIN not entered"
        } else {
            "2004 Corvette C5 — VIN " + settings.vin
        }
    }

    private fun fmt(value: Double?, decimals: Int): String {
        if (value == null) return "--"
        return String.format(Locale.US, "%." + decimals + "f", value)
    }

    private fun fmtPct(value: Double?): String =
        if (value == null) "--" else String.format(Locale.US, "%.1f%%", value)

    private fun fmtMph(kph: Double?): String =
        if (kph == null) "--" else String.format(Locale.US, "%.1f mph (%.1f km/h)", kph * 0.621371, kph)

    private fun fmtTempF(c: Double?): String =
        if (c == null) "--" else String.format(Locale.US, "%.0f F (%.0f C)", c * 9.0 / 5.0 + 32.0, c)
}
