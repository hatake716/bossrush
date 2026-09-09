package io.github.hatake716.bossrush

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.accessibility.*
import kotlin.math.*

data class UiButton(val label: String,val rect: RectF,val enabled: Boolean=true,val skill: Int=-1,val action: () -> Unit)

class GameView(context: Context,val engine: GameEngine): View(context), Choreographer.FrameCallback {
    val audio=Chiptune()
    var hasSave=false
    var bestScore=0
    var onContinue: (() -> Unit)?=null
    var onSoundChanged: ((Boolean) -> Unit)?=null
    private val art=PixelArt()
    private val p=Paint().apply { isAntiAlias=false }
    private val type=Paint().apply { isAntiAlias=true; typeface=Typeface.create("sans-serif",Typeface.NORMAL) }
    private val buttons=mutableListOf<UiButton>()
    private var scale=1f; private var ox=0f; private var oy=0f
    private var lastFrame=0L; private var running=false
    private var clock=0.0
    private var lastScreen=Screen.TITLE
    private var joystickId=-1; private var stickX=0f; private var stickY=0f
    private var skillPointer=-1
    private val keySet=mutableSetOf<Int>()
    private var pressed: String?=null
    private var codexPage=0
    private var returnScreen=Screen.TITLE
    private var focusId=View.NO_ID
    private var hoveredId=View.NO_ID
    init {
        isFocusable=true; isFocusableInTouchMode=true; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription="BOSSRUSH タイトル"
    }
    fun resume() { if(!running) { running=true; lastFrame=0; audio.start(); Choreographer.getInstance().postFrameCallback(this) } }
    fun suspend() { running=false; engine.pause(); resetInput(); audio.stop(); Choreographer.getInstance().removeFrameCallback(this) }
    override fun doFrame(frameTimeNanos: Long) {
        if(!running) return
        val dt=if(lastFrame==0L) .0 else (frameTimeNanos-lastFrame)/1e9
        lastFrame=frameTimeNanos; clock+=dt.coerceAtMost(.05)
        engine.update(dt)
        val scene=when(engine.screen) { Screen.BATTLE,Screen.CUTIN,Screen.INTRO,Screen.PAUSED -> "battle"; Screen.SHOP,Screen.REWARD -> "shop"; Screen.ENDING -> "ending"; else -> "title" }
        audio.music(scene,engine.run?.stage ?: 0)
        engine.sounds.toList().forEach { audio.effect(it) }; engine.sounds.clear()
        if(lastScreen!=engine.screen) {
            lastScreen=engine.screen; resetInput(); contentDescription="BOSSRUSH ${screenName()}"
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        }
        invalidate()
        // Battle tracks display vsync. Menus leave idle time for Android lifecycle,
        // accessibility and input dispatch, and need far fewer redraws.
        val delay=when(engine.screen) { Screen.BATTLE,Screen.CUTIN -> 0L; Screen.TITLE,Screen.ENDING -> 33L; else -> 70L }
        Choreographer.getInstance().postFrameCallbackDelayed(this,delay)
    }
    fun screenName()=when(engine.screen) {
        Screen.TITLE -> "タイトル"; Screen.JOBS -> "職業選択"; Screen.INTRO -> "ボス紹介 ${engine.bossInfo.name}"
        Screen.BATTLE -> "戦闘 ${engine.bossInfo.name}"; Screen.CUTIN -> "必殺技 ${engine.bossInfo.ultimate}"
        Screen.REWARD -> "ボス撃破 技の成長"; Screen.SHOP -> "ショップ"; Screen.PAUSED -> "一時停止"
        Screen.GAMEOVER -> "ゲームオーバー"; Screen.ENDING -> "ゲームクリア"; Screen.CODEX -> "神話図鑑"; Screen.HELP -> "遊び方"
    }
    private fun resetInput() { joystickId=-1; skillPointer=-1; pressed=null; stickX=0f; stickY=0f; engine.moveX=.0; engine.moveY=.0; engine.heldSkill=-1; keySet.clear() }
    fun goBack() {
        when(engine.screen) {
            Screen.BATTLE,Screen.CUTIN -> engine.pause()
            Screen.PAUSED -> engine.unpause()
            Screen.CODEX,Screen.HELP -> engine.changeScreen(returnScreen)
            Screen.JOBS,Screen.INTRO,Screen.SHOP,Screen.GAMEOVER,Screen.ENDING -> engine.changeScreen(Screen.TITLE)
            Screen.REWARD -> Unit
            else -> (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }
    private fun rect(c: Canvas,x: Float,y: Float,w: Float,h: Float,color: Int) { p.color=color; p.style=Paint.Style.FILL; c.drawRect(x,y,x+w,y+h,p) }
    private fun border(c: Canvas,x: Float,y: Float,w: Float,h: Float,color: Int=Ink.mid,thick: Float=2f) {
        rect(c,x,y,w,thick,color); rect(c,x,y+h-thick,w,thick,color); rect(c,x,y,thick,h,color); rect(c,x+w-thick,y,thick,h,color)
    }
    private fun text(c: Canvas,s: String,x: Float,y: Float,size: Float=15f,color: Int=Ink.light,align: Paint.Align=Paint.Align.LEFT) {
        type.textSize=size; type.color=color; type.textAlign=align; c.drawText(s,x,y,type)
    }
    private fun wrap(c: Canvas,s: String,x: Float,y: Float,width: Float,size: Float=14f,color: Int=Ink.light,line: Float=23f): Float {
        type.textSize=size
        var row=""; var yy=y
        for(ch in s) {
            if(ch=='\n' || type.measureText(row+ch)>width) { text(c,row,x,yy,size,color); yy+=line; row=if(ch=='\n') "" else "$ch" } else row+=ch
        }
        if(row.isNotEmpty()) text(c,row,x,yy,size,color)
        return yy+line
    }
    private fun pixel(c: Canvas,s: String,x: Float,y: Float,size: Float=2f,color: Int=Ink.light,center: Boolean=false)=PixelFont.draw(c,s,x,y,size,color,center)
    private fun button(c: Canvas,label: String,x: Float,y: Float,w: Float,h: Float,primary: Boolean=false,enabled: Boolean=true,skill: Int=-1,action: () -> Unit) {
        val over=pressed==label
        val fill=if(enabled&&(primary||over)) Ink.light else Ink.deep
        rect(c,x+3,y+4,w,h,Ink.dark); rect(c,x,y,w,h,fill)
        border(c,x,y,w,h,if(enabled) Ink.mid else Ink.deep)
        text(c,label,x+w/2,y+h/2+5,15f,if(!enabled) Ink.mid else if(primary||over) Ink.dark else Ink.light,Paint.Align.CENTER)
        buttons.add(UiButton(label,RectF(x,y,x+w,y+h),enabled,skill,action))
    }
    private fun header(c: Canvas,section: String,back: Boolean=true) {
        rect(c,0f,0f,960f,57f,Ink.dark); rect(c,24f,55f,912f,1f,Ink.mid)
        art.icon(c,"sword",28f,16f,1.6f); pixel(c,"BOSSRUSH",64f,19f,2.4f)
        pixel(c,section,300f,22f,1.5f,Ink.mid)
        button(c,if(audio.enabled) "♪ ON" else "♪ OFF",803f,12f,66f,31f) { audio.enabled=!audio.enabled; onSoundChanged?.invoke(audio.enabled) }
        if(back) button(c,"戻る",880f,12f,57f,31f) { goBack() }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        scale=min(width/960f,height/540f); ox=(width-960*scale)/2; oy=(height-540*scale)/2
        canvas.drawColor(Ink.dark); canvas.save(); canvas.translate(ox,oy); canvas.scale(scale,scale); canvas.clipRect(0f,0f,960f,540f)
        buttons.clear()
        when(engine.screen) {
            Screen.TITLE -> title(canvas)
            Screen.JOBS -> jobs(canvas)
            Screen.INTRO -> intro(canvas)
            Screen.BATTLE,Screen.CUTIN,Screen.PAUSED -> { battle(canvas); if(engine.screen==Screen.CUTIN) cutin(canvas); if(engine.screen==Screen.PAUSED) paused(canvas) }
            Screen.REWARD -> reward(canvas)
            Screen.SHOP -> shop(canvas)
            Screen.CODEX -> codex(canvas)
            Screen.HELP -> help(canvas)
            Screen.GAMEOVER -> result(canvas,false)
            Screen.ENDING -> result(canvas,true)
        }
        canvas.restore()
    }
    private fun landscape(c: Canvas,color: Boolean=false) {
        val pal=if(color) Ink.dawn else Ink.palette
        rect(c,0f,57f,960f,483f,pal[0])
        for(i in 0..65) {
            val x=(i*137%960).toFloat(); val y=(75+i*71%230).toFloat()
            rect(c,x,y,if(i%8==0) 3f else 2f,2f,if(i%3==0) pal[2] else pal[1])
        }
        p.color=pal[2]; c.drawCircle(698f,161f,50f,p); p.color=pal[0]; c.drawCircle(680f,149f,46f,p)
        for(i in 0..16) {
            val h=(40+(i*47%80)).toFloat()
            rect(c,i*64f,362-h,67f,h+85,pal[1]); rect(c,i*64f+16,348-h,34f,30f,pal[1])
        }
        for(i in 0..9) { rect(c,i*111f,413f+(i%3)*5,100f,4f,pal[2]); rect(c,i*111f+8,454f+(i%2)*18,85f,3f,pal[1]) }
        art.tree(c,698f,422f,1.7f,color)
        // Broken stone arch and its runes.
        for(i in 0..6) { rect(c,516f,280f+i*19,21f,17f,pal[2]); rect(c,845f,280f+i*19,21f,17f,pal[2]) }
        for(i in 0..5) { rect(c,532f+i*22,264f-i*7,22f,17f,pal[1]); rect(c,729f+i*22,229f+i*7,22f,17f,pal[1]) }
        for(i in 0..8) rect(c,613f+i*19,464f,14f,3f,pal[2])
        art.sprite(c,if(color) art.heroKey(engine.job) else "warrior",690f,442f,1.45f,color=color)
        for(i in 0..13) {
            val xx=(520+i*37%357).toFloat(); val yy=(300+(i*31+clock*9)%150).toFloat()
            rect(c,xx,yy,3f,3f,if(color && i%3==0) Color.rgb(230,164,111) else pal[2])
        }
    }
    private fun title(c: Canvas) {
        landscape(c); header(c,"THE COLORLESS SAGA",false)
        pixel(c,"A POCKET-SIZED RAID ADVENTURE",48f,101f,1.35f,Ink.mid)
        pixel(c,"BOSS",47f,141f,9f,Ink.mid); pixel(c,"BOSS",44f,137f,9f)
        pixel(c,"RUSH",47f,220f,9f,Ink.mid); pixel(c,"RUSH",44f,216f,9f)
        text(c,"神々を越えて、色を取り戻せ。",48f,313f,20f)
        text(c,"4つの職業。32の試練。ひとつの夜明け。",49f,344f,14f,Ink.mid)
        button(c,"はじめから  →",48f,372f,212f,49f,true) {
            if(hasSave) AlertDialog.Builder(context).setTitle("新しい冒険を始めますか？").setMessage("職業を選んで出発すると、今の冒険の保存データが置き換わります。最高スコアは残ります。")
                .setPositiveButton("職業を選ぶ") { _,_ -> engine.changeScreen(Screen.JOBS) }.setNegativeButton("戻る",null).show()
            else engine.changeScreen(Screen.JOBS)
        }
        button(c,"つづきから",274f,372f,173f,49f,enabled=hasSave) { onContinue?.invoke() }
        button(c,"遊び方",48f,438f,123f,36f) { returnScreen=Screen.TITLE; engine.changeScreen(Screen.HELP) }
        button(c,"神話図鑑",185f,438f,123f,36f) { returnScreen=Screen.TITLE; engine.changeScreen(Screen.CODEX) }
        pixel(c,"BEST ${bestScore.toString().padStart(6,'0')}",48f,502f,1.65f,Ink.mid)
        pixel(c,"32 GODS / 4 HEROES / 1 DAWN",649f,505f,1.2f,Ink.mid)
    }
    private fun jobs(c: Canvas) {
        header(c,"CHOOSE YOUR HERO")
        pixel(c,"WHO WILL FACE THE GODS?",36f,83f,2.4f)
        text(c,"色を失った世界に、小さな勇者が立ち上がる。",36f,126f,15f,Ink.mid)
        Job.entries.forEachIndexed { i,job ->
            val x=36f+i*226; val selected=engine.selectedJob==job
            rect(c,x,149f,211f,243f,if(selected) Ink.deep else Ink.dark); border(c,x,149f,211f,243f,if(selected) Ink.light else Ink.mid)
            pixel(c,"0${i+1}",x+14,163f,1.5f,Ink.mid)
            art.sprite(c,art.heroKey(job),x+106,264f,2.7f)
            text(c,job.label,x+106,293f,23f,Ink.light,Paint.Align.CENTER)
            pixel(c,job.english,x+106,308f,1.5f,Ink.mid,true)
            text(c,job.role,x+106,349f,14f,Ink.light,Paint.Align.CENTER)
            text(c,"HP ${job.hp.toInt()}  /  ${if(job==Job.THIEF) "報酬 160%" else if(job==Job.SUMMONER) "仲間 2体" else "ゲージ 20/s"}",x+106,376f,12f,Ink.mid,Paint.Align.CENTER)
            buttons.add(UiButton("${job.label}を選択",RectF(x,149f,x+211,392f)) { engine.selectedJob=job })
        }
        val j=engine.selectedJob
        text(c,j.lore,37f,424f,16f)
        text(c,Skills.all.getValue(j).joinToString("  /  ") { it.name },37f,453f,14f,Ink.mid)
        button(c,"この職業で出発  →",673f,457f,251f,51f,true) { engine.newRun() }
        pixel(c,"LEVEL 1 - 16",37f,496f,1.5f,Ink.mid)
    }
    private fun intro(c: Canvas) {
        val b=engine.bossInfo; val stage=engine.run!!.stage
        header(c,"THE NEXT ENCOUNTER")
        rect(c,36f,79f,355f,416f,Ink.deep); border(c,36f,79f,355f,416f)
        pixel(c,"ENCOUNTER ${(stage+1).toString().padStart(2,'0')} / 32",60f,103f,1.8f)
        for(i in 0..5) border(c,80f+i*10,166f+i*10,260f-i*20,235f-i*20,Ink.mid,1f)
        art.sprite(c,b.form,214f,366f,4.4f,stage)
        text(c,b.realm,214f,447f,19f,Ink.light,Paint.Align.CENTER)
        pixel(c,"${b.bpm} BPM",214f,467f,1.2f,Ink.mid,true)
        text(c,b.epithet,431f,108f,15f,Ink.mid)
        text(c,b.name,428f,153f,34f)
        wrap(c,b.lore,431f,195f,460f,16f)
        rect(c,431f,237f,491f,126f,Ink.deep)
        pixel(c,"LIMIT BREAK",450f,252f,1.4f,Ink.mid)
        text(c,b.ultimate,450f,294f,20f)
        wrap(c,b.hint,450f,325f,445f,14f)
        text(c,"HPが残り1/3で発動。カットインの後に連続攻撃。",431f,390f,14f,Ink.mid)
        text(c,"全回復して挑戦  /  アイテム ${engine.run!!.inventory.size}/5",431f,424f,15f)
        button(c,"戦闘開始  →",665f,454f,259f,51f,true) { engine.beginBattle() }
    }
    private fun bar(c: Canvas,x: Float,y: Float,w: Float,h: Float,value: Double,maxValue: Double,color: Int=Ink.light) {
        rect(c,x,y,w,h,Ink.dark); border(c,x,y,w,h,Ink.mid,1f)
        rect(c,x+3,y+3,((w-6)*(value/maxValue).coerceIn(.0,1.0)).toFloat(),h-6,color)
    }
    private fun battle(c: Canvas) {
        val e=engine; val r=e.run!!; val b=e.bossInfo
        header(c,"${(r.stage+1).toString().padStart(2,'0')} / 32   ${b.id.uppercase()}",false)
        button(c,"II",880f,12f,57f,31f) { e.pause() }
        text(c,b.name,28f,80f,19f)
        bar(c,225f,65f,307f,16f,e.boss.hp,e.boss.maxHp)
        pixel(c,"${ceil(e.boss.hp/e.boss.maxHp*100).toInt()}%",543f,69f,1.35f)
        rect(c,28f,96f,600f,334f,Ink.deep); border(c,25f,93f,606f,340f)
        c.save(); c.translate(28f,96f); c.clipRect(0f,0f,600f,334f)
        for(y in 0..10) for(x in 0..18) {
            val xx=x*36f+(if(y%2==0) 0 else -18); val yy=y*32f
            border(c,xx,yy,35f,31f,Ink.dark,1f)
            if((x*7+y*11)%13==0) { rect(c,xx+5,yy+5,4f,2f,Ink.mid); rect(c,xx+9,yy+7,2f,5f,Ink.dark) }
        }
        p.color=Ink.mid; p.style=Paint.Style.STROKE; p.strokeWidth=1f
        c.drawOval(80f,12f,520f,327f,p); c.drawOval(92f,20f,508f,319f,p); p.style=Paint.Style.FILL
        for(i in 0..7) {
            val a=i*PI/4; val x=300+cos(a)*215; val y=170+sin(a)*146
            pixel(c,"${i+1}",x.toFloat(),y.toFloat(),1.4f,Ink.mid,true)
        }
        e.hazards.forEach { drawHazard(c,it) }
        e.iceMarks.forEach { m ->
            p.style=Paint.Style.STROKE; p.strokeWidth=2f; p.color=Ink.light
            c.drawCircle(m.x.toFloat(),m.y.toFloat(),m.radius.toFloat(),p); p.style=Paint.Style.FILL
            art.icon(c,"ice",m.x.toFloat()-8,m.y.toFloat()-8,1f)
        }
        p.color=Ink.dark; c.drawOval(e.boss.x.toFloat()-46,e.boss.y.toFloat()-2,e.boss.x.toFloat()+46,e.boss.y.toFloat()+18,p)
        art.sprite(c,b.form,e.boss.x.toFloat(),e.boss.y.toFloat()+sin(clock*3).toFloat()*2,2.25f,r.stage)
        p.color=Ink.light; p.style=Paint.Style.STROKE; p.strokeWidth=1f
        c.drawOval(e.boss.x.toFloat()-34,e.boss.y.toFloat()-9,e.boss.x.toFloat()+34,e.boss.y.toFloat()+17,p)
        p.style=Paint.Style.FILL
        for(s in e.summons) {
            art.sprite(c,when(s.kind) { 0 -> "giant"; 1 -> "rabbit"; else -> "haniwa" },s.x.toFloat(),s.y.toFloat(),if(s.kind==0) 1.2f else .85f)
            bar(c,s.x.toFloat()-14,s.y.toFloat()+8,28f,5f,s.life,20.0)
        }
        val px=e.player.x.toFloat(); val py=e.player.y.toFloat()
        if((e.buffs["clones"] ?: .0)>0) {
            art.sprite(c,art.heroKey(e.job),px-32,py+7,1.1f,alpha=135)
            art.sprite(c,art.heroKey(e.job),px+32,py+7,1.1f,alpha=135)
        }
        val alpha=if((e.buffs["invisible"] ?: .0)>0) 85 else if(e.invulnerability>0) 155 else 255
        art.sprite(c,art.heroKey(e.job),px,py,1.15f,alpha=alpha)
        // A visible foot marker is the actual seven-unit collision circle.
        p.style=Paint.Style.STROKE; p.color=Ink.light; p.strokeWidth=2f; c.drawCircle(px,py,7f,p); p.style=Paint.Style.FILL
        rect(c,px-2,py-2,4f,4f,Ink.light)
        if((e.buffs["shield"] ?: .0)>0 || (e.buffs["armor"] ?: .0)>0) {
            p.style=Paint.Style.STROKE; p.strokeWidth=2f; c.drawCircle(px,py-15,24f,p); p.style=Paint.Style.FILL
        }
        if(e.restTime>0) { pixel(c,"REST",px,py-61,1.25f,Ink.light,true); bar(c,px-24,py-48,48f,7f,2-e.restTime,2.0) }
        e.projectiles.forEach { pr ->
            if(pr.kind=="fire") art.icon(c,"fire",pr.x.toFloat()-7,pr.y.toFloat()-8,1f)
            else { p.color=Ink.light; p.strokeWidth=3f; val a=atan2(pr.vy,pr.vx); c.drawLine(pr.x.toFloat(),pr.y.toFloat(),(pr.x-cos(a)*13).toFloat(),(pr.y-sin(a)*13).toFloat(),p) }
        }
        e.particles.forEach { q ->
            if(q.text=="SLASH" || q.text=="HANIWA") {
                p.style=Paint.Style.STROKE; p.strokeWidth=5f; p.color=Ink.light
                c.drawArc(q.x.toFloat()-28,q.y.toFloat()-28,q.x.toFloat()+28,q.y.toFloat()+28,190f,150f,false,p); p.style=Paint.Style.FILL
            } else if(q.text=="BURST"||q.text=="ICE"||q.text=="HIT") {
                art.icon(c,if(q.text=="ICE") "ice" else "boost",q.x.toFloat()-16,q.y.toFloat()-16,2f)
            } else { pixel(c,q.text.replace('−','-'),q.x.toFloat()+1,q.y.toFloat()+1,1.7f,Ink.dark,true); pixel(c,q.text.replace('−','-'),q.x.toFloat(),q.y.toFloat(),1.7f,Ink.light,true) }
        }
        c.restore()
        // Cast information stays outside the playfield so it never hides a telegraph.
        if(e.castEnd>e.elapsed) {
            text(c,e.castName,28f,455f,14f)
            bar(c,28f,464f,241f,8f,e.castEnd-e.elapsed,1.75)
        } else pixel(c,"READ. DODGE. STRIKE.",28f,456f,1.3f,Ink.mid)
        text(c,if(e.messageTime>0) e.message else e.castHint,282f,455f,13f)
        // Floating thumb stick. Anywhere in the left half of the arena can be used.
        p.color=Ink.dark; c.drawCircle(103f,374f,48f,p); p.style=Paint.Style.STROKE; p.color=Ink.mid; p.strokeWidth=2f; c.drawCircle(103f,374f,47f,p); p.style=Paint.Style.FILL
        rect(c,77f,370f,52f,8f,Ink.deep); rect(c,99f,348f,8f,52f,Ink.deep)
        p.color=Ink.mid; c.drawCircle(103f+stickX*25,374f+stickY*25,18f,p)
        p.color=Ink.light; c.drawCircle(103f+stickX*25,374f+stickY*25,5f,p)
        rect(c,653f,72f,283f,221f,Ink.deep); border(c,653f,72f,283f,221f)
        art.sprite(c,art.heroKey(e.job),687f,127f,1.05f)
        text(c,e.job.label,723f,101f,19f); pixel(c,"${e.elapsed.toInt()/60}:${(e.elapsed.toInt()%60).toString().padStart(2,'0')}",861f,89f,1.7f,Ink.light)
        text(c,"HP ${ceil(e.player.hp).toInt()} / ${e.player.maxHp.toInt()}",723f,123f,13f)
        bar(c,671f,141f,247f,15f,e.player.hp,e.player.maxHp)
        text(c,if(e.job==Job.SUMMONER) "召喚ゲージ  +10/s" else "アクションゲージ  +20/s",671f,181f,12f,Ink.mid)
        bar(c,671f,190f,247f,12f,e.gauge,100.0)
        text(c,"${r.gold} G",671f,229f,17f); pixel(c,"SCORE ${r.score}",768f,218f,1.25f,Ink.mid)
        val buffNames=mapOf("shield" to "盾","focus" to "魔力","power" to "攻↑","armor" to "守↑","haste" to "速↑","speed" to "気合","invisible" to "無敵","clones" to "三影")
        val active=e.buffs.filter { it.value>0 }.entries.joinToString(" ") { "${buffNames[it.key]}${ceil(it.value).toInt()}s" }
        text(c,if(active.isEmpty()) (if(e.job==Job.SUMMONER) "仲間 ${e.summons.size}/2" else "足元の小さな丸が当たり判定") else active,671f,260f,12f,Ink.light)
        if(e.fortune) text(c,"黄金の印：報酬 ×3",671f,281f,11f,Ink.mid)
        Skills.all.getValue(e.job).forEachIndexed { i,s ->
            val x=653f+(i%2)*145; val y=310f+(i/2)*88
            val ready=e.cooldowns[i]<=0 && e.restTime<=0
            val held=e.heldSkill==i
            rect(c,x,y,138f,77f,if(held) Ink.light else Ink.deep); border(c,x,y,138f,77f,if(ready) Ink.light else Ink.mid)
            art.icon(c,s.glyph,x+11,y+12,1.7f,if(held) Ink.dark else Ink.light)
            pixel(c,"LV${e.levels[i]}",x+92,y+11,1.2f,if(held) Ink.dark else Ink.mid)
            text(c,if(e.job==Job.SUMMONER&&i==2&&e.summons.any { it.kind==2 }) "はにわで殴る" else s.name,x+69,y+58,if(s.name.length>7) 13f else 15f,if(held) Ink.dark else Ink.light,Paint.Align.CENTER)
            if(!ready && e.cooldowns[i]>0) {
                rect(c,x+3,y+68,(132*e.cooldowns[i]/Skills.cooldown(e.job,i,e.levels[i])).toFloat(),5f,Ink.mid)
                text(c,"%.1fs".format(java.util.Locale.ROOT,e.cooldowns[i]),x+62,y+28,12f,if(held) Ink.dark else Ink.light)
            }
            buttons.add(UiButton("技${i+1} ${s.name}",RectF(x,y,x+138,y+77),true,i) { e.useSkill(i) })
        }
        pixel(c,"ITEMS",29f,500f,1.4f,Ink.mid)
        for(i in 0..4) {
            val x=116f+i*99
            rect(c,x,484f,89f,41f,Ink.deep); border(c,x,484f,89f,41f,Ink.mid,1f)
            if(i<r.inventory.size) {
                val item=r.inventory[i]; art.icon(c,item.icon,x+7,491f,1.6f)
                text(c,item.title.take(3),x+40,508f,12f)
                buttons.add(UiButton("アイテム${i+1} ${item.title}",RectF(x,484f,x+89,525f)) { e.selectedItem=i; e.pause() })
            } else text(c,"—",x+45,509f,14f,Ink.mid,Paint.Align.CENTER)
        }
        text(c,"技は長押しで連続使用",795f,502f,12f,Ink.mid,Paint.Align.CENTER)
        text(c,"休むと2秒間移動できません",795f,523f,11f,Ink.mid,Paint.Align.CENTER)
    }
    private fun drawHazard(c: Canvas,h: Hazard) {
        val path=Path(); val x=h.x.toFloat(); val y=h.y.toFloat(); val a=h.a.toFloat(); val b=h.b.toFloat()
        when(h.shape) {
            "circle" -> path.addCircle(x,y,a,Path.Direction.CW)
            "ring" -> { path.addRect(0f,0f,600f,334f,Path.Direction.CW); path.addCircle(x,y,a,Path.Direction.CCW) }
            "safe", "tower" -> { path.addRect(0f,0f,600f,334f,Path.Direction.CW); path.addCircle(x,y,a,Path.Direction.CCW) }
            "line" -> {
                val dx=cos(h.angle).toFloat(); val dy=sin(h.angle).toFloat()
                path.moveTo(x-dx*a/2+dy*b/2,y-dy*a/2-dx*b/2); path.lineTo(x+dx*a/2+dy*b/2,y+dy*a/2-dx*b/2)
                path.lineTo(x+dx*a/2-dy*b/2,y+dy*a/2+dx*b/2); path.lineTo(x-dx*a/2-dy*b/2,y-dy*a/2+dx*b/2); path.close()
            }
            "cone" -> { path.moveTo(x,y); path.arcTo(x-a,y-a,x+a,y+a,((h.angle-h.b/2)*180/PI).toFloat(),(h.b*180/PI).toFloat(),false); path.close() }
            "knock" -> {
                p.style=Paint.Style.STROKE; p.strokeWidth=2f; p.color=Ink.light
                for(i in 1..3) c.drawCircle(x,y,20f+i*22,p)
                p.style=Paint.Style.FILL
                pixel(c,"CENTER",x,y-8,1.5f,Ink.light,true); return
            }
        }
        c.save(); c.clipPath(path)
        p.color=if(h.resolved) Ink.light else Ink.dark; p.alpha=if(h.resolved) 175 else 100; c.drawPath(path,p); p.alpha=255
        p.color=if(h.resolved) Ink.dark else Ink.mid; p.strokeWidth=2f
        for(i in -340..600 step 14) c.drawLine(i.toFloat(),0f,i+340f,340f,p)
        c.restore()
        p.color=Ink.light; p.style=Paint.Style.STROKE; p.strokeWidth=2f; c.drawPath(path,p)
        if(h.shape in listOf("circle","ring","safe","tower")) {
            c.drawArc(x-a,y-a,x+a,y+a,-90f,(h.time/h.delay*360).coerceAtMost(360.0).toFloat(),false,p)
        }
        p.style=Paint.Style.FILL
        if(h.shape=="safe"||h.shape=="tower") {
            art.icon(c,if(h.shape=="tower") "boost" else "ghost",x-12,y-21,1.5f)
            text(c,h.label,x,y+22,14f,Ink.light,Paint.Align.CENTER)
        }
    }
    private fun scrim(c: Canvas) { p.color=Ink.dark; p.alpha=225; c.drawRect(0f,57f,960f,540f,p); p.alpha=255; buttons.clear() }
    private fun cutin(c: Canvas) {
        scrim(c)
        val b=engine.bossInfo
        rect(c,0f,161f,960f,190f,Ink.light)
        for(i in 0..22) rect(c,i*48f-20,166f+i%3*4,30f,2f,Ink.mid)
        art.sprite(c,b.form,202f,337f,4.5f,engine.run!!.stage)
        pixel(c,"LIMIT BREAK",384f,192f,2.8f,Ink.deep)
        text(c,b.ultimate,383f,269f,30f,Ink.dark)
        text(c,b.name,385f,309f,18f,Ink.deep)
        text(c,"残り1/3 ── 神々の真なる力",480f,119f,18f,Ink.light,Paint.Align.CENTER)
        wrap(c,b.hint,170f,401f,620f,19f,Ink.light,30f)
        pixel(c,"READ THE SIGNS",480f,475f,1.7f,Ink.mid,true)
    }
    private fun paused(c: Canvas) {
        scrim(c)
        val selected=engine.selectedItem; val inv=engine.run!!.inventory
        if(selected in inv.indices) {
            val item=inv[selected]
            rect(c,236f,132f,488f,303f,Ink.deep); border(c,236f,132f,488f,303f)
            art.icon(c,item.icon,453f,166f,3.4f)
            text(c,item.title,480f,267f,26f,Ink.light,Paint.Align.CENTER)
            text(c,item.description,480f,306f,17f,Ink.mid,Paint.Align.CENTER)
            button(c,"使う",490f,354f,193f,48f,true) { engine.unpause(); engine.useItem(selected) }
            button(c,"戻る",278f,354f,193f,48f) { engine.selectedItem=-1; engine.unpause() }
        } else {
            pixel(c,"PAUSED",480f,132f,4f,Ink.light,true)
            text(c,"ひと息ついて、次の一手を。",480f,207f,18f,Ink.mid,Paint.Align.CENTER)
            button(c,"戦闘に戻る",345f,244f,270f,51f,true) { engine.unpause() }
            button(c,"遊び方",345f,310f,270f,43f) { returnScreen=Screen.PAUSED; engine.changeScreen(Screen.HELP) }
            button(c,"タイトルへ",345f,367f,270f,43f) { engine.changeScreen(Screen.TITLE) }
            text(c,"つづきからは、このボスの戦闘前から再開します。",480f,464f,14f,Ink.mid,Paint.Align.CENTER)
        }
    }
    private fun reward(c: Canvas) {
        val e=engine
        header(c,"VICTORY",false)
        pixel(c,"BOSS DEFEATED",35f,82f,3.6f)
        text(c,"${e.bossInfo.name}を越えた。",37f,134f,17f,Ink.mid)
        text(c,"%.1f秒  /  被ダメージ %.0f  /  +%d G".format(java.util.Locale.ROOT,e.lastTime,e.lastDamage,e.lastGold),37f,165f,16f)
        pixel(c,"+${e.lastScore}",704f,105f,3.4f,Ink.light)
        val thief=e.job==Job.THIEF
        text(c,if(e.rewardChosen) "技を強化しました" else "強化する技を1つ選択",37f,202f,19f)
        Skills.all.getValue(e.job).forEachIndexed { i,s ->
            val x=36f+i*226; val y=221f
            rect(c,x,y,211f,if(thief) 113f else 157f,Ink.deep); border(c,x,y,211f,if(thief) 113f else 157f)
            art.icon(c,s.glyph,x+14,y+17,2f)
            text(c,s.name,x+62,y+36,15f)
            text(c,if(e.rewardChosen) "Lv.${e.levels[i]}" else if(e.levels[i]>=16) "Lv.16 MAX" else "Lv.${e.levels[i]} → ${e.levels[i]+1}",x+16,y+70,20f)
            text(c,Skills.detail(e.job,i,e.levels[i]),x+16,y+99,11f,Ink.mid)
            if(!thief) wrap(c,s.description,x+16,y+123,178f,12f,Ink.mid,19f)
            buttons.add(UiButton("${s.name}を強化",RectF(x,y,x+211,y+(if(thief) 113 else 157)),!e.rewardChosen&&e.levels[i]<16) { e.upgrade(i) })
        }
        if(thief) {
            text(c,if(e.lootChosen) "特殊アイテムを獲得しました" else "盗賊の戦利品：4つから1つ選ぶ",37f,364f,16f)
            Item.entries.filter { it.special }.forEachIndexed { i,item ->
                val x=36f+i*226
                button(c,item.title,x,378f,211f,34f,enabled=!e.lootChosen) { e.chooseLoot(item) }
                text(c,item.description,x+105,429f,10f,Ink.mid,Paint.Align.CENTER)
            }
            if(e.pendingLoot!=null) {
                text(c,"交換する所持品を選択：",37f,459f,14f)
                e.run!!.inventory.forEachIndexed { i,item -> button(c,item.title,217f+i*141,441f,135f,27f) { e.replaceLoot(i) } }
            } else text(c,"特殊アイテムもストック5個に含まれます。",37f,459f,12f,Ink.mid)
        } else {
            text(c,"威力・効果はLv.1で最大の25%。Lv.16まで直線的に成長。",37f,421f,14f,Ink.mid)
            text(c,"範囲も拡大し、待機時間も短くなります。",37f,447f,14f,Ink.mid)
        }
        button(c,if(e.run!!.stage==31) "夜明けへ  →" else "旅の商人へ  →",674f,476f,250f,45f,true,e.rewardChosen&&e.lootChosen) { e.finishReward() }
        pixel(c,"ONE STEP CLOSER TO DAWN",37f,491f,1.4f,Ink.mid)
    }
    private fun shop(c: Canvas) {
        val e=engine; val r=e.run!!
        header(c,"THE WANDERING MERCHANT")
        pixel(c,"A MOMENT OF REST",36f,83f,2.8f)
        text(c,"「次の神に挑む前に、旅の支度はいかが？」",36f,132f,16f,Ink.mid)
        text(c,"${r.gold} G",920f,111f,27f,Ink.light,Paint.Align.RIGHT)
        Item.entries.filter { !it.special }.forEachIndexed { i,item ->
            val x=36f+i*226
            rect(c,x,159f,211f,198f,Ink.deep); border(c,x,159f,211f,198f)
            art.icon(c,item.icon,x+81,178f,3f)
            text(c,item.title,x+105,251f,18f,Ink.light,Paint.Align.CENTER)
            text(c,item.description,x+105,281f,12f,Ink.mid,Paint.Align.CENTER)
            button(c,"${item.price} G  購入",x+16,307f,179f,35f,enabled=r.gold>=item.price&&r.inventory.size<5) { e.buy(item) }
        }
        text(c,"旅のかばん  ${r.inventory.size}/5",36f,393f,18f)
        for(i in 0..4) {
            val x=36f+i*179
            val item=r.inventory.getOrNull(i)
            if(item!=null) button(c,item.title,x,410f,168f,44f) {
                AlertDialog.Builder(context).setTitle(item.title).setMessage("${item.description}\n\nこのアイテムを手放して、かばんに空きを作りますか？")
                    .setPositiveButton("手放す") { _,_ -> e.discardItem(i) }.setNegativeButton("戻る",null).show()
            } else { border(c,x,410f,168f,44f,Ink.deep); text(c,"空き",x+84,438f,13f,Ink.mid,Paint.Align.CENTER) }
        }
        text(c,"${if(e.messageTime>0) e.message else "ボス戦ごとにHPとゲージは全回復。ここで自動保存されます。"}",36f,499f,13f,Ink.mid)
        button(c,"次のボスへ  →",675f,475f,249f,46f,true) { e.leaveShop() }
    }
    private fun codex(c: Canvas) {
        header(c,"THE GODS AND THEIR KIN")
        text(c,"神話図鑑",36f,102f,27f)
        text(c,"原典のモチーフから生まれた、32の試練。",204f,100f,14f,Ink.mid)
        for(i in 0..7) {
            val index=codexPage*8+i; val b=Bosses.all[index]
            val x=36f+(i%2)*200; val y=129f+(i/2)*84
            val selected=index==engine.selectedBoss
            rect(c,x,y,190f,74f,if(selected) Ink.light else Ink.deep)
            pixel(c,(index+1).toString().padStart(2,'0'),x+10,y+12,1.3f,if(selected) Ink.deep else Ink.mid)
            text(c,b.name,x+10,y+47,if(b.name.length>8) 13f else 15f,if(selected) Ink.dark else Ink.light)
            buttons.add(UiButton("図鑑 ${b.name}",RectF(x,y,x+190,y+74)) { engine.selectedBoss=index })
        }
        val b=Bosses.all[engine.selectedBoss]
        rect(c,459f,81f,465f,381f,Ink.deep)
        art.sprite(c,b.form,538f,218f,2.5f,engine.selectedBoss)
        text(c,b.name,614f,121f,24f)
        text(c,b.epithet,614f,150f,13f,Ink.mid)
        wrap(c,b.lore,614f,180f,289f,14f)
        text(c,b.ultimate,481f,270f,19f)
        wrap(c,b.hint,481f,302f,418f,15f)
        text(c,"♪ ${b.theme}",481f,378f,17f)
        text(c,"${b.bpm} BPM  /  パルス波・三角波・ノイズ",481f,406f,13f,Ink.mid)
        text(c,"戦闘と楽曲は神話をもとにした独自の創作です。",481f,443f,12f,Ink.mid)
        button(c,"←",36f,483f,62f,36f,enabled=codexPage>0) { codexPage--; engine.selectedBoss=codexPage*8 }
        pixel(c,"${codexPage+1} / 4",233f,496f,1.5f,Ink.mid,true)
        button(c,"→",364f,483f,62f,36f,enabled=codexPage<3) { codexPage++; engine.selectedBoss=codexPage*8 }
        text(c,"参考：散文エッダ / 古エッダ（詳細はREADME）",481f,506f,13f,Ink.mid)
    }
    private fun help(c: Canvas) {
        header(c,"HOW TO PLAY")
        pixel(c,"READ. DODGE. STRIKE.",36f,83f,2.8f)
        val topics=listOf(
            "01  移動と攻撃" to "戦場の左半分をドラッグして移動。右の技をタップ、長押しで連続使用。攻撃は自動でボスの方向を狙います。足元の丸が当たり判定です。",
            "02  予兆を読む" to "斜線が危険地帯。輪の内側は安全です。月印と白いルーンの円は中へ入りましょう。吹き飛ばしは中央へ。前後攻撃は切り返します。",
            "03  技と召喚" to "技にはゲージと待機時間が必要。召喚士は回復速度が半分で仲間は2体まで。はにわは正面を守り、再タップすると近接攻撃します。",
            "04  休むとアイテム" to "休むと2秒間動けず、その後に回復。下のアイテムを選ぶと時間が止まり、効果を確認して使えます。かばんは特殊アイテムを含めて5個まで。",
            "05  成長と保存" to "撃破後に技を1つ強化。Lv.16が最大。盗賊は特殊品も1つ選べます。戦闘前と買い物後に自動保存。敗北で冒険終了、最高スコアは残ります。",
            "06  高いスコアへ" to "素早く倒し、被ダメージを減らすと高得点。全32体を越えると世界に色が戻ります。物理キー：WASD/矢印で移動、1〜4で技、Escで一時停止。"
        )
        topics.forEachIndexed { i,pair ->
            val x=36f+(i%2)*455; val y=145f+(i/2)*123
            text(c,pair.first,x,y,18f)
            wrap(c,pair.second,x,y+27,420f,14f,Ink.mid,23f)
        }
    }
    private fun result(c: Canvas,clear: Boolean) {
        val r=engine.run!!
        if(clear) landscape(c,true) else landscape(c)
        if(!clear) { p.color=Ink.dark; p.alpha=200; c.drawRect(0f,57f,960f,540f,p); p.alpha=255 }
        header(c,if(clear) "A NEW DAWN" else "THE JOURNEY ENDS",false)
        pixel(c,if(clear) "THE WORLD" else "GAME OVER",40f,103f,4.5f,if(clear) Ink.dawn[3] else Ink.light)
        if(clear) pixel(c,"IN COLOR",40f,155f,4.5f,Ink.dawn[2])
        text(c,if(clear) "世界に、色が戻った。" else "夜は、まだ明けない。",40f,if(clear) 233f else 188f,26f,if(clear) Ink.dawn[3] else Ink.light)
        wrap(c,if(clear) "最後の神が剣を下ろした。\n灰色だった葉に緑が、空に青が宿る。\n小さな勇者の旅は、誰かの明日になった。" else "倒れるたび、予兆は記憶になる。\n次の冒険では、きっと一歩先へ。",40f,if(clear) 274f else 234f,505f,16f,if(clear) Ink.dawn[3] else Ink.mid,26f)
        rect(c,39f,365f,462f,93f,Ink.dark); border(c,39f,365f,462f,93f,if(clear) Ink.dawn[2] else Ink.mid)
        pixel(c,"SCORE ${r.score.toString().padStart(6,'0')}",58f,383f,2.4f,if(clear) Ink.dawn[3] else Ink.light)
        text(c,"${r.job.label}  /  ${r.kills}体撃破  /  %.1f秒  /  被ダメージ %.0f".format(java.util.Locale.ROOT,r.totalTime,r.totalDamage),58f,438f,13f,if(clear) Ink.dawn[2] else Ink.mid)
        button(c,"タイトルへ",40f,478f,215f,44f,true) { engine.changeScreen(Screen.TITLE) }
        if(clear) { text(c,"THANK YOU FOR PLAYING",736f,486f,15f,Ink.dawn[3],Paint.Align.CENTER); text(c,"BOSSRUSH / ORIGINAL ART & MUSIC",736f,511f,11f,Ink.dawn[2],Paint.Align.CENTER) }
        else button(c,"もう一度挑む",274f,478f,227f,44f) { engine.changeScreen(Screen.JOBS) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index=event.actionIndex; val id=event.getPointerId(index)
        val x=(event.getX(index)-ox)/scale; val y=(event.getY(index)-oy)/scale
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN -> {
                val b=buttons.lastOrNull { it.rect.contains(x,y) }
                if(b!=null) {
                    if(b.enabled) {
                        pressed=b.label
                        if(b.skill>=0 && engine.screen==Screen.BATTLE) { skillPointer=id; engine.heldSkill=b.skill; b.action() }
                    }
                } else if(engine.screen==Screen.BATTLE && x<640 && y in 95f..433f && joystickId<0) {
                    joystickId=id
                    // Relative drag with a fixed visible center only when touched near the thumb pad.
                    joystickOriginX=if(hypot(x-103,y-374)<65) 103f else x
                    joystickOriginY=if(hypot(x-103,y-374)<65) 374f else y
                    updateStick(x,y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if(joystickId>=0) { val i=event.findPointerIndex(joystickId); if(i>=0) updateStick((event.getX(i)-ox)/scale,(event.getY(i)-oy)/scale) }
                return true
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP -> {
                if(id==joystickId) { joystickId=-1; stickX=0f; stickY=0f; engine.moveX=.0; engine.moveY=.0 }
                else if(id==skillPointer) { skillPointer=-1; engine.heldSkill=-1; pressed=null }
                else {
                    val b=buttons.lastOrNull { it.rect.contains(x,y) && it.label==pressed }
                    if(b?.enabled==true) { b.action(); audio.effect("click"); performClick() }
                    pressed=null
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { resetInput(); return true }
        }
        return true
    }
    private var joystickOriginX=103f; private var joystickOriginY=374f
    private fun updateStick(x: Float,y: Float) {
        val dx=(x-joystickOriginX)/37; val dy=(y-joystickOriginY)/37
        val len=hypot(dx,dy).coerceAtLeast(1f); stickX=dx/len; stickY=dy/len
        engine.moveX=stickX.toDouble(); engine.moveY=stickY.toDouble()
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onKeyDown(keyCode: Int,event: KeyEvent): Boolean {
        if(keyCode==KeyEvent.KEYCODE_ESCAPE) { if(event.repeatCount==0) goBack(); return true }
        if(keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_4 && engine.screen==Screen.BATTLE) {
            engine.heldSkill=keyCode-KeyEvent.KEYCODE_1; engine.useSkill(engine.heldSkill); return true
        }
        if(keyCode in movementKeys) { keySet.add(keyCode); updateKeyboard(); return true }
        return super.onKeyDown(keyCode,event)
    }
    override fun onKeyUp(keyCode: Int,event: KeyEvent): Boolean {
        if(keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_4) { engine.heldSkill=-1; return true }
        if(keyCode in movementKeys) { keySet.remove(keyCode); updateKeyboard(); return true }
        return super.onKeyUp(keyCode,event)
    }
    private val movementKeys=setOf(KeyEvent.KEYCODE_W,KeyEvent.KEYCODE_A,KeyEvent.KEYCODE_S,KeyEvent.KEYCODE_D,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN)
    private fun updateKeyboard() {
        engine.moveX=(if(keySet.any { it==KeyEvent.KEYCODE_D || it==KeyEvent.KEYCODE_DPAD_RIGHT }) 1.0 else .0)-(if(keySet.any { it==KeyEvent.KEYCODE_A || it==KeyEvent.KEYCODE_DPAD_LEFT }) 1.0 else .0)
        engine.moveY=(if(keySet.any { it==KeyEvent.KEYCODE_S || it==KeyEvent.KEYCODE_DPAD_DOWN }) 1.0 else .0)-(if(keySet.any { it==KeyEvent.KEYCODE_W || it==KeyEvent.KEYCODE_DPAD_UP }) 1.0 else .0)
    }
    // Canvas controls expose real virtual accessibility nodes for TalkBack, keyboards and UI tests.
    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = object: AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(id: Int): AccessibilityNodeInfo? {
            if(id==View.NO_ID) {
                val node=AccessibilityNodeInfo.obtain(this@GameView); onInitializeAccessibilityNodeInfo(node)
                buttons.indices.forEach { node.addChild(this@GameView,it) }; return node
            }
            val b=buttons.getOrNull(id) ?: return null
            val node=AccessibilityNodeInfo.obtain(); node.setSource(this@GameView,id); node.setParent(this@GameView)
            node.packageName=context.packageName; node.className="android.widget.Button"; node.contentDescription=b.label
            node.isEnabled=b.enabled; node.isClickable=true; node.isFocusable=true; node.isVisibleToUser=true
            node.isAccessibilityFocused=focusId==id
            val location=IntArray(2); getLocationOnScreen(location)
            node.setBoundsInScreen(Rect((ox+b.rect.left*scale).toInt()+location[0],(oy+b.rect.top*scale).toInt()+location[1],(ox+b.rect.right*scale).toInt()+location[0],(oy+b.rect.bottom*scale).toInt()+location[1]))
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            node.addAction(if(focusId==id) AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS else AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
            return node
        }
        override fun performAction(id: Int,action: Int,arguments: Bundle?): Boolean {
            val b=buttons.getOrNull(id) ?: return false
            when(action) {
                AccessibilityNodeInfo.ACTION_CLICK -> { if(!b.enabled) return false; b.action(); invalidate(); return true }
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> { focusId=id; virtualEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED); return true }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> { focusId=View.NO_ID; virtualEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED); return true }
            }
            return false
        }
    }
    private fun virtualEvent(id: Int,eventType: Int) {
        val event=AccessibilityEvent.obtain(eventType); event.packageName=context.packageName; event.className="android.widget.Button"; event.setSource(this,id)
        event.contentDescription=buttons.getOrNull(id)?.label; parent?.requestSendAccessibilityEvent(this,event)
    }
    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        val manager=context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if(manager.isTouchExplorationEnabled) {
            val index=buttons.indexOfLast { it.rect.contains((event.x-ox)/scale,(event.y-oy)/scale) }
            if(index!=hoveredId) { if(hoveredId>=0) virtualEvent(hoveredId,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT); hoveredId=index; if(index>=0) virtualEvent(index,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) }
            if(event.action==MotionEvent.ACTION_HOVER_EXIT && hoveredId>=0) { virtualEvent(hoveredId,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT); hoveredId=View.NO_ID }
            return index>=0
        }
        return super.dispatchHoverEvent(event)
    }
}
