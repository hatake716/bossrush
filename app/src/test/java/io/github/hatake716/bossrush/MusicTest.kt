package io.github.hatake716.bossrush

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class MusicTest {
    @Test fun everyThemeHasANonSilentDistinctBoundedWaveform() {
        val signatures=mutableSetOf<Long>()
        for(index in 0..31) {
            var hash=7L; var energy=.0; var maximum=.0
            for(i in 0 until 22050 step 3) {
                val s=ScoreSynth.sample(i.toDouble()/22050,"battle",index)
                assertTrue(s.isFinite()); maximum=max(maximum,abs(s)); energy+=s*s
                hash=hash*31+(s*30000).roundToInt()
            }
            assertTrue(energy>1); assertTrue(maximum<.8); signatures.add(hash)
        }
        assertEquals(32,signatures.size)
    }
    @Test fun exportOriginalMusicPreviews() {
        val dir=File("../artifacts/music"); dir.mkdirs()
        for((name,scene,index) in listOf(Triple("01-ratatoskr","battle",0),Triple("31-thor","battle",30),Triple("32-odin","battle",31),Triple("ending","ending",31))) {
            val length=ScoreSynth.SAMPLE_RATE*12
            val data=ByteBuffer.allocate(44+length*2).order(ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(36+length*2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
                .putInt(ScoreSynth.SAMPLE_RATE).putInt(ScoreSynth.SAMPLE_RATE*2).putShort(2).putShort(16).put("data".toByteArray()).putInt(length*2)
            repeat(length) { data.putShort((ScoreSynth.sample(it.toDouble()/ScoreSynth.SAMPLE_RATE,scene,index)*18000).toInt().toShort()) }
            File(dir,"$name.wav").writeBytes(data.array())
            assertEquals((44+length*2).toLong(),File(dir,"$name.wav").length())
        }
    }
}
