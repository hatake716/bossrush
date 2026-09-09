package io.github.hatake716.bossrush

/** Attack energy briefly regains color; the world itself stays colorless until the ending. */
data class CombatPalette(val energy: Int,val accent: Int,val core: Int=0xfffff4df.toInt(),val shadow: Int=0xff130f2d.toInt())
object UltimateColors {
    private val pairs=listOf(
        "ratatoskr" to (0xff79fa57 to 0xffffb33d), "dainn" to (0xffaaff68 to 0xff38dab0),
        "gullinbursti" to (0xffffc329 to 0xffff603b), "hugin" to (0xff47d8ff to 0xff8563ff),
        "munin" to (0xffc688ff to 0xff489eff), "tanngrisnir" to (0xffffff38 to 0xff5998ff),
        "skoll" to (0xffff762c to 0xffffdd42), "hati" to (0xff8eb7ff to 0xffd97aff),
        "hraesvelgr" to (0xff68ffe5 to 0xff468dff), "thjazi" to (0xff87eaff to 0xffb459ff),
        "skadi" to (0xff47e6ff to 0xff5276ff), "ullr" to (0xff92ffe6 to 0xff2aa5ff),
        "aegir" to (0xff32caff to 0xff5275ff), "ran" to (0xff1cf6ce to 0xffac4eff),
        "njord" to (0xff66f8ff to 0xffffd76a), "jormungandr" to (0xffa3ff26 to 0xff25ceba),
        "garmr" to (0xffff3d70 to 0xff9f57ff), "hel" to (0xff63e6ff to 0xffff46c8),
        "nidhogg" to (0xffbd43ff to 0xff7cff3b), "fenrir" to (0xff8fb8ff to 0xffff5367),
        "hrungnir" to (0xffffb55e to 0xfff2693b), "thrym" to (0xff75eaff to 0xffdb87ff),
        "utgardloki" to (0xffc76bff to 0xff3bffdc), "surtr" to (0xffff4926 to 0xffffcd28),
        "idunn" to (0xff89ff58 to 0xffff759c), "freyr" to (0xffffd94a to 0xff78ef78),
        "freyja" to (0xffff70db to 0xffffd14b), "tyr" to (0xffff7771 to 0xff73cfff),
        "heimdall" to (0xff7bffff to 0xffff75dc), "loki" to (0xff4dffb8 to 0xffd449ff),
        "thor" to (0xffffe32e to 0xff3c91ff), "odin" to (0xffbc7cff to 0xff6cffff)
    )
    val all=pairs.associate { (id,pair) -> id to CombatPalette(pair.first.toInt(),pair.second.toInt()) }
    fun forBoss(id: String)=all.getValue(id)
}
