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
    @Test fun zunHarmonyHasMajorSixAndSevenThenMinorTonicInEverySection() {
        for(bar in 0 until 16) {
            val chord=BattleScore.chordAt(bar)
            assertEquals(if(bar%4<2) 4 else 3,chord[1]-chord[0])
            assertEquals(7,chord[2]-chord[0])
            assertEquals(listOf(8,10,0,0)[bar%4],chord[0])
        }
        assertTrue(Bosses.all.all { it.bpm in 192..232 })
        assertEquals(Bosses.all.map { it.id },BattleScore.themes.map { it.id })
        assertEquals(32,BattleScore.themes.map { it.phraseA to it.phraseB }.toSet().size)
        assertEquals(3,BattleScore.themes.first { it.id=="loki" }.beats)
        for(theme in BattleScore.themes) {
            assertEquals(16,theme.phraseA.size); assertEquals(16,theme.phraseB.size)
            assertEquals(theme.beats*4,theme.rhythm.length)
        }
    }
    @Test fun fullArrangementsAndLoopBoundariesStayFiniteWithHeadroom() {
        for(index in 0..31) {
            val duration=60.0/Bosses.all[index].bpm*BattleScore.themes[index].beats*16
            for(frame in 0..(duration*22050).toInt() step 31) {
                val value=ScoreSynth.sample(frame/22050.0,"battle",index)
                assertTrue(value.isFinite()); assertTrue(abs(value)<1.0)
            }
            for(offset in listOf(-.00001,.0,.00001)) {
                assertTrue(ScoreSynth.sample(duration+offset,"battle",index).isFinite())
            }
        }
    }
    @Test fun titleOvertureHasDistinctSectionsAndCleanFullLoopHeadroom() {
        assertEquals(144,TitleScore.BPM)
        val signatures=mutableSetOf<Long>()
        var maximum=.0
        for(section in 0..7) {
            var energy=.0; var hash=7L
            for(frame in 0 until (TitleScore.SECONDS/8*22050).toInt() step 3) {
                val t=section*TitleScore.SECONDS/8+frame/22050.0
                val v=ScoreSynth.sample(t,"title",0)
                assertTrue(v.isFinite()); maximum=max(maximum,abs(v)); energy+=v*v
                hash=hash*31+(v*30000).roundToInt()
                assertEquals(v,ScoreSynth.sample(t,"title",31),.0)
            }
            assertTrue(energy>50); signatures.add(hash)
        }
        assertEquals(8,signatures.size); assertTrue(maximum in .25..0.75)
        for(i in -10..10) assertTrue(abs(TitleScore.sample(TitleScore.SECONDS+i/22050.0))<.75)
        assertNotEquals(ScoreSynth.sample(.123,"shop",0),ScoreSynth.sample(.123,"title",0))
    }
    @Test fun exportOriginalMusicPreviews() {
        val dir=File("../artifacts/music"); dir.mkdirs()
        for((name,scene,index) in listOf(Triple("title-overture","title",0),Triple("01-ratatoskr","battle",0),Triple("13-aegir","battle",12),Triple("18-hel","battle",17),Triple("30-loki","battle",29),Triple("31-thor","battle",30),Triple("32-odin","battle",31),Triple("ending","ending",31))) {
            val length=(ScoreSynth.SAMPLE_RATE*(if(scene=="title") TitleScore.SECONDS else 24.0)).toInt()
            val data=ByteBuffer.allocate(44+length*2).order(ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(36+length*2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
                .putInt(ScoreSynth.SAMPLE_RATE).putInt(ScoreSynth.SAMPLE_RATE*2).putShort(2).putShort(16).put("data".toByteArray()).putInt(length*2)
            repeat(length) { data.putShort((ScoreSynth.sample(it.toDouble()/ScoreSynth.SAMPLE_RATE,scene,index)*18000).toInt().toShort()) }
            File(dir,"$name.wav").writeBytes(data.array())
            assertEquals((44+length*2).toLong(),File(dir,"$name.wav").length())
        }
        val names=listOf("sword","knife","arrow","fire","ice","giant-hit","haniwa")
        for(name in names) {
            val clip=SoundEffects.clip(name)!!
            val length=clip.size
            val data=ByteBuffer.allocate(44+length*2).order(ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(36+length*2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
                .putInt(ScoreSynth.SAMPLE_RATE).putInt(ScoreSynth.SAMPLE_RATE*2).putShort(2).putShort(16).put("data".toByteArray()).putInt(length*2)
            clip.forEach { data.putShort((it*22000).toInt().toShort()) }
            File(dir,"se-$name.wav").writeBytes(data.array())
        }
    }
}
