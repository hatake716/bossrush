package io.github.hatake716.bossrush

import kotlin.math.*

enum class PlayerEffectKind { SWORD, KNIFE, HANIWA, GIANT, ARROW, FIRE, ICE, SHIELD, FOCUS, SPEED, STEAL, SUMMON_GIANT, SUMMON_RABBIT, SUMMON_HANIWA, HEAL, LIMIT_SLASH, LIMIT_FLARE, LIMIT_SUMMON, LIMIT_VANISH }

/** A snapshot of one successful action. Drawing an effect never applies damage. */
data class PlayerEffect(
    val kind: PlayerEffectKind, val x: Double, val y: Double, val radius: Double,
    val level: Int, val angle: Double=0.0, var age: Double=0.0
) {
    val strength: Double get()=Skills.progress(level)
    val lifetime: Double get()=when(kind) {
        PlayerEffectKind.SUMMON_GIANT,PlayerEffectKind.SUMMON_RABBIT,PlayerEffectKind.SUMMON_HANIWA,PlayerEffectKind.HEAL,PlayerEffectKind.LIMIT_SUMMON,PlayerEffectKind.LIMIT_VANISH -> .65+.25*strength
        PlayerEffectKind.LIMIT_SLASH -> .34+.14*strength
        PlayerEffectKind.LIMIT_FLARE -> .55+.15*strength
        else -> .30+.18*strength
    }
    val progress: Double get()=(age/lifetime).coerceIn(0.0,1.0)
    companion object { const val LIMIT=48 }
}

object PlayerAttackGeometry {
    const val BOSS_RADIUS=25.0
    /** First contact with a circle in relative-motion space, including fast crossings. */
    fun contactFraction(x: Double,y: Double,xx: Double,yy: Double,radius: Double): Double? {
        val c=x*x+y*y-radius*radius
        if(c<=0) return 0.0
        val dx=xx-x; val dy=yy-y; val a=dx*dx+dy*dy
        if(a<1e-12) return null
        val b=2*(x*dx+y*dy); val disc=b*b-4*a*c
        if(disc<0) return null
        val t=(-b-sqrt(disc))/(2*a)
        return t.takeIf { it in 0.0..1.0 }
    }
}
