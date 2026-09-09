package io.github.hatake716.bossrush

import kotlin.math.roundToInt

enum class Job(val label: String, val english: String, val role: String, val hp: Double, val speed: Double, val lore: String) {
    WARRIOR("戦士", "WARRIOR", "剣と盾の守り手", 150.0, 132.0, "強い剣と堅い盾。懐に飛び込んで戦う。"),
    MAGE("魔法使い", "MAGE", "炎と氷の詠み手", 105.0, 134.4, "直線の炎と追尾する氷で、離れて戦う。"),
    SUMMONER("召喚士", "SUMMONER", "小さな仲間と共に", 115.0, 129.6, "仲間は最大2体。召喚ゲージの回復は半分。"),
    THIEF("盗賊", "THIEF", "幸運を盗む旅人", 95.0, 142.8, "能力は控えめ。お金1.6倍と特別な戦利品。")
}

data class Skill(val name: String, val glyph: String, val power: Double, val cooldown: Double, val range: Double, val cost: Double, val description: String)

object Skills {
    private val rest = Skill("休む", "rest", 160.0, 10.0, 0.0, 0.0, "2秒間その場で休み、HPを回復。途中で動けない。")
    val all = mapOf(
        Job.WARRIOR to listOf(
            Skill("剣", "sword", 144.0, .95, 67.0, 16.0, "近くの敵を強く斬る。ボスの近くで使おう。"),
            Skill("盾", "shield", 0.0, 8.0, 0.0, 20.0, "4秒間、受けるダメージを軽減。"),
            Skill("弓", "bow", 44.0, 1.8, 650.0, 14.0, "ボスに向かって矢を飛ばす、弱い遠距離攻撃。"), rest),
        Job.MAGE to listOf(
            Skill("炎", "fire", 88.0, 1.15, 38.0, 19.0, "真っすぐ飛び、着弾地点を巻き込む範囲魔法。"),
            Skill("氷", "ice", 92.0, 1.65, 38.0, 22.0, "0.4秒追尾した地点に、氷を落とす。"),
            Skill("魔力を高める", "boost", 0.0, 9.0, 0.0, 18.0, "5秒間、攻撃力が上昇。炎と氷の前に使おう。"), rest),
        Job.SUMMONER to listOf(
            Skill("巨人を呼ぶ", "giant", 96.0, 3.0, 65.0, 60.0, "12秒間、強い拳でボスを攻撃する仲間。"),
            Skill("白ウサギを呼ぶ", "rabbit", 64.0, 3.0, 80.0, 45.0, "12秒間、2秒ごとに召喚士のHPを回復。"),
            Skill("はにわを呼ぶ", "haniwa", 64.0, 1.25, 68.0, 40.0, "正面攻撃を軽減。出現中に再タップで殴る。"), rest),
        Job.THIEF to listOf(
            Skill("ナイフ", "knife", 100.0, .9, 59.0, 15.0, "小さな刃で素早く斬る、中威力の近接攻撃。"),
            Skill("盗む", "steal", 0.0, 8.0, 77.0, 25.0, "近くで特殊アイテムを盗む。各ボス1回まで。"),
            Skill("気合いをいれる", "speed", 0.0, 8.0, 0.0, 16.0, "5秒間、移動速度が上昇。予兆を素早く回避。"), rest)
    )
    fun fraction(level: Int) = .25 + .05 * (level.coerceIn(1, 16) - 1)
    fun progress(level: Int) = (level.coerceIn(1, 16) - 1) / 15.0
    fun power(job: Job, slot: Int, level: Int) = all.getValue(job)[slot].power * fraction(level)
    fun cooldown(job: Job, slot: Int, level: Int) = all.getValue(job)[slot].cooldown * (1 - .45 * progress(level))
    fun range(job: Job, slot: Int, level: Int) = all.getValue(job)[slot].range * (1 + .6 * progress(level))
    fun arrowRadius(level: Int) = 6.0 * (1 + .6 * progress(level))
    fun auraRadius(level: Int) = 22.0 * (1 + .6 * progress(level))
    fun rangeDetail(job: Job, slot: Int, level: Int): String = when {
        job==Job.WARRIOR && slot==2 -> "矢幅 %.1f / 射程 %.0f".format(java.util.Locale.ROOT,arrowRadius(level)*2,range(job,slot,level))
        all.getValue(job)[slot].range>0 -> "${if(job==Job.SUMMONER && slot==1) "回復範囲" else "技範囲"} %.1f".format(java.util.Locale.ROOT,range(job,slot,level))
        else -> "自分に効果 / 光と紋章も成長"
    }
    fun detail(job: Job, slot: Int, level: Int): String {
        val skill = all.getValue(job)[slot]
        val powerText = if (skill.power > 0) "威力 ${power(job, slot, level).roundToInt()}   " else "効果 ${(fraction(level) * 100).roundToInt()}%   "
        return powerText + "待機 %.1f秒".format(java.util.Locale.ROOT, cooldown(job, slot, level))
    }
}

enum class Item(val title: String, val icon: String, val price: Int, val special: Boolean, val description: String) {
    POTION("薬草のしずく", "potion", 35, false, "HPを最大値の45%回復"),
    POWER("力の霊薬", "boost", 45, false, "8秒間、攻撃力が1.5倍"),
    ARMOR("石肌の霊薬", "shield", 40, false, "8秒間、被ダメージを半減"),
    HASTE("風の霊薬", "speed", 35, false, "8秒間、移動速度が1.5倍"),
    INVISIBLE("隠れ身のマント", "ghost", 0, true, "6秒間、透明になり無敵"),
    CLONES("三影の鏡", "clone", 0, true, "8秒間、本人と分身2人で攻撃"),
    FORTUNE("黄金の印", "coin", 0, true, "このボスの撃破報酬金が3倍"),
    HOURGLASS("時戻しの砂時計", "hourglass", 0, true, "全技の待機とゲージを即時回復")
}

enum class Pattern { CIRCLE, DONUT, CROSS, LANES, CONE, METEORS, SWEEP, KNOCKBACK, TOWERS, SPIRAL, CHASE, SIDES, WAVE, FRONTBACK, GRID, ECLIPSE }

data class Boss(
    val id: String, val name: String, val epithet: String, val form: String,
    val realm: String, val lore: String, val attacks: List<Pattern>,
    val attackNames: List<String>, val ultimate: String, val sequence: List<Pattern>,
    val hint: String, val theme: String, val bpm: Int, val root: Int
)

object Bosses {
    // Mythological traits are the source; encounter order, attacks and melodies are original adaptations.
    val all = listOf(
        Boss("ratatoskr", "ラタトスク", "世界樹を駆ける伝令", "squirrel", "根の森", "世界樹を走り、鷲と竜の言葉を運ぶリス。", listOf(Pattern.CIRCLE, Pattern.LANES), listOf("木の実の雨", "枝渡り"), "世界樹の大伝令", listOf(Pattern.LANES, Pattern.CIRCLE), "縦の予兆を離れ、次の円も避ける。", "枝先のいたずら", 204, 64),
        Boss("dainn", "ダーイン", "若葉を喰む四鹿", "stag", "根の森", "世界樹の枝葉を食む四頭の鹿の一頭。", listOf(Pattern.CONE, Pattern.CIRCLE), listOf("角の薙ぎ払い", "落葉の輪"), "四枝の奔流", listOf(Pattern.CROSS, Pattern.DONUT), "十字を避けた後、ボスの近くへ。", "翠枝の角笛", 192, 60),
        Boss("gullinbursti", "グリンブルスティ", "黄金のたてがみ", "boar", "根の森", "フレイの黄金の猪。輝く毛が闇を照らす。", listOf(Pattern.LANES, Pattern.CONE), listOf("黄金突進", "光の牙"), "金毛の夜明け", listOf(Pattern.LANES, Pattern.SWEEP), "突進の列を抜け、次の薙ぎ払いから離れる。", "黄金の蹄", 208, 62),
        Boss("hugin", "フギン", "思考を運ぶ翼", "raven", "根の森", "オーディンの鴉。世界を巡り知らせを運ぶ。", listOf(Pattern.METEORS, Pattern.CROSS), listOf("思考の羽", "黒翼の交差"), "思考の迷宮", listOf(Pattern.GRID, Pattern.CIRCLE), "格子の隙間へ。足元の記憶からも離れる。", "黒翼の問い", 212, 65),
        Boss("munin", "ムニン", "記憶を運ぶ翼", "raven", "霧の谷", "オーディンの鴉。名は記憶に結びつく。", listOf(Pattern.CHASE, Pattern.CONE), listOf("記憶の追跡", "忘却の翼"), "昨日の残像", listOf(Pattern.CHASE, Pattern.CHASE, Pattern.DONUT), "足跡の円を置いて移動。最後は中央へ。", "忘れられた空", 200, 57),
        Boss("tanngrisnir", "タングリスニ", "雷車を曳く山羊", "goat", "霧の谷", "トールの戦車を曳く二頭の山羊の一頭。", listOf(Pattern.CROSS, Pattern.CONE), listOf("蹄の雷", "角突き"), "雷車の轍", listOf(Pattern.CROSS, Pattern.LANES), "十字の外へ、続く雷の轍も踏まない。", "雷雲の蹄音", 216, 60),
        Boss("skoll", "スコル", "太陽を追う狼", "wolf", "霧の谷", "空を走る太陽を追いかける狼。", listOf(Pattern.CONE, Pattern.SWEEP), listOf("陽炎の牙", "日輪狩り"), "太陽を呑む顎", listOf(Pattern.ECLIPSE, Pattern.CIRCLE), "月印の小さな安全地帯へ。直後に離れる。", "日蝕の疾走", 224, 62),
        Boss("hati", "ハティ", "月を追う狼", "wolf", "霧の谷", "天を駆ける月を追跡する狼。", listOf(Pattern.DONUT, Pattern.CHASE), listOf("月影の輪", "夜の追跡"), "月を喰らう夜", listOf(Pattern.ECLIPSE, Pattern.DONUT), "月印へ駆け込み、続いてボスの近くへ。", "銀月の追走", 220, 59),
        Boss("hraesvelgr", "フレースヴェルグ", "風を生む大鷲", "eagle", "氷の峰", "世界の北端で、翼から風を起こす巨人。", listOf(Pattern.KNOCKBACK, Pattern.LANES), listOf("北風の翼", "風の裂け目"), "世界の果ての風", listOf(Pattern.KNOCKBACK, Pattern.SIDES), "吹き飛ばし前に中央へ。次も中央の帯へ。", "北端の風鳴り", 200, 55),
        Boss("thjazi", "スィアチ", "鷲に化ける巨人", "eagle", "氷の峰", "鷲の姿でイズンと若返りの林檎を連れ去った巨人。", listOf(Pattern.CONE, Pattern.METEORS), listOf("鷲の急襲", "凍てつく羽"), "林檎を奪う翼", listOf(Pattern.TOWERS, Pattern.SWEEP), "林檎の印へ入って受け止め、薙ぎ払いを回避。", "奪われた春", 208, 57),
        Boss("skadi", "スカジ", "雪山の狩人", "archer", "氷の峰", "山と狩猟に結びつき、スキーと弓を使う女神。", listOf(Pattern.LANES, Pattern.CHASE), listOf("雪の矢列", "狙撃"), "白銀の狩場", listOf(Pattern.LANES, Pattern.GRID, Pattern.CIRCLE), "矢列から格子の隙間へ。最後の狙撃を誘導。", "白銀を射る", 216, 66),
        Boss("ullr", "ウル", "誓いの射手", "archer", "氷の峰", "弓とスキーに秀でた神。盾とも結びつく。", listOf(Pattern.CROSS, Pattern.CONE), listOf("交差する矢", "盾の射線"), "イチイの誓約", listOf(Pattern.FRONTBACK, Pattern.LANES), "前、後ろの順に薙ぐ。次の矢列にも注意。", "イチイの弦", 204, 64),
        Boss("aegir", "エーギル", "海の宴の主", "sea", "深い海", "神々を宴に招く、海と結びつく存在。", listOf(Pattern.WAVE, Pattern.DONUT), listOf("潮の宴", "渦潮"), "九つの波の杯", listOf(Pattern.WAVE, Pattern.WAVE, Pattern.DONUT), "順番に来る波を越え、最後は渦の内へ。", "荒波の祝宴", 196, 50),
        Boss("ran", "ラーン", "網を投げる海の女主人", "sea", "深い海", "網で海に落ちた者を捕らえる、エーギルの妻。", listOf(Pattern.GRID, Pattern.CIRCLE), listOf("海底の網", "溺れる輪"), "帰らずの網", listOf(Pattern.GRID, Pattern.KNOCKBACK, Pattern.CIRCLE), "網の隙間、中央、円の外へと順に移る。", "深海の糸", 208, 53),
        Boss("njord", "ニョルズ", "風と海の守り手", "king", "深い海", "風と海を治め、航海や富と結びつく神。", listOf(Pattern.KNOCKBACK, Pattern.WAVE), listOf("順風の試練", "航路の潮"), "ノーアトゥーンの嵐", listOf(Pattern.KNOCKBACK, Pattern.WAVE, Pattern.TOWERS), "中央で風を受け、波の後に港の印へ。", "帰港なき帆", 200, 60),
        Boss("jormungandr", "ヨルムンガンド", "世界を囲む大蛇", "serpent", "深い海", "海で世界を取り囲み、自らの尾をくわえる大蛇。", listOf(Pattern.DONUT, Pattern.CONE), listOf("世界蛇の環", "毒の息"), "ミズガルズの終潮", listOf(Pattern.DONUT, Pattern.CIRCLE, Pattern.WAVE), "蛇の輪の内、円の外、潮の隙間の順。", "世界を締める環", 224, 48),
        Boss("garmr", "ガルム", "冥界の番犬", "wolf", "死者の国", "グニパヘリルに結びつく犬。終末にテュールと戦う。", listOf(Pattern.CONE, Pattern.CHASE), listOf("冥府の咆哮", "血の足跡"), "グニパヘリルの解放", listOf(Pattern.FRONTBACK, Pattern.CHASE, Pattern.CROSS), "前後の牙、追跡の円、十字を順番に避ける。", "鎖の向こう", 220, 52),
        Boss("hel", "ヘル", "生と死の境界", "queen", "死者の国", "半身が暗く、半身が肌色と描かれる死者の国の支配者。", listOf(Pattern.SIDES, Pattern.SWEEP), listOf("生死の境", "冷たい抱擁"), "二つの顔の審判", listOf(Pattern.SIDES, Pattern.ECLIPSE, Pattern.SWEEP), "中央の帯、月印、明るい半面を見極める。", "半分だけの心臓", 192, 57),
        Boss("nidhogg", "ニーズヘッグ", "根を齧る竜", "dragon", "死者の国", "世界樹の根を齧る竜。死者とも結びつく。", listOf(Pattern.CONE, Pattern.GRID), listOf("根腐れの息", "黒根の侵食"), "世界樹の崩落", listOf(Pattern.GRID, Pattern.METEORS, Pattern.DONUT), "根の隙間へ、落石を離れ、最後は中央。", "朽ちる根の歌", 224, 49),
        Boss("fenrir", "フェンリル", "縛めを断つ狼", "wolf", "死者の国", "グレイプニルに縛られ、終末にオーディンを呑む狼。", listOf(Pattern.FRONTBACK, Pattern.CIRCLE), listOf("双顎", "鎖の破片"), "グレイプニル断裂", listOf(Pattern.CROSS, Pattern.KNOCKBACK, Pattern.ECLIPSE), "鎖の十字を抜け、風に備え、月印へ走る。", "解き放たれた顎", 228, 50),
        Boss("hrungnir", "フルングニル", "石の心臓", "giant", "巨人の炉", "石の頭と心臓を持ち、砥石を武器にトールと戦った巨人。", listOf(Pattern.CIRCLE, Pattern.CROSS), listOf("砥石砕き", "石心の地割れ"), "三角の石心", listOf(Pattern.METEORS, Pattern.TOWERS, Pattern.CIRCLE), "岩を避け、印を踏み、足元の破砕から退く。", "三角の鼓動", 208, 48),
        Boss("thrym", "スリュム", "鎚を隠す霜の王", "giant", "巨人の炉", "ミョルニルを隠し、フレイヤとの結婚を要求した巨人。", listOf(Pattern.LANES, Pattern.SIDES), listOf("霜の大槌", "氷壁"), "奪われたミョルニル", listOf(Pattern.CROSS, Pattern.SIDES, Pattern.CROSS), "十字、中央の帯、もう一度十字の外へ。", "霜王の婚礼", 204, 51),
        Boss("utgardloki", "ウートガルザロキ", "幻術の城主", "mage", "巨人の炉", "トール一行を、炎・思考・老いなどの幻の試練で欺く。", listOf(Pattern.ECLIPSE, Pattern.FRONTBACK), listOf("幻の試練", "虚像の手"), "ウートガルズの幻城", listOf(Pattern.ECLIPSE, Pattern.GRID, Pattern.ECLIPSE), "月印の位置は毎回変わる。次の印を見直す。", "偽りの広間", 224, 61),
        Boss("surtr", "スルト", "炎の国の剣", "firegiant", "巨人の炉", "炎の剣を持ち、終末に世界を焼く巨人。", listOf(Pattern.SWEEP, Pattern.METEORS), listOf("炎剣の薙ぎ", "火の雨"), "ムスペルの終焔", listOf(Pattern.SWEEP, Pattern.SWEEP, Pattern.DONUT, Pattern.CIRCLE), "左右の炎剣、内側、外側の順に切り返す。", "燃え尽きる天", 232, 52),
        Boss("idunn", "イズン", "若返りの林檎", "queen", "黄金の庭", "神々の若さを保つ林檎を守る女神。", listOf(Pattern.TOWERS, Pattern.CIRCLE), listOf("林檎の試練", "若葉の波紋"), "永遠の果樹園", listOf(Pattern.TOWERS, Pattern.CIRCLE, Pattern.TOWERS), "林檎の印、円の外、次の林檎の印へ。", "春を守る戦い", 204, 67),
        Boss("freyr", "フレイ", "豊穣と光の主", "king", "黄金の庭", "豊穣と平和の神。自ら戦う剣を手放した逸話を持つ。", listOf(Pattern.CONE, Pattern.SPIRAL), listOf("鹿角の一撃", "豊穣の光"), "アルヴヘイムの輝き", listOf(Pattern.SPIRAL, Pattern.TOWERS, Pattern.DONUT), "回る光を避け、印へ入り、最後は中央へ。", "実りの剣舞", 216, 62),
        Boss("freyja", "フレイヤ", "黄金と魔術の女神", "queen", "黄金の庭", "愛や戦い、セイズの魔術に結びつき、猫の車を持つ。", listOf(Pattern.CHASE, Pattern.SPIRAL), listOf("猫の追跡", "セイズの輪"), "ブリーシンガメンの星", listOf(Pattern.SPIRAL, Pattern.ECLIPSE, Pattern.CHASE), "星の列を抜け、月印へ。最後は足を止めない。", "黄金の涙", 220, 65),
        Boss("tyr", "テュール", "誓いの片腕", "knight", "黄金の庭", "狼を縛る誓いのために片手を失った、勇敢な神。", listOf(Pattern.FRONTBACK, Pattern.CONE), listOf("片腕の剣", "誓いの切先"), "失われた右手の誓い", listOf(Pattern.FRONTBACK, Pattern.TOWERS, Pattern.CROSS), "前後の剣を避け、誓いの印を踏み、十字外へ。", "隻腕の誓約", 216, 60),
        Boss("heimdall", "ヘイムダル", "虹の橋の番人", "knight", "神々の門", "鋭い感覚でビフレストを守り、角笛で終末を告げる神。", listOf(Pattern.LANES, Pattern.DONUT), listOf("虹橋の光線", "角笛の波紋"), "ギャラルホルン", listOf(Pattern.WAVE, Pattern.LANES, Pattern.ECLIPSE), "音の波、橋の光線、月印の順に走り抜ける。", "終わりを告げる角笛", 224, 64),
        Boss("loki", "ロキ", "形を変える策略家", "mage", "神々の門", "さまざまな姿を取り、神々に益も災いももたらす存在。", listOf(Pattern.ECLIPSE, Pattern.CHASE), listOf("変わり身", "策略の足跡"), "千の顔のラグナロク", listOf(Pattern.ECLIPSE, Pattern.FRONTBACK, Pattern.GRID, Pattern.ECLIPSE), "変わる月印を確認。前後、格子、最後の月印。", "千の顔のワルツ", 228, 63),
        Boss("thor", "トール", "轟くミョルニル", "hammer", "神々の門", "雷神。ミョルニルを振るい、世界蛇と戦う。", listOf(Pattern.CROSS, Pattern.KNOCKBACK, Pattern.CIRCLE), listOf("ミョルニル", "雷鳴", "落雷"), "九歩の雷轟", listOf(Pattern.CROSS, Pattern.KNOCKBACK, Pattern.CROSS, Pattern.DONUT), "雷の十字、中央で構え、十字外、鎚の内へ。", "九歩の雷鳴", 232, 50),
        Boss("odin", "オーディン", "隻眼の万物の父", "odin", "神々の門", "知恵を求める隻眼の神。槍グングニルと二羽の鴉を伴う。", listOf(Pattern.LANES, Pattern.SPIRAL, Pattern.TOWERS), listOf("グングニル", "二羽の鴉", "ルーンの試練"), "終わりから始まる世界", listOf(Pattern.GRID, Pattern.ECLIPSE, Pattern.CROSS, Pattern.TOWERS, Pattern.DONUT), "格子、月印、十字外、ルーン、中央。すべての記憶を。", "隻眼の夜明け", 228, 57)
    )
}
