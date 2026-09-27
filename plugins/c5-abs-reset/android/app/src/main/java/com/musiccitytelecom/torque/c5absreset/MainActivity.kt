package com.musiccitytelecom.torque.c5absreset

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private lateinit var torque: TorqueClient
    private lateinit var engine: AbsResetEngine
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var rawText: TextView
    private lateinit var logText: TextView
    private lateinit var scanButton: Button
    private lateinit var clearButton: Button

    private val io = Executors.newSingleThreadExecutor()
    private val automaticPassStarted = AtomicBoolean(false)
    @Volatile private var connected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        rawText = findViewById(R.id.rawText)
        logText = findViewById(R.id.logText)
        scanButton = findViewById(R.id.scanButton)
        clearButton = findViewById(R.id.clearButton)

        torque = TorqueClient(this)
        engine = AbsResetEngine(torque)

        scanButton.setOnClickListener { scanAllModules() }
        clearButton.setOnClickListener {
            if (MaintenanceConfig.ONE_TAP_EBCM_CLEAR == 1) {
                performManualEbcmClear()
            } else {
                confirmManualEbcmClear()
            }
        }

        logText.text = MaintenanceLog.summary(this)
    }

    override fun onResume() {
        super.onResume()
        torque.bind { ok, message ->
            connected = ok
            runOnUiThread {
                statusText.text = if (ok && !torque.hasFullPermissions()) {
                    message + " — enable full plugin permission for GM module diagnostics"
                } else {
                    message
                }
            }

            if (
                ok &&
                torque.hasFullPermissions() &&
                MaintenanceConfig.AUTO_MAINTENANCE_ON_CONNECT == 1 &&
                automaticPassStarted.compareAndSet(false, true)
            ) {
                runAutomaticPass()
            }
        }
    }

    override fun onDestroy() {
        torque.unbind()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun runAutomaticPass() {
        if (MaintenanceConfig.SILENT_AUTOMATIC_PASS != 1) {
            runOnUiThread {
                setBusy(true, "Automatic module scan / cleanup running...")
            }
        }

        io.execute {
            val report = engine.runAutomaticMaintenance()
            MaintenanceLog.append(this, report)

            runOnUiThread {
                if (MaintenanceConfig.SILENT_AUTOMATIC_PASS != 1) {
                    setBusy(false, "Automatic maintenance pass complete")
                    renderMaintenance(report)
                } else {
                    scanButton.isEnabled = true
                    clearButton.isEnabled = true
                    statusText.text = "Connected — automatic maintenance logged"
                }
                logText.text = MaintenanceLog.summary(this)
            }
        }
    }

    private fun scanAllModules() {
        if (!connected) {
            statusText.text = "Torque is not connected."
            return
        }

        setBusy(true, "Scanning EBCM, BCM, HVAC and RDCM...")
        io.execute {
            val results = C5Modules.ALL.map { engine.readCodes(it) }
            runOnUiThread {
                setBusy(false, "Module scan complete")
                renderSnapshots(results)
            }
        }
    }

    private fun confirmManualEbcmClear() {
        if (!connected) {
            statusText.text = "Torque is not connected."
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Manually clear EBCM codes?")
            .setMessage(
                "This performs one stationary EBCM clear. If C1242 is current, the ABS/TCS warning may return immediately because the module is still detecting a pump-motor-circuit fault."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear once") { _, _ -> performManualEbcmClear() }
            .show()
    }

    private fun performManualEbcmClear() {
        if (!connected) {
            statusText.text = "Torque is not connected."
            return
        }

        setBusy(true, "Verifying stationary vehicle and clearing EBCM once...")
        io.execute {
            val result = engine.clearEbcmManuallyWhenStationary()
            runOnUiThread {
                setBusy(false, result.message)
                renderManualClear(result)
            }
        }
    }

    private fun renderMaintenance(report: MaintenanceReport) {
        val speed = report.speedKph?.let {
            String.format(Locale.US, "%.1f km/h", it)
        } ?: "--"

        resultText.text = buildString {
            append("Automatic pass\n")
            append("Stationary verified: ").append(report.stationaryVerified)
            append(" • speed ").append(speed).append("\n\n")

            report.results.forEach { r ->
                append(r.before.profile.name).append("\n")
                append("  current: ").append(codeList(r.before.current)).append("\n")
                append("  history/all: ").append(codeList(r.before.all)).append("\n")
                append("  action: ").append(decisionText(r.decision)).append("\n")
                if (r.clearAttempted) {
                    append("  clear ack: ").append(r.clearAcknowledged).append("\n")
                    append("  after current: ")
                        .append(codeList(r.after?.current.orEmpty()))
                        .append("\n")
                }
                append("\n")
            }
        }

        rawText.text = report.results.joinToString("\n") { r ->
            buildString {
                append(r.before.profile.key)
                append(" 19C2: ")
                append(ProtocolParser.prettyRaw(r.before.rawCurrent))
                append("\n")
                append(r.before.profile.key)
                append(" 19FFFF: ")
                append(ProtocolParser.prettyRaw(r.before.rawAll))
                if (r.rawClear.isNotEmpty()) {
                    append("\n")
                    append(r.before.profile.key)
                    append(" 14: ")
                    append(ProtocolParser.prettyRaw(r.rawClear))
                }
            }
        }
    }

    private fun renderSnapshots(results: List<ModuleDtcSnapshot>) {
        resultText.text = results.joinToString("\n\n") { s ->
            buildString {
                append(s.profile.name).append("\n")
                append("  current: ").append(codeList(s.current)).append("\n")
                append("  history/all: ").append(codeList(s.all))
            }
        }

        rawText.text = results.joinToString("\n") { s ->
            s.profile.key + " current=" + ProtocolParser.prettyRaw(s.rawCurrent) +
                " all=" + ProtocolParser.prettyRaw(s.rawAll)
        }
    }

    private fun renderManualClear(result: AbsClearResult) {
        resultText.text = buildString {
            append(result.message).append("\n")
            append("Verified speed: ")
            append(result.speedKph?.let {
                String.format(Locale.US, "%.1f km/h", it)
            } ?: "--")
            append("\n")
            append("Before EBCM current: ")
            append(codeList(result.before?.current.orEmpty()))
            append("\n")
            append("After EBCM current: ")
            append(codeList(result.afterCurrent))
        }

        rawText.text = "EBCM service 14: " + ProtocolParser.prettyRaw(result.rawClear)
    }

    private fun codeList(codes: List<AbsDtc>): String =
        if (codes.isEmpty()) "none" else codes.joinToString { it.code }

    private fun decisionText(decision: AutoDecision): String = when (decision) {
        AutoDecision.NO_WATCHED_CODE -> "logged; no watched code present"
        AutoDecision.NOT_STATIONARY -> "logged only; vehicle not verified stationary"
        AutoDecision.CURRENT_READ_UNVERIFIED -> "logged only; current-state read not verified"
        AutoDecision.CURRENT_SAFETY_FAULT_BLOCKED ->
            "CURRENT SAFETY FAULT — not auto-cleared"
        AutoDecision.ACTIVE_FAULT_LOGGED ->
            "current fault logged; not repeatedly erased"
        AutoDecision.CLEAR_HISTORY_ONCE ->
            "history-only watched code cleared once"
        AutoDecision.CLEAR_CURRENT_NON_SAFETY_ONCE ->
            "current non-safety watched code clear attempted once"
    }

    private fun setBusy(busy: Boolean, message: String) {
        scanButton.isEnabled = !busy
        clearButton.isEnabled = !busy
        statusText.text = message
    }
}
