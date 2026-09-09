package io.github.hatake716.bossrush

import android.content.res.AssetManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.Closeable
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** Decode off the UI/audio threads; no streaming I/O or codec work at a loop boundary. */
internal object MusicDecoder {
    fun decode(assets: AssetManager, track: MusicTrack): MusicPcm {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            assets.openFd(track.asset).use { extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            val index = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            extractor.selectTrack(index)
            val inputFormat = extractor.getTrackFormat(index)
            val codec = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
            decoder = codec
            codec.configure(inputFormat, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val samples = ShortArray(track.frames * 2 + 16384)
            var size = 0
            var inputEnded = false
            var outputEnded = false
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (!outputEnded) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Music load cancelled")
                check(System.nanoTime() < deadline) { "Music decode timed out: ${track.id}" }
                if (!inputEnded) {
                    val input = codec.dequeueInputBuffer(5000)
                    if (input >= 0) {
                        val buffer = codec.getInputBuffer(input)!!
                        val length = extractor.readSampleData(buffer, 0)
                        if (length < 0) {
                            codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(input, 0, length, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val output = codec.dequeueOutputBuffer(info, 5000)
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val format = codec.outputFormat
                    check(format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == MusicCatalog.SAMPLE_RATE)
                    check(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 2)
                    check(!format.containsKey(MediaFormat.KEY_PCM_ENCODING) || format.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_16BIT)
                } else if (output >= 0) {
                    val buffer = codec.getOutputBuffer(output)!!
                    if (info.size > 0) {
                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                        val pcm = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val count = pcm.remaining()
                        check(size + count <= samples.size) { "Unexpected music length: ${track.id}" }
                        pcm.get(samples, size, count); size += count
                    }
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(output, false)
                }
            }
            // Vorbis decoders may omit a short priming block. Never pad a loop with silence.
            check(size % 2 == 0 && size / 2 in track.frames - 2048..track.frames + 2048) { "Incomplete music: ${track.id}, $size samples" }
            val result = samples.copyOf(min(size, track.frames * 2))
            // Smooth only 1.45ms across the decoded edge; beat spacing stays intact.
            val frames = result.size / 2
            for (ch in 0..1) {
                val seam = (result[ch].toInt() + result[(frames-1)*2+ch]) / 2.0
                for (i in 0 until 64) {
                    val ratio = i / 63.0
                    val first = i*2+ch; val last = (frames-64+i)*2+ch
                    result[first] = (seam*(1-ratio)+result[first]*ratio).toInt().toShort()
                    result[last] = (result[last]*(1-ratio)+seam*ratio).toInt().toShort()
                }
            }
            return MusicPcm(track.id, result)
        } finally {
            try { decoder?.stop() } catch (_: IllegalStateException) { }
            decoder?.release(); extractor.release()
        }
    }
}

/** Session-scoped loader. A cancelled/old scene can never publish into a new session. */
internal class MusicLibrary(private val assets: AssetManager): Closeable {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "bossrush-music-loader").apply { isDaemon = true } }
    private val cache = object: LinkedHashMap<String, MusicPcm>(3, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MusicPcm>?) = size > 2
    }
    @Volatile private var wanted: MusicTrack? = null
    @Volatile private var closed = false
    @Volatile var ready: MusicPcm? = null
        private set
    @Synchronized fun request(track: MusicTrack?) {
        if (closed || wanted == track) return
        wanted = track; ready = null
        if (track == null) return
        executor.execute {
            if (closed || wanted != track) return@execute
            try {
                val pcm = cache[track.id] ?: MusicDecoder.decode(assets, track).also { cache[track.id] = it }
                if (!closed && wanted == track) ready = pcm
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                android.util.Log.w("BOSSRUSH", "Music unavailable: ${track.id}", e)
            }
        }
    }
    @Synchronized override fun close() {
        closed = true; wanted = null; ready = null; executor.shutdownNow()
    }
}
