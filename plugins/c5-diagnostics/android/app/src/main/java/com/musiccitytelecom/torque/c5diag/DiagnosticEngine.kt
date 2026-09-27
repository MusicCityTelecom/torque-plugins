package com.musiccitytelecom.torque.c5diag

import android.os.SystemClock
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.max

class DiagnosticEngine(
    private val torque: TorqueClient,
    private val settings: PluginSettings,
    private val listener: (DiagnosticSnapshot) -> Unit
) {
    companion object {
        private const val PCM_HEADER = "6C10F1"
        private const val EBCM_HEADER = "6C28F1"
        private const val PROFILE = "2004 Corvette C5 / GM P59 initial profile"
    }

    private data class PollJob(
        val periodMs: Long,
        var nextDueMs: Long,
        val action: () -> Unit
    )

    private var executor: ScheduledExecutorService? = null
    private val jobs = mutableListOf<PollJob>()
    private val values = linkedMapOf<String, Double>()
    private val misfireCurrent = IntArray(8)
    private val misfireHistory = IntArray(8)
    private val misfireSession = LongArray(8)
    private val lastCurrent = IntArray(8) { -1 }

    @Volatile private var latestWheel: WheelSpeeds? = null
    @Volatile private var rawWheelResponse: String? = null
    @Volatile private var latestSnapshot: DiagnosticSnapshot? = null

    private val samples = Collections.synchronizedList(mutableListOf<DiagnosticSnapshot>())
    private var lastEmitMs = 0L
    private var lastRecordMs = 0L
    private var sessionId = ""
    private var startedAtMs = 0L

    fun start() {
        if (executor != null) return

        sessionId = UUID.randomUUID().toString()
        startedAtMs = System.currentTimeMillis()
        values.clear()
        misfireCurrent.fill(0)
        misfireHistory.fill(0)
        misfireSession.fill(0)
        lastCurrent.fill(-1)
        samples.clear()
        latestWheel = null
        rawWheelResponse = null
        latestSnapshot = null

        val now = SystemClock.elapsedRealtime()
        buildJobs(now)

        executor = Executors.newSingleThreadScheduledExecutor().also { exec ->
            exec.scheduleWithFixedDelay(
                { tick() },
                0L,
                90L,
                TimeUnit.MILLISECONDS
            )
        }
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
        emitSnapshot(forceRecord = true)
    }

    fun isRunning(): Boolean = executor != null

    fun currentReport(): SessionReport? {
        if (sessionId.isEmpty()) return null
        val copy = synchronized(samples) { samples.toList() }.toMutableList()
        val latest = latestSnapshot
        if (latest != null && (copy.isEmpty() || copy.last().timestampMs != latest.timestampMs)) {
            copy.add(latest)
        }
        return SessionReport(
            id = sessionId,
            startedAtMs = startedAtMs,
            generatedAtMs = System.currentTimeMillis(),
            vin = settings.vin,
            profile = PROFILE,
            samples = copy
        )
    }

    private fun buildJobs(now: Long) {
        jobs.clear()

        // One EBCM request returns all four wheels. Mode 2A requires Torque's
        // "Allow full permissions" option for this plugin.
        addJob(now, 500L, 0L) { queryWheels() }

        val currentPids = currentMisfirePids()
        currentPids.forEachIndexed { index, command ->
            addJob(now, 1600L, 120L + index * 170L) {
                queryMisfireCurrent(index, command)
            }
        }

        val historyPids = arrayOf(
            "221201", "221202", "221203", "221204",
            "2211F8", "2211F9", "2211FA", "2211FB"
        )
        historyPids.forEachIndexed { index, command ->
            addJob(now, 20000L, 1600L + index * 350L) {
                queryMisfireHistory(index, command)
            }
        }

        addJob(now, 1000L, 250L) { queryMode01("rpm", 0x0C, 2) { b -> ResponseParser.u16(b) / 4.0 } }
        addJob(now, 1000L, 700L) { queryMode01("speed_kph", 0x0D, 1) { b -> u8(b[0]).toDouble() } }

        addJob(now, 6000L, 900L) { queryMode01("load_pct", 0x04, 1) { b -> u8(b[0]) * 100.0 / 255.0 } }
        addJob(now, 6000L, 1300L) { queryMode01("coolant_c", 0x05, 1) { b -> u8(b[0]) - 40.0 } }
        addJob(now, 6000L, 1700L) { queryMode01("stft_b1_pct", 0x06, 1) { b -> (u8(b[0]) - 128) * 100.0 / 128.0 } }
        addJob(now, 6000L, 2100L) { queryMode01("ltft_b1_pct", 0x07, 1) { b -> (u8(b[0]) - 128) * 100.0 / 128.0 } }
        addJob(now, 6000L, 2500L) { queryMode01("stft_b2_pct", 0x08, 1) { b -> (u8(b[0]) - 128) * 100.0 / 128.0 } }
        addJob(now, 6000L, 2900L) { queryMode01("ltft_b2_pct", 0x09, 1) { b -> (u8(b[0]) - 128) * 100.0 / 128.0 } }
        addJob(now, 6000L, 3300L) { queryMode01("timing_deg", 0x0E, 1) { b -> u8(b[0]) / 2.0 - 64.0 } }
        addJob(now, 6000L, 3700L) { queryMode01("maf_gps", 0x10, 2) { b -> ResponseParser.u16(b) / 100.0 } }
        addJob(now, 6000L, 4100L) { queryMode01("throttle_pct", 0x11, 1) { b -> u8(b[0]) * 100.0 / 255.0 } }
        addJob(now, 6000L, 4500L) { queryMode01("module_v", 0x42, 2) { b -> ResponseParser.u16(b) / 1000.0 } }
        addJob(now, 3000L, 1100L) { queryKnockRetard() }
    }

    private fun addJob(now: Long, period: Long, offset: Long, action: () -> Unit) {
        jobs.add(PollJob(period, now + offset, action))
    }

    private fun tick() {
        try {
            val now = SystemClock.elapsedRealtime()
            val due = jobs
                .filter { it.nextDueMs <= now }
                .minByOrNull { it.nextDueMs }

            if (due != null) {
                due.nextDueMs = max(now, due.nextDueMs) + due.periodMs
                due.action()
            }

            if (now - lastEmitMs >= 250L) {
                emitSnapshot(forceRecord = false)
                lastEmitMs = now
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            // A single malformed/unsupported PID must not terminate the session.
        }
    }

    private fun currentMisfirePids(): Array<String> {
        val first = if (settings.useP59C1C2Swap) "221206" else "221205"
        val second = if (settings.useP59C1C2Swap) "221205" else "221206"
        return arrayOf(
            first, second, "221207", "221208",
            "2211EA", "2211EB", "2211EC", "2211ED"
        )
    }

    private fun queryMisfireCurrent(index: Int, command: String) {
        val pid = command.takeLast(4).toInt(16)
        val lines = torque.query(PCM_HEADER, command)
        val payload = ResponseParser.parseMode22(lines, pid, 1) ?: return
        val current = u8(payload[0])

        val previous = lastCurrent[index]
        if (previous >= 0) {
            // Current GM counters can reset as the monitoring window rolls.
            // Treat a lower value as a new window, not as a huge unsigned wrap.
            val delta = if (current >= previous) current - previous else current
            if (delta in 1..255) misfireSession[index] += delta.toLong()
        }
        lastCurrent[index] = current
        misfireCurrent[index] = current
    }

    private fun queryMisfireHistory(index: Int, command: String) {
        val pid = command.takeLast(4).toInt(16)
        val lines = torque.query(PCM_HEADER, command)
        val payload = ResponseParser.parseMode22(lines, pid, 2) ?: return
        misfireHistory[index] = ResponseParser.u16(payload)
    }

    private fun queryWheels() {
        if (!torque.hasFullPermissions()) return

        val lines = torque.query(EBCM_HEADER, "2A0120")
        if (lines.isEmpty()) return

        rawWheelResponse = lines.joinToString(" | ")
        ResponseParser.parseWheelPacket(lines)?.let {
            latestWheel = it
        }
    }

    private fun queryKnockRetard() {
        val lines = torque.query(PCM_HEADER, "2211A6")
        val payload = ResponseParser.parseMode22(lines, 0x11A6, 1) ?: return
        values["knock_retard_deg"] = u8(payload[0]) * 22.5 / 255.0
    }

    private fun queryMode01(
        key: String,
        pid: Int,
        bytesNeeded: Int,
        converter: (ByteArray) -> Double
    ) {
        val command = "01" + pid.toString(16).padStart(2, '0').uppercase()
        val lines = torque.query(PCM_HEADER, command)
        val payload = ResponseParser.parseMode01(lines, pid, bytesNeeded) ?: return
        values[key] = converter(payload)
    }

    private fun emitSnapshot(forceRecord: Boolean) {
        if (sessionId.isEmpty()) return

        val snapshot = DiagnosticSnapshot(
            timestampMs = System.currentTimeMillis(),
            values = LinkedHashMap(values),
            misfireCurrent = misfireCurrent.copyOf(),
            misfireHistory = misfireHistory.copyOf(),
            misfireSession = misfireSession.copyOf(),
            wheelSpeeds = latestWheel,
            rawWheelResponse = rawWheelResponse,
            fullTorquePermissions = torque.hasFullPermissions()
        )
        latestSnapshot = snapshot

        val now = SystemClock.elapsedRealtime()
        if (forceRecord || now - lastRecordMs >= 1000L) {
            synchronized(samples) {
                if (samples.size >= 7200) samples.removeAt(0)
                samples.add(snapshot)
            }
            lastRecordMs = now
        }

        listener(snapshot)
    }

    private fun u8(b: Byte): Int = b.toInt() and 0xFF
}
