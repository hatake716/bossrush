package io.github.hatake716.bossrush

import kotlin.math.*

/** Interleaved stereo PCM; one complete, pre-rendered musical loop. */
data class MusicPcm(val id: String, val samples: ShortArray) {
    init { require(samples.isNotEmpty() && samples.size % 2 == 0) }
    val frames get() = samples.size / 2
}

/** Only the audio thread owns this mixer. Pure PCM makes loop and mute behavior testable. */
class AudioMix {
    val effects = EffectMixer(MusicCatalog.SAMPLE_RATE)
    var music: MusicPcm? = null
        set(value) { if (field !== value) { field = value; position = 0; entrance = 0.0 } }
    var position = 0
        private set
    var duck = 1.0
        private set
    private var entrance = 0.0
    var peak = 0
        private set
    fun render(buffer: ShortArray, enabled: Boolean, volume: Double) {
        require(buffer.size % 2 == 0)
        peak = 0
        if (!enabled) effects.clear()
        val pcm = music
        for (i in buffer.indices step 2) {
            val target = if (effects.voiceCount > 0) .58 else 1.0
            duck += (target - duck) * if (target < duck) .0035 else .00035
            val effect = effects.next() * .85
            entrance = min(1.0, entrance + 1.0 / (MusicCatalog.SAMPLE_RATE * .012))
            for (channel in 0..1) {
                val sample = if (pcm == null) .0 else pcm.samples[position * 2 + channel] / 32768.0
                val mixed = sample * .72 * duck * entrance + effect
                val value = if (enabled) (tanh(mixed) * volume.coerceIn(.0, 1.0) * 30000).roundToInt() else 0
                buffer[i + channel] = value.toShort()
                peak = max(peak, abs(value))
            }
            if (pcm != null) position = (position + 1) % pcm.frames
        }
    }
}
