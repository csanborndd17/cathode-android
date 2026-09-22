package com.twelvepts.cathode.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SpectralResult(val suspectedTranscode: Boolean, val estimatedCutoffHz: Int?, val windows: Int)

object LosslessAnalyzer {
    private const val WINDOW = 2048
    private const val TARGET_WINDOWS = 6

    fun analyze(context: Context, source: Uri): SpectralResult? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, source, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val sourceFormat = extractor.getTrackFormat(track)
            val mime = sourceFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            if (sampleRate < 44_100) return SpectralResult(false, sampleRate / 2, 0)
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(sourceFormat, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var nextSampleUs = 5_000_000L
            val ratios = mutableListOf<Double>()
            val cutoffs = mutableListOf<Int>()
            while (!outputDone && ratios.size < TARGET_WINDOWS) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> encoding = codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (index >= 0) {
                        if (info.presentationTimeUs >= nextSampleUs && info.size > 0) {
                            val buffer = codec.getOutputBuffer(index)
                            if (buffer != null) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val samples = when (encoding) {
                                    AudioFormat.ENCODING_PCM_FLOAT -> buffer.order(ByteOrder.nativeOrder()).asFloatBuffer().let { values ->
                                        FloatArray(minOf(WINDOW, values.remaining())) { values.get() }
                                    }
                                    else -> buffer.order(ByteOrder.nativeOrder()).asShortBuffer().let { values ->
                                        FloatArray(minOf(WINDOW, values.remaining())) { values.get() / 32768f }
                                    }
                                }
                                if (samples.size == WINDOW) spectrum(samples, sampleRate)?.let { (ratio, cutoff) -> ratios += ratio; cutoffs += cutoff }
                            }
                            nextSampleUs += 7_000_000L
                        }
                        codec.releaseOutputBuffer(index, false)
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
            if (ratios.size < 3) return SpectralResult(false, cutoffs.maxOrNull(), ratios.size)
            val lowHighFrequencyWindows = ratios.count { it < 0.00005 }
            val suspected = lowHighFrequencyWindows >= (ratios.size * .84).toInt().coerceAtLeast(3) && (cutoffs.maxOrNull() ?: sampleRate / 2) < 17_500
            return SpectralResult(suspected, cutoffs.maxOrNull(), ratios.size)
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun spectrum(samples: FloatArray, sampleRate: Int): Pair<Double, Int>? {
        val rms = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size)
        if (rms < .004) return null
        val real = DoubleArray(WINDOW) { i -> samples[i] * (.5 - .5 * cos(2.0 * Math.PI * i / (WINDOW - 1))) }
        val imaginary = DoubleArray(WINDOW)
        fft(real, imaginary)
        var total = 0.0
        var high = 0.0
        var maximum = 0.0
        val magnitudes = DoubleArray(WINDOW / 2)
        for (bin in 1 until WINDOW / 2) {
            val magnitude = real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
            magnitudes[bin] = magnitude
            total += magnitude
            maximum = maxOf(maximum, magnitude)
            if (bin.toDouble() * sampleRate / WINDOW >= 16_000) high += magnitude
        }
        if (total <= 0.0) return null
        val cutoffBin = (WINDOW / 2 - 1 downTo 1).firstOrNull { magnitudes[it] > maximum * .0001 } ?: 1
        return high / total to (cutoffBin * sampleRate / WINDOW)
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        var j = 0
        for (i in 1 until real.size) {
            var bit = real.size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val r = real[i]; real[i] = real[j]; real[j] = r
                val im = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = im
            }
        }
        var length = 2
        while (length <= real.size) {
            val angle = -2.0 * Math.PI / length
            val wLengthReal = cos(angle)
            val wLengthImaginary = sin(angle)
            for (start in real.indices step length) {
                var wr = 1.0; var wi = 0.0
                for (offset in 0 until length / 2) {
                    val even = start + offset; val odd = even + length / 2
                    val oddReal = real[odd] * wr - imaginary[odd] * wi
                    val oddImaginary = real[odd] * wi + imaginary[odd] * wr
                    real[odd] = real[even] - oddReal; imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal; imaginary[even] += oddImaginary
                    val nextWr = wr * wLengthReal - wi * wLengthImaginary
                    wi = wr * wLengthImaginary + wi * wLengthReal; wr = nextWr
                }
            }
            length = length shl 1
        }
    }
}

private fun MediaFormat.getInteger(key: String, fallback: Int): Int = if (containsKey(key)) getInteger(key) else fallback
