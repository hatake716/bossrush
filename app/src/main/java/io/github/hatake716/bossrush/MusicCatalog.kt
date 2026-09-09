package io.github.hatake716.bossrush

import kotlin.math.floor
import kotlin.math.roundToInt

data class MusicTrack(val id: String, val bpm: Int, val beats: Int, val bars: Int) {
    val frames = (bars * beats * 60.0 / bpm * MusicCatalog.SAMPLE_RATE).roundToInt()
    val seconds get() = frames.toDouble() / MusicCatalog.SAMPLE_RATE
    val asset get() = "music/$id.ogg"
}

/** The same whole four-chord phrases used by tools/music/render.py. */
object MusicCatalog {
    const val SAMPLE_RATE = 44100
    val battles = Bosses.all.mapIndexed { index, boss ->
        val beats = BattleScore.themes[index].beats
        val bars = floor(30.0 * boss.bpm / (60 * beats * 4) + .5).toInt() * 4
        MusicTrack(boss.id, boss.bpm, beats, bars)
    }
    val shop = MusicTrack("shop", 128, 4, 16)
    val ending = MusicTrack("ending", 96, 4, 12)
    val all = battles + shop + ending
    fun forScene(scene: String, bossIndex: Int = 0): MusicTrack? = when (scene) {
        "battle" -> battles[bossIndex.coerceIn(battles.indices)]
        "shop" -> shop
        "ending" -> ending
        else -> null // Title, job selection, codex, help and game over stay silent.
    }
}
