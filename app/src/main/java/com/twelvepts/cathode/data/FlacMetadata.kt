package com.twelvepts.cathode.data

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class FlacMetadata(val replayGainDb: Float? = null, val hasSeekTable: Boolean = false)

fun readFlacMetadata(input: InputStream): FlacMetadata {
    if (input.readNBytes(4).decodeToString() != "fLaC") return FlacMetadata()
    var gain: Float? = null
    var seekTable = false
    var last = false
    while (!last) {
        val first = input.read()
        if (first < 0) break
        last = first and 0x80 != 0
        val type = first and 0x7f
        val lengthBytes = input.readNBytes(3)
        if (lengthBytes.size != 3) break
        val length = ((lengthBytes[0].toInt() and 0xff) shl 16) or
            ((lengthBytes[1].toInt() and 0xff) shl 8) or (lengthBytes[2].toInt() and 0xff)
        if (length > 4_194_304) break
        val payload = input.readNBytes(length)
        if (payload.size != length) break
        if (type == 3 && length >= 18) seekTable = true
        if (type == 4) {
            val comments = parseVorbisComments(payload)
            gain = comments["REPLAYGAIN_TRACK_GAIN"]?.substringBefore(" ")?.toFloatOrNull()
                ?: comments["R128_TRACK_GAIN"]?.toFloatOrNull()?.div(256f)
        }
    }
    return FlacMetadata(gain, seekTable)
}

private fun parseVorbisComments(payload: ByteArray): Map<String, String> = runCatching {
    val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
    val vendorLength = buffer.int
    if (vendorLength !in 0..buffer.remaining()) return@runCatching emptyMap()
    buffer.position(buffer.position() + vendorLength)
    val count = buffer.int.coerceIn(0, 10_000)
    buildMap {
        repeat(count) {
            if (buffer.remaining() < 4) return@repeat
            val length = buffer.int
            if (length !in 0..buffer.remaining()) return@repeat
            val value = ByteArray(length).also(buffer::get).decodeToString()
            val separator = value.indexOf('=')
            if (separator > 0) put(value.substring(0, separator).uppercase(), value.substring(separator + 1))
        }
    }
}.getOrDefault(emptyMap())
