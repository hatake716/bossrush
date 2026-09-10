package io.github.hatake716.bossrush

import kotlin.math.*

/** Runtime catalog. Complete timed melodies live in tools/music/battle_scores.py. */
data class BattleTheme(val id: String, val character: String, val beats: Int = 4)

object BattleScore {
    val themes = listOf(
        BattleTheme("ratatoskr","枝跳びの高音スタッカートと、休符を挟む伝言の応酬"),
        BattleTheme("dainn","森に響く付点の角笛、長く歌う旋律と四つ打ちの蹄"),
        BattleTheme("gullinbursti","黄金のファンファーレと、短長短で突進するギャロップ"),
        BattleTheme("hugin","鋭い上方跳躍の問い、沈黙の後に返る低い答え"),
        BattleTheme("munin","記憶を辿る下行の歌、後半で追いかけるピアノの輪唱"),
        BattleTheme("tanngrisnir","二打ずつ跳ねる雷車の蹄、低音ユニゾンとタムの疾走"),
        BattleTheme("skoll","太陽を追う上行の反復、息継ぎの短い高速パンク"),
        BattleTheme("hati","月を巡る裏拍の長い下行旋律、重いハーフタイム"),
        BattleTheme("hraesvelgr","巨大な翼の二小節ロングトーン、広がる弦と開放ギター"),
        BattleTheme("thjazi","翼の急降下を描く連続下降、春を奪う低音の切り返し"),
        BattleTheme("skadi","氷を射抜く離れた高音、鋭い空白と細い結晶の連打"),
        BattleTheme("ullr","弓を引く溜めと矢の三連音、跳ねる弦のロック"),
        BattleTheme("aegir","宴の三連シャッフル、波を跨ぐ大きな上下の旋律"),
        BattleTheme("ran","半拍ずれで絡む網の旋律、沈降ベースと二声ピアノ"),
        BattleTheme("njord","帆を広げる上向きの長音、明快な四つ打ちと開放和音"),
        BattleTheme("jormungandr","同じ音へ巻き戻る蛇の円環、低域の反復と圧迫する刻み"),
        BattleTheme("garmr","三度吠えて途切れる低音、鎖を打つ裏拍のスネア"),
        BattleTheme("hel","高音の嘆きと低音の返答、長く伸ばす哀歌と葬送の鼓動"),
        BattleTheme("nidhogg","根を噛む細かい同音連打と、階段状に崩れる低い旋律"),
        BattleTheme("fenrir","鎖を切る全員休符、直後のオクターブ強打と重いブレイク"),
        BattleTheme("hrungnir","三角の石心を刻む3＋3＋2、低い三音の旋律"),
        BattleTheme("thrym","付点の婚礼行進から荒い酒宴へ、低音の霜王ファンファーレ"),
        BattleTheme("utgardloki","拍頭を逃げる幻術、3・5・7個の音群でずれるピアノ"),
        BattleTheme("surtr","高音まで駆け上がる炎の奔流、切れ目のないパワーメタル"),
        BattleTheme("idunn","芽吹く短い前打音と歌う長音、明るい跳ね方のピアノロック"),
        BattleTheme("freyr","自ら踊る剣の六連アルペジオ、鹿角の跳躍と軽快なリフ"),
        BattleTheme("freyja","黄金の涙を落とす装飾下降、歌うセイズと舞踏の裏拍"),
        BattleTheme("tyr","誓いの行進と四度の呼び声、片腕を示す一拍の空白"),
        BattleTheme("heimdall","ギャラルホルンの五度と付点三連の号令、虹橋の高音"),
        BattleTheme("loki","三拍子を二拍ずつ横切る仮面舞踏、旋律の反転と跳躍",3),
        BattleTheme("thor","九打の三連雷鎚、太いオクターブと二重キックの重圧"),
        BattleTheme("odin","槍の跳躍と長い叙事旋律、間奏で交わす二羽の鴉の応答")
    )
    fun chordAt(bar: Int): List<Int> = when(Math.floorMod(bar,4)) {
        0 -> listOf(8,12,15) // bVI major
        1 -> listOf(10,14,17) // bVII major
        else -> listOf(0,3,7) // i minor (two bars)
    }
}

internal object AudioMath {
    fun pulse(t: Double,f: Double,duty: Double)=if((t*f)%1<duty) 1.0-duty else -duty
    fun triangle(t: Double,f: Double)=1-4*abs((t*f)%1-.5)
    fun noise(time: Double): Double {
        var n=(time*22050).toLong()+0x9e3779b9L
        n=(n xor (n shr 16))*0x45d9f3bL; n=(n xor (n shr 16))*0x45d9f3bL
        return ((n xor (n shr 16)) and 65535)/32768.0-1
    }
}
