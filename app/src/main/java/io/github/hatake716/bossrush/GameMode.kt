package io.github.hatake716.bossrush

enum class GameMode(val label: String,val damageMultiplier: Int,val scoreMultiplier: Int) {
    NORMAL("通常",1,1), HARD("ハード",3,3)
}
