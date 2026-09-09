package io.github.hatake716.bossrush

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max

/** Rendered stereo piano-rock music mixed with the original synthesized combat SE. */
class Chiptune(context: Context) {
    private val assets = context.applicationContext.assets
    @Volatile var enabled = true
    @Volatile var volume = .7
    @Volatile private var requested: MusicTrack? = null
    private val effects = ConcurrentLinkedQueue<String>()
    @Volatile private var session: Session? = null
    internal val playingId get() = session?.playingId
    internal val renderedPeak get() = session?.peak ?: 0
    internal val running get() = session?.thread?.isAlive == true
    fun music(name: String, index: Int = 0) { requested = MusicCatalog.forScene(name, index) }
    fun effect(name: String) { if (effects.size < 12) effects.add(name) }
    @Synchronized fun start() {
        if (session != null) return
        session = Session().also { it.thread.start() }
    }
    @Synchronized fun stop() {
        val previous = session ?: return
        session = null
        previous.active = false
        previous.library.close()
        previous.thread.join(800)
        effects.clear()
    }
    private inner class Session {
        @Volatile var active = true
        @Volatile var playingId: String? = null
        @Volatile var peak = 0
        val library = MusicLibrary(assets)
        val thread = Thread({ renderLoop() }, "bossrush-audio").apply { isDaemon = true }
        fun renderLoop() {
            var output: AudioTrack? = null
            try {
                val minBuffer = AudioTrack.getMinBufferSize(MusicCatalog.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(MusicCatalog.SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(max(4096, minBuffer)).setTransferMode(AudioTrack.MODE_STREAM).build()
                output = track; track.play()
                val mixer = AudioMix()
                val buffer = ShortArray(1024)
                var old: MusicTrack? = null
                while (active) {
                    val current = requested
                    if (current != old) { old = current; mixer.music = null; mixer.effects.clear() }
                    library.request(current)
                    // Match the current request even if a previous decode completes during a scene change.
                    mixer.music = library.ready?.takeIf { it.id == current?.id }
                    playingId = mixer.music?.id
                    while (true) { val name = effects.poll() ?: break; if (enabled) mixer.effects.add(name) }
                    mixer.render(buffer, enabled, volume); peak = mixer.peak
                    var written = 0
                    while (active && written < buffer.size) {
                        val count = track.write(buffer, written, buffer.size - written)
                        if (count <= 0) { active = false; break }
                        written += count
                    }
                }
            } catch (e: IllegalArgumentException) {
                android.util.Log.w("BOSSRUSH", "Audio format unavailable", e)
            } catch (e: IllegalStateException) {
                android.util.Log.w("BOSSRUSH", "Audio output unavailable", e)
            } finally {
                active = false; playingId = null; peak = 0; library.close()
                try { output?.pause(); output?.flush() } catch (_: IllegalStateException) { }
                output?.release()
            }
        }
    }
}
