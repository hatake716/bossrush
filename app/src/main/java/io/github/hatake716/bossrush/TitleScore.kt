package io.github.hatake716.bossrush

import kotlin.math.*

/** Original overture “九つの世界への旅立ち”: fanfare, journey, open sky, return, cadence.
 * A 32-bar D-major march, written as notes and durations (eighth-note units).
 * Two pulse voices, triangle bass and noise percussion retain the handheld timbre.
 */
object TitleScore {
    const val BPM = 144
    const val BARS = 32
    const val SECONDS = BARS*4*60.0/BPM
    private const val EIGHTH = 30.0/BPM
    private data class Note(val start: Int,val length: Int,val frequency: Double)
    private fun frequency(midi: Int)=440*2.0.pow((midi-69)/12.0)
    private fun phrase(text: String): List<Note> {
        var start=0
        val pitches=mapOf("C" to 0,"D" to 2,"E" to 4,"F" to 5,"G" to 7,"A" to 9,"B" to 11)
        val notes=text.split(" ").map { token ->
            val (pitch,duration)=token.split(":"); val length=duration.toInt()
            val midi=if(pitch=="-") -1 else (pitch.last().digitToInt()+1)*12+pitches.getValue(pitch.take(1))+(if('#' in pitch) 1 else 0)
            Note(start,length,if(midi<0) .0 else frequency(midi)).also { start+=length }
        }
        require(start==8) { "Title bar must have four beats: $text" }
        return notes
    }
    private val melody=listOf(
        "D5:3 A4:1 D5:2 F#5:2", "A5:3 F#5:1 E5:2 D5:2",
        "G5:2 B5:1 A5:1 G5:2 E5:2", "C#5:2 E5:1 G5:1 A5:2 -:2",
        "F#5:2 E5:1 D5:1 A4:2 D5:2", "E5:3 F#5:1 G5:2 E5:2",
        "F#5:2 A5:2 B5:1 A5:1 F#5:2", "E5:4 C#5:2 -:2",
        "D5:2 F#5:1 A5:1 F#5:2 E5:2", "D5:2 B4:1 D5:1 G5:3 F#5:1",
        "E5:2 F#5:1 G5:1 A5:2 C#5:2", "D5:6 -:2",
        "B5:4 A5:2 G5:2", "A5:3 F#5:1 E5:2 F#5:2",
        "G5:4 E5:2 D5:2", "F#5:3 D5:1 B4:2 D5:2",
        "E5:2 G5:2 B5:3 A5:1", "F#5:2 A5:2 D6:2 C#6:2",
        "B5:2 G5:2 E5:2 G5:2", "A5:4 E5:2 -:2",
        "A5:2 F#5:1 E5:1 D5:2 F#5:2", "G5:3 A5:1 B5:2 G5:2",
        "F#5:2 A5:2 D6:1 C#6:1 B5:2", "A5:4 C#6:2 -:2",
        "D6:2 A5:1 F#5:1 A5:2 B5:2", "G5:2 B5:1 A5:1 G5:3 F#5:1",
        "E5:2 F#5:1 G5:1 A5:2 C#6:2", "D6:6 -:2",
        "B5:3 G5:1 D5:2 G5:2", "A5:3 F#5:1 D5:2 F#5:2",
        "E5:2 G5:1 A5:1 C#6:2 E5:2", "D5:6 -:2"
    ).map(::phrase)
    private val chordNames=listOf(
        "D","D","G","A7", "D","Em","Bm","A", "D","G","A7","D",
        "G","D","Em","Bm","Em","D","G","A7",
        "D","G","Bm","A","D","G","A7","D","G","D","A7","D"
    )
    private val chords=mapOf(
        "D" to intArrayOf(50,54,57,62), "G" to intArrayOf(43,47,50,55),
        "A" to intArrayOf(45,49,52,57), "A7" to intArrayOf(45,49,52,55),
        "Em" to intArrayOf(52,55,59,64), "Bm" to intArrayOf(47,50,54,59)
    )
    private val harmony=chordNames.map { name -> chords.getValue(name).map { frequency(it+12) }.toDoubleArray() }
    private val bass=chordNames.map { name -> val root=chords.getValue(name)[0]; doubleArrayOf(frequency(root-12),frequency(root-5)) }
    private val arpOrder=intArrayOf(0,2,1,2,3,2,1,2)
    fun sample(time: Double): Double {
        val t=time.coerceAtLeast(.0)%SECONDS
        val bar=(t/(EIGHTH*8)).toInt().coerceAtMost(BARS-1)
        val within=(t-bar*EIGHTH*8).coerceIn(.0,EIGHTH*8)
        val step=(within/EIGHTH).toInt().coerceAtMost(7)
        val note=melody[bar].last { it.start*EIGHTH<=within }
        val local=within-note.start*EIGHTH
        val duration=note.length*EIGHTH
        val lyrical=bar in 12..19
        val gate=duration*(if(lyrical) .96 else .87)
        val leadEnv=envelope(local,gate)*(.78+.22*exp(-local*18))
        val lead=if(note.frequency==.0) .0 else pulse(t,note.frequency,if(local<.045) .125 else .25)*leadEnv
        val arpLocal=within%EIGHTH
        val counter=pulse(t,harmony[bar][arpOrder[step]],.5)*envelope(arpLocal,EIGHTH*.8)
        val beat=(within/(EIGHTH*2)).toInt()
        val bassLocal=within%(EIGHTH*2)
        val low=triangle(t,bass[bar][beat%2])*envelope(bassLocal,EIGHTH*1.8)
        val sample=(time*ScoreSynth.SAMPLE_RATE).toLong()
        val bits=sample*1103515245L+12345L
        val noise=((bits xor(bits shr 11) xor(bits shl 7)) and 65535)/32768.0-1
        val roll=bar%4==3 && step>=6
        val drumLocal=if(roll) within%(EIGHTH/2) else bassLocal
        val snare=noise*exp(-drumLocal*(if(roll) 50 else 34))*(if(beat%2==1 || roll) .065 else .018)
        val dynamics=if(lyrical) .83 else if(bar>=20) 1.0 else .94
        return (lead*.34+counter*(if(lyrical) .065 else .09)+low*.17+snare)*dynamics*min(1.0,time.coerceAtLeast(.0)*80)
    }
    private fun envelope(t: Double,gate: Double)=if(t>=gate) .0 else min(1.0,t/.006)*min(1.0,(gate-t)/.022)
    private fun pulse(t: Double,f: Double,duty: Double)=if((t*f)%1<duty) 1-duty else -duty
    private fun triangle(t: Double,f: Double)=1-4*abs((t*f)%1-.5)
}
