package io.github.hatake716.bossrush

import kotlin.math.*

/** Consecutive successful uses advance each weapon independently; menu time never counts. */
class PlayerCombos {
    private val stages=IntArray(3)
    private val deadlines=DoubleArray(3)
    fun next(job: Job,slot: Int,time: Double): Int {
        if(!supports(job,slot)) return 1
        return if(time>deadlines[slot]) 1 else stages[slot]%3+1
    }
    fun commit(job: Job,slot: Int,time: Double,cooldown: Double): Int {
        val next=next(job,slot,time)
        if(supports(job,slot)) { stages[slot]=next; deadlines[slot]=time+cooldown+2.4 }
        return next
    }
    fun reset() { stages.fill(0); deadlines.fill(0.0) }
    companion object {
        fun supports(job: Job,slot: Int)=when(job) {
            Job.WARRIOR,Job.THIEF -> slot==0
            Job.MAGE -> slot in 0..1
            else -> false
        }
        fun multiplier(stage: Int)=when(stage) { 2 -> 1.15; 3 -> 1.35; else -> 1.0 }
    }
}

object PlayerMagic {
    const val PROJECTILE_LIMIT=128
    /** Lv.1: 1/3/5 shots; Lv.16: 11/13/15. Power is shared, not multiplied by count. */
    fun count(level: Int,stage: Int)=1+2*((level.coerceIn(1,16)-1)/3)+2*(stage.coerceIn(1,3)-1)
    fun fireSpread(level: Int,stage: Int)=.12+.22*Skills.progress(level)+.08*(stage-1)
    fun iceOrbit(level: Int)=100.0+45*Skills.progress(level)
}

/** A body lunge, fist extension and recoil, expressed in battle time for pause/cut-in safety. */
object SummonPunch {
    const val DURATION=.46
    fun extension(age: Double): Double {
        if(age !in 0.0..DURATION) return 0.0
        val u=age/DURATION
        return if(u<.28) sin(u/.28*PI/2) else (1-(u-.28)/.72).pow(2)
    }
}
