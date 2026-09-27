package com.musiccitytelecom.torque.c5diag

object ResponseParser {
    fun linesToBytes(lines: List<String>): List<ByteArray> =
        lines.mapNotNull { cleanHexLine(it) }

    fun cleanHexLine(raw: String): ByteArray? {
        /*
         * Torque/ELM adapters sometimes return a valid frame followed by a
         * status trailer on the same string, for example:
         *
         *   6A200000000000<DATA ERROR
         *
         * Keep the valid frame before '<' and discard the adapter status
         * trailer. Status-only strings such as "NO DATA" and "SEARCHING..."
         * still fail the strict hex check below.
         */
        val candidate = raw
            .trim()
            .trimEnd('>')
            .substringBefore('<')
            .trim()

        if (candidate.isEmpty()) return null

        // Reject adapter/status text instead of accidentally extracting A-F letters from it.
        if (!candidate.matches(Regex("^[0-9A-Fa-f :\\t-]+$"))) return null

        val hex = candidate.replace(Regex("[^0-9A-Fa-f]"), "")
        if (hex.length < 2 || hex.length % 2 != 0) return null

        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun findAfter(lines: List<String>, prefix: IntArray, count: Int): ByteArray? {
        for (bytes in linesToBytes(lines)) {
            if (bytes.size < prefix.size + count) continue
            for (start in 0..bytes.size - prefix.size - count) {
                var match = true
                for (i in prefix.indices) {
                    if ((bytes[start + i].toInt() and 0xFF) != prefix[i]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    return bytes.copyOfRange(start + prefix.size, start + prefix.size + count)
                }
            }
        }
        return null
    }

    fun parseMode01(lines: List<String>, pid: Int, bytesNeeded: Int): ByteArray? =
        findAfter(lines, intArrayOf(0x41, pid and 0xFF), bytesNeeded)

    fun parseMode22(lines: List<String>, pid: Int, bytesNeeded: Int): ByteArray? =
        findAfter(
            lines,
            intArrayOf(0x62, (pid shr 8) and 0xFF, pid and 0xFF),
            bytesNeeded
        )

    fun parseWheelPacket(lines: List<String>): WheelSpeeds? {
        val payload = findAfter(lines, intArrayOf(0x6A, 0x20), 6) ?: return null
        val u = payload.map { it.toInt() and 0xFF }
        return WheelSpeeds(
            lfKph = u[0].toDouble(),
            rfKph = u[1].toDouble(),
            lrKph = u[2].toDouble(),
            rrKph = u[3].toDouble(),
            vssKph = u[4].toDouble(),
            aux = u[5],
            raw = lines.joinToString(" | "),
            candidate = true
        )
    }

    fun u16(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
}
