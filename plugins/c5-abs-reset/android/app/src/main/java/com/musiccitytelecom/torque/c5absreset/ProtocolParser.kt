package com.musiccitytelecom.torque.c5absreset

object ProtocolParser {
    private val frameRegex = Regex("(?i)(?:[0-9a-f]{2}[\\s:]*){1,}")

    fun parseDtcs(lines: List<String>, scope: String): List<AbsDtc> {
        val out = mutableListOf<AbsDtc>()
        for (frame in frames(lines)) {
            val start = payloadStart(frame)
            if (start >= frame.size || frame[start] != 0x59 || start + 2 >= frame.size) continue
            val hi = frame[start + 1]
            val lo = frame[start + 2]
            if (hi == 0 && lo == 0) continue
            out += AbsDtc(decodeDtc((hi shl 8) or lo), frame.getOrNull(start + 3), scope)
        }
        return out.distinctBy { Triple(it.code, it.status, it.scope) }
    }

    fun parseVehicleSpeedKph(lines: List<String>): Double? {
        for (frame in frames(lines)) {
            for (i in 0 until frame.size - 2) {
                if (frame[i] == 0x41 && frame[i + 1] == 0x0D) return frame[i + 2].toDouble()
            }
        }
        return null
    }

    fun clearAcknowledged(lines: List<String>): Boolean {
        for (frame in frames(lines)) {
            val start = payloadStart(frame)
            if (start < frame.size && frame[start] == 0x54) return true
        }
        return false
    }

    fun clearNegativeCode(lines: List<String>): Int? {
        for (frame in frames(lines)) {
            val start = payloadStart(frame)
            if (start + 2 < frame.size && frame[start] == 0x7F && frame[start + 1] == 0x14) {
                return frame[start + 2]
            }
        }
        return null
    }

    fun prettyRaw(lines: List<String>): String =
        if (lines.isEmpty()) "(no response)" else lines.joinToString(" | ")

    private fun frames(lines: List<String>): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        for (raw in lines) {
            val useful = raw.substringBefore('<')
            for (match in frameRegex.findAll(useful)) {
                val hex = match.value.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                if (hex.length < 2 || hex.length % 2 != 0) continue
                val bytes = hex.chunked(2).mapNotNull { it.toIntOrNull(16) }
                if (bytes.isNotEmpty()) out += bytes
            }
        }
        return out
    }

    private fun payloadStart(frame: List<Int>): Int =
        if (frame.size >= 4 && frame[0] == 0x6C) 3 else 0

    private fun decodeDtc(word: Int): String {
        val family = when ((word ushr 14) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val second = (word ushr 12) and 0x03
        val tail = (word and 0x0FFF).toString(16).uppercase().padStart(3, '0')
        return "$family$second$tail"
    }
}
