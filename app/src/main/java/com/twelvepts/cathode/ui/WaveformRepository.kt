package com.twelvepts.cathode.ui

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs

internal object WaveformRepository {
    private const val PREFS = "cathode_waveforms"
    private const val BINS = 84

    fun load(context: Context, cacheKey: String, source: Uri): List<Float>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "v1_" + cacheKey.hashCode().toUInt().toString(16)
        prefs.getString(key, null)?.split(',')?.mapNotNull(String::toFloatOrNull)?.takeIf { it.size == BINS }?.let { return it }
        val decoded = decode(context, source) ?: return null
        prefs.edit().putString(key, decoded.joinToString(",") { "%.4f".format(java.util.Locale.US, it) }).apply()
        return decoded
    }

    private fun decode(context: Context, source: Uri): List<Float>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, source, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val peaks = FloatArray(BINS)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex) ?: continue
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        pcmEncoding = if (codec.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        codec.getOutputBuffer(outputIndex)?.let { buffer ->
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            var peak = 0f
                            when (pcmEncoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> {
                                    val samples = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
                                    while (samples.hasRemaining()) peak = maxOf(peak, abs(samples.get()))
                                }
                                AudioFormat.ENCODING_PCM_8BIT -> while (buffer.hasRemaining()) peak = maxOf(peak, abs((buffer.get().toInt() and 0xff) - 128) / 128f)
                                else -> {
                                    val samples = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                                    while (samples.hasRemaining()) peak = maxOf(peak, abs(samples.get().toInt()) / 32768f)
                                }
                            }
                            val bin = ((info.presentationTimeUs.toDouble() / durationUs) * BINS).toInt().coerceIn(0, BINS - 1)
                            peaks[bin] = maxOf(peaks[bin], peak)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
            val max = peaks.maxOrNull()?.takeIf { it > .001f } ?: return null
            return peaks.map { (.12f + .88f * (it / max)).coerceIn(.12f, 1f) }
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}
