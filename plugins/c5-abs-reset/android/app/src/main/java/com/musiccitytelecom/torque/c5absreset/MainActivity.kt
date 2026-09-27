package com.musiccitytelecom.torque.c5absreset

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var torque: TorqueClient
    private lateinit var engine: AbsResetEngine
    private lateinit var statusText: TextView
    private lateinit var dtcText: TextView
    private lateinit var rawText: TextView
    private lateinit var readButton: Button
    private lateinit var clearButton: Button
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var connected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        dtcText = findViewById(R.id.dtcText)
        rawText = findViewById(R.id.rawText)
        readButton = findViewById(R.id.readButton)
        clearButton = findViewById(R.id.clearButton)

        torque = TorqueClient(this)
        engine = AbsResetEngine(torque)
        readButton.setOnClickListener { readCodes() }
        clearButton.setOnClickListener { confirmClear() }
    }

    override fun onResume() {
        super.onResume()
        torque.bind { ok, message ->
            connected = ok
            runOnUiThread {
                statusText.text = if (ok && !torque.hasFullPermissions()) {
                    message + " — enable full plugin permission for EBCM commands"
                } else message
            }
        }
    }

    override fun onDestroy() {
        torque.unbind()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun readCodes() {
        if (!connected) {
            statusText.text = "Torque is not connected."
            return
        }
        setBusy(true, "Reading EBCM DTCs...")
        io.execute {
            val result = engine.readCodes()
            runOnUiThread {
                setBusy(false, "EBCM read complete")
                renderRead(result)
            }
        }
    }

    private fun confirmClear() {
        if (!connected) {
            statusText.text = "Torque is not connected."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Clear ABS/TCS codes?")
            .setMessage(
                "This performs one EBCM read-then-clear cycle. It can turn the warning lamps off only if the fault is no longer present. " +
                    "Clearing is blocked unless vehicle speed is verified at 0 km/h. If the warning returns, diagnose the ABS/TCS fault."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Read then clear") { _, _ -> performClear() }
            .show()
    }

    private fun performClear() {
        setBusy(true, "Verifying stationary vehicle...")
        io.execute {
            val result = engine.clearWhenStationary()
            runOnUiThread {
                setBusy(false, result.message)
                renderClear(result)
            }
        }
    }

    private fun renderRead(result: AbsReadResult) {
        if (!torque.hasFullPermissions()) {
            dtcText.text = "Full Torque plugin permission is required for GM EBCM service 19."
            rawText.text = ""
            return
        }
        dtcText.text = if (result.dtcs.isEmpty()) {
            "No decodable EBCM DTC returned."
        } else {
            result.dtcs.joinToString("\n") {
                val st = it.status?.let { s ->
                    " status 0x" + s.toString(16).uppercase().padStart(2, '0')
                } ?: ""
                it.code + "  " + it.scope + st
            }
        }
        rawText.text =
            "19 C2 FF 00: " + ProtocolParser.prettyRaw(result.rawCurrent) + "\n" +
            "19 FF FF 00: " + ProtocolParser.prettyRaw(result.rawAll)
    }

    private fun renderClear(result: AbsClearResult) {
        val before = result.before?.dtcs.orEmpty()
        dtcText.text = buildString {
            append(result.message).append('\n')
            append("Verified speed: ")
            append(result.speedKph?.let { String.format(Locale.US, "%.1f km/h", it) } ?: "--")
            append('\n')
            append("Before: ")
            append(if (before.isEmpty()) "no decoded DTC" else before.joinToString { it.code })
            append('\n')
            append("After/current: ")
            append(if (result.afterCurrent.isEmpty()) "none returned" else result.afterCurrent.joinToString { it.code })
        }
        rawText.text = "Service 14 response: " + ProtocolParser.prettyRaw(result.rawClear)
    }

    private fun setBusy(busy: Boolean, message: String) {
        readButton.isEnabled = !busy
        clearButton.isEnabled = !busy
        statusText.text = message
    }
}
