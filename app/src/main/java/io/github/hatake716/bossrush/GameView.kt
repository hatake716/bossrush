package io.github.hatake716.bossrush

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.accessibility.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.*

data class UiButton(val label: String,val rect: RectF,val enabled: Boolean=true,val skill: Int=-1,val action: () -> Unit)

class GameView(context: Context,val engine: GameEngine): View(context), Choreographer.FrameCallback {
    val audio=Chiptune(context)
    var hasSave=false
    var bestScore=0
    var onContinue: (() -> Unit)?=null
    var onSoundChanged: ((Boolean) -> Unit)?=null
    private val art=PixelArt(context.assets)
    private val backgrounds=BackgroundArt(context.assets)
    private val battleEffects=BattleEffects()
    private val playerEffects=PlayerEffects()
    internal val storyText=StoryText(context.assets)
    private val p=Paint().apply { isAntiAlias=false }
    private val type=Paint().apply { isAntiAlias=false; typeface=storyText.face }
    private val buttons=mutableListOf<UiButton>()
    internal var viewport=GameViewport.fit(960,540)
        private set
    private var cutoutLeft=0; private var cutoutTop=0; private var cutoutRight=0; private var cutoutBottom=0
    private val scale get()=viewport.scale
    private val ox get()=viewport.x
    private val oy get()=viewport.y
    private val extra get()=viewport.extra
    private val headerTop get()=(cutoutTop-viewport.y)/viewport.scale
    private val fullBottom get()=viewport.fullTop+viewport.fullHeight
    internal fun safeArea(left: Int,top: Int,right: Int,bottom: Int) {
        if(left==cutoutLeft && top==cutoutTop && right==cutoutRight && bottom==cutoutBottom) return
        cutoutLeft=left; cutoutTop=top; cutoutRight=right; cutoutBottom=bottom
        updateViewport(); resetInput(); windowChanged=true; invalidate()
    }
    private fun updateViewport() {
        viewport=GameViewport.fit(width.coerceAtLeast(1),height.coerceAtLeast(1),cutoutLeft,cutoutTop,cutoutRight,cutoutBottom)
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh); updateViewport(); resetInput(); windowChanged=true
    }
    private inline fun shifted(c: Canvas,dx: Float,dy: Float=0f,draw: () -> Unit) {
        val first=buttons.size
        c.save(); c.translate(dx,dy); draw(); c.restore()
        for(i in first until buttons.size) buttons[i].rect.offset(dx,dy)
    }
    private fun fullRect(c: Canvas,color: Int) = rect(c,viewport.fullLeft,viewport.fullTop,viewport.fullWidth,viewport.fullHeight,color)
    private var lastFrame=0L; private var running=false
    private var clock=0.0
    private var lastScreen=Screen.TITLE
    private var lastStoryPage=""
    private var windowChanged=false
    private var accessibilityButtons=emptyList<Pair<String,Boolean>>()
    private var joystickId=-1; private var stickX=0f; private var stickY=0f
    private var skillPointer=-1
    private var touchSkill=-1
    internal val controller=GameController(this)
    internal fun controllerButtons(): List<UiButton> = buttons
    private var pressed: String?=null
    private var codexPage=0
    private var portraitExpanded=false
    private var returnScreen=Screen.TITLE
    private var controllerHelp=false
    private var focusId=View.NO_ID
    private var hoveredId=View.NO_ID
    init {
        isFocusable=true; isFocusableInTouchMode=true; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        // Canvas draws its own per-button selection. Android's default focus
        // highlight otherwise washes out the entire game after gamepad input.
        defaultFocusHighlightEnabled=false
        contentDescription="BOSSRUSH タイトル"
        ViewCompat.setOnApplyWindowInsetsListener(this) { _,insets ->
            val safe=insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
            safeArea(safe.left,safe.top,safe.right,safe.bottom)
            insets
        }
    }
    fun resume() { if(!running) { running=true; lastFrame=0; audio.start(); Choreographer.getInstance().postFrameCallback(this) } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); controller.attach(); requestFocus() }
    override fun onDetachedFromWindow() { controller.detach(); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if(!hasWindowFocus) { resetInput(); controller.focusLost() }
    }
    fun suspend() { running=false; engine.pause(); resetInput(); audio.stop(); Choreographer.getInstance().removeFrameCallback(this) }
    override fun doFrame(frameTimeNanos: Long) {
        if(!running) return
        val dt=if(lastFrame==0L) .0 else (frameTimeNanos-lastFrame)/1e9
        lastFrame=frameTimeNanos; clock+=dt.coerceAtMost(.05)
        engine.update(dt)
        val scene=when(engine.screen) {
            Screen.BATTLE,Screen.CUTIN,Screen.INTRO,Screen.PAUSED -> "battle"
            Screen.SHOP,Screen.REWARD -> "shop"
            Screen.ENDING -> "ending"
            Screen.STORY -> when(engine.run?.storyMoment) {
                StoryMoment.BEFORE -> "battle"; StoryMoment.AFTER -> "shop"
                StoryMoment.EPILOGUE -> "ending"; else -> "title"
            }
            else -> "title"
        }
        audio.music(scene,engine.run?.stage ?: 0)
        engine.sounds.toList().forEach { audio.effect(it) }; engine.sounds.clear()
        if(lastScreen!=engine.screen) {
            lastScreen=engine.screen; resetInput(); contentDescription="BOSSRUSH ${screenName()}"
            windowChanged=true
        }
        controller.tick()
        invalidate()
        // Battle tracks display vsync. Menus leave idle time for Android lifecycle,
        // accessibility and input dispatch, and need far fewer redraws.
        val delay=when(engine.screen) { Screen.BATTLE,Screen.CUTIN -> 0L; Screen.TITLE,Screen.ENDING -> 33L; else -> 70L }
        Choreographer.getInstance().postFrameCallbackDelayed(this,delay)
    }
    fun screenName()=when(engine.screen) {
        Screen.TITLE -> "タイトル"; Screen.JOBS -> "職業選択"; Screen.INTRO -> "ボス紹介 ${engine.bossInfo.name}"
        Screen.STORY -> "物語 ${MainStory.title(engine.run!!.storyMoment,engine.run!!.stage)}"
        Screen.BATTLE -> "戦闘 ${engine.bossInfo.name}"; Screen.CUTIN -> "必殺技 ${engine.bossInfo.ultimate}"
        Screen.REWARD -> "ボス撃破 技の成長"; Screen.SHOP -> "ショップ"; Screen.PAUSED -> "一時停止"
        Screen.GAMEOVER -> "ゲームオーバー"; Screen.ENDING -> "ゲームクリア"; Screen.CODEX -> "神話図鑑"; Screen.HELP -> "遊び方"
    }
    private fun resetInput() {
        joystickId=-1; skillPointer=-1; touchSkill=-1; pressed=null; stickX=0f; stickY=0f
        controller.reset()
    }
    internal fun refreshControls() {
        val movement=controller.movement
        engine.moveX=if(joystickId>=0) stickX.toDouble() else movement.x.toDouble()
        engine.moveY=if(joystickId>=0) stickY.toDouble() else movement.y.toDouble()
        engine.heldSkill=if(touchSkill>=0) touchSkill else controller.heldSkill
    }
    fun goBack() {
        if(portraitExpanded) { portraitExpanded=false; invalidate(); return }
        when(engine.screen) {
            Screen.BATTLE,Screen.CUTIN -> engine.pause()
            Screen.PAUSED -> engine.unpause()
            Screen.CODEX,Screen.HELP -> engine.changeScreen(returnScreen)
            Screen.JOBS,Screen.STORY,Screen.INTRO,Screen.SHOP,Screen.GAMEOVER,Screen.ENDING -> engine.changeScreen(Screen.TITLE)
            Screen.REWARD -> engine.cancelUpgrade()
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
            // Hang closing punctuation on the preceding line instead of leaving a
            // Japanese full stop or closing bracket alone at the next line's start.
            if(ch=='\n' || (type.measureText(row+ch)>width && ch !in "、。，．！？）」』】〕〉》")) {
                text(c,row,x,yy,size,color); yy+=line; row=if(ch=='\n') "" else "$ch"
            } else row+=ch
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
        shifted(c,0f,headerTop) {
            rect(c,viewport.fullLeft,0f,viewport.fullWidth,57f,Ink.dark); rect(c,24f,55f,912f+extra,1f,Ink.mid)
            art.icon(c,"sword",28f,16f,1.6f); pixel(c,"BOSSRUSH",64f,19f,2.4f)
            pixel(c,if(controller.active && engine.screen !in listOf(Screen.BATTLE,Screen.CUTIN)) "A:OK / B:BACK" else section,300f,22f,1.5f,Ink.mid)
            button(c,if(audio.enabled) "♪ ON" else "♪ OFF",803f+extra,12f,66f,31f) { audio.enabled=!audio.enabled; onSoundChanged?.invoke(audio.enabled) }
            if(back) button(c,"戻る",880f+extra,12f,57f,31f) { goBack() }
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateViewport()
        canvas.drawColor(Ink.dark); canvas.save(); canvas.translate(ox,oy); canvas.scale(scale,scale)
        if(engine.screen !in listOf(Screen.TITLE,Screen.STORY,Screen.ENDING,Screen.GAMEOVER)) {
            val v=viewport
            if(engine.run!=null && engine.screen in listOf(Screen.BATTLE,Screen.CUTIN,Screen.PAUSED,Screen.INTRO))
                backgrounds.battle(canvas,engine.bossInfo.id,v.fullLeft,v.fullTop,v.fullWidth,v.fullHeight)
            else backgrounds.landscape(canvas,false,v.fullLeft,v.fullTop,v.fullWidth,v.fullHeight)
            fullRect(canvas,Color.argb(220,16,29,26))
        }
        buttons.clear()
        when(engine.screen) {
            Screen.TITLE -> title(canvas)
            Screen.JOBS -> jobs(canvas)
            Screen.STORY -> story(canvas)
            Screen.INTRO -> intro(canvas)
            Screen.BATTLE,Screen.CUTIN,Screen.PAUSED -> { battle(canvas); if(engine.screen==Screen.CUTIN) cutin(canvas); if(engine.screen==Screen.PAUSED) paused(canvas) }
            Screen.REWARD -> reward(canvas)
            Screen.SHOP -> shop(canvas)
            Screen.CODEX -> codex(canvas)
            Screen.HELP -> help(canvas)
            Screen.GAMEOVER -> result(canvas,false)
            Screen.ENDING -> result(canvas,true)
        }
        if(controller.active && engine.screen !in listOf(Screen.BATTLE,Screen.CUTIN)) controller.focused()?.let { b ->
            val r=b.rect
            border(canvas,r.left-4,r.top-4,r.width()+8,r.height()+8,Ink.light,2f)
            rect(canvas,r.left-7,r.centerY()-4,5f,8f,Ink.light)
        }
        canvas.restore()
        // Publish the virtual tree only AFTER its new buttons have been drawn. Reused
        // node ids otherwise retain labels from the previous menu in accessibility caches.
        val currentButtons=buttons.map { it.label to it.enabled }
        if(windowChanged || currentButtons!=accessibilityButtons) {
            accessibilityButtons=currentButtons; focusId=View.NO_ID; hoveredId=View.NO_ID
            if(windowChanged) sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            windowChanged=false
            if((context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isEnabled) {
                val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                event.contentChangeTypes=AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                sendAccessibilityEventUnchecked(event)
            }
        }
    }
    private fun landscape(c: Canvas,color: Boolean=false) {
        val v=viewport
        backgrounds.landscape(c,color,v.fullLeft,v.fullTop,v.fullWidth,v.fullHeight)
        val pal=if(color) Ink.dawn else Ink.palette
        art.sprite(c,if(color) art.heroKey(engine.job) else "warrior",v.fullLeft+v.fullWidth*.8604f,v.fullTop+v.fullHeight*.824f,1.45f,color=color)
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
            if(hasSave) controller.prepareDialog(AlertDialog.Builder(context).setTitle("新しい冒険を始めますか？").setMessage("職業を選んで出発すると、今の冒険の保存データが置き換わります。最高スコアは残ります。")
                .setPositiveButton("職業を選ぶ") { _,_ -> engine.changeScreen(Screen.JOBS) }.setNegativeButton("戻る",null).show())
            else engine.changeScreen(Screen.JOBS)
        }
        button(c,"つづきから",274f,372f,173f,49f,enabled=hasSave) { onContinue?.invoke() }
        button(c,"遊び方",48f,438f,123f,36f) { returnScreen=Screen.TITLE; engine.changeScreen(Screen.HELP) }
        button(c,"神話図鑑",185f,438f,123f,36f) { returnScreen=Screen.TITLE; engine.changeScreen(Screen.CODEX) }
        pixel(c,"BEST ${bestScore.toString().padStart(6,'0')}",48f,502f,1.65f,Ink.mid)
        rect(c,638f+extra,fullBottom-45f,288f,29f,Color.argb(215,16,29,26))
        pixel(c,"32 GODS / 4 HEROES / 1 DAWN",649f+extra,fullBottom-35f,1.2f,Ink.mid)
    }
    private fun jobs(c: Canvas) {
        header(c,"CHOOSE YOUR HERO")
        pixel(c,"WHO WILL FACE THE GODS?",36f,83f,2.4f)
        text(c,"色を失った世界に、小さな勇者が立ち上がる。",36f,126f,15f,Ink.mid)
        Job.entries.forEachIndexed { i,job ->
            val x=36f+i*(226+extra/3); val selected=engine.selectedJob==job
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
        button(c,"この職業で出発  →",673f+extra,457f,251f,51f,true) { engine.newRun(story=true) }
        pixel(c,"LEVEL 1 - 16",37f,496f,1.5f,Ink.mid)
    }
    private fun story(c: Canvas) {
        val r=engine.run!!; val moment=r.storyMoment
        val book=MainStory.chapters[r.stage]; val line=engine.storyLine
        val chapterScene=moment==StoryMoment.BEFORE || moment==StoryMoment.AFTER
        val color=moment==StoryMoment.EPILOGUE
        val v=viewport
        if(chapterScene) backgrounds.battle(c,book.id,v.fullLeft,v.fullTop,v.fullWidth,v.fullHeight)
        else backgrounds.landscape(c,color,v.fullLeft,v.fullTop,v.fullWidth,v.fullHeight)
        fullRect(c,Color.argb(if(color) 55 else 105,16,29,26))
        header(c,"THE COLORLESS CHRONICLE")
        // A dark title panel and full-width dialogue keep text clear of detailed art.
        rect(c,36f,77f,535f+extra/2,191f,Color.argb(230,16,29,26))
        border(c,36f,77f,535f+extra/2,191f,Ink.mid)
        storyText.draw(c,if(chapterScene) MainStory.act(r.stage) else "色を取り戻す旅",55f,95f,16f,Ink.mid)
        storyText.draw(c,MainStory.title(moment,r.stage),55f,130f,24f)
        storyText.draw(c,"取り戻した色　${r.kills.coerceIn(0,32)}／３２",55f,185f,16f,Ink.mid)
        for(i in 0..31) {
            val xx=55f+(i%16)*28; val yy=217f+(i/16)*20
            rect(c,xx,yy,18f,12f,if(i<r.kills) MainStory.chapters[i].color else Ink.deep)
            border(c,xx,yy,18f,12f,if(i<r.kills) Ink.light else Ink.mid,1f)
        }
        if(chapterScene && line.speaker!="旅人") art.boss(c,book.id,745f+extra*.75f,300f,285f+extra/4,216f)
        else art.sprite(c,art.heroKey(r.job),741f+extra*.75f,281f,3.3f,color=color)
        val x=36f; val y=307f; val w=888f+extra
        rect(c,x+4,y+5,w,212f,Ink.dark)
        rect(c,x,y,w,212f,Ink.dark); border(c,x,y,w,212f,Ink.light,3f); border(c,x+7,y+7,w-14,198f,Ink.mid,1f)
        rect(c,54f,293f,(line.speaker.length*16+32).toFloat(),31f,Ink.dark)
        storyText.draw(c,line.speaker,70f,300f,16f)
        pixel(c,"${r.storyPage+1} / ${engine.storyPages.size}",895f+extra,318f,1.3f,Ink.mid,true)
        storyText.paragraph(c,line.text,60f,342f,840f+extra,24f,30f)
        // Primary action is first in the virtual tree, so A advances naturally.
        button(c,engine.storyAdvanceLabel,670f+extra,467f,230f,36f,true) { engine.advanceStory() }
        button(c,"前のページ",60f,467f,145f,36f,enabled=r.storyPage>0) { engine.previousStoryPage() }
        button(c,"この場面をスキップ",223f,467f,210f,36f) { engine.skipStory() }
        val signature="${r.storyMoment}:${r.stage}:${r.storyPage}"
        contentDescription="BOSSRUSH ${MainStory.title(moment,r.stage)}。${r.storyPage+1}ページ。${line.speaker}。${line.text}"
        if(lastStoryPage!=signature) { lastStoryPage=signature; windowChanged=true }
    }
    private fun intro(c: Canvas) {
        val b=engine.bossInfo; val stage=engine.run!!.stage
        header(c,"THE NEXT ENCOUNTER")
        rect(c,36f,79f,355f+extra,416f,Ink.deep); border(c,36f,79f,355f+extra,416f)
        pixel(c,"ENCOUNTER ${(stage+1).toString().padStart(2,'0')} / 32",60f,103f,1.8f)
        backgrounds.portrait(c,b.id,38f,142f,351f+extra,276f)
        art.boss(c,b.id,214f+extra/2,405f,300f+extra,270f)
        text(c,b.realm,214f+extra/2,447f,19f,Ink.light,Paint.Align.CENTER)
        pixel(c,"${b.bpm} BPM",214f+extra/2,467f,1.2f,Ink.mid,true)
        shifted(c,extra) {
            text(c,b.epithet,431f,108f,15f,Ink.mid)
            text(c,b.name,428f,153f,34f)
            wrap(c,b.lore,431f,195f,460f,16f)
            rect(c,431f,237f,491f,126f,Ink.deep)
            pixel(c,"LIMIT BREAK",450f,252f,1.4f,Ink.mid)
            text(c,b.ultimate,450f,294f,20f)
            wrap(c,b.hint,450f,325f,445f,14f)
            text(c,"通常：${b.attackNames.joinToString(" / ")}",431f,386f,13f,Ink.mid)
            text(c,"HP1/2で覚醒。1/4以下は二重詠唱！",431f,407f,12f,Ink.mid)
            text(c,"全回復して挑戦  /  アイテム ${engine.run!!.inventory.size}/5",431f,424f,15f)
            button(c,"戦闘開始  →",665f,454f,259f,51f,true) { engine.beginBattle() }
        }
    }
    private fun bar(c: Canvas,x: Float,y: Float,w: Float,h: Float,value: Double,maxValue: Double,color: Int=Ink.light) {
        rect(c,x,y,w,h,Ink.dark); border(c,x,y,w,h,Ink.mid,1f)
        rect(c,x+3,y+3,((w-6)*(value/maxValue).coerceIn(.0,1.0)).toFloat(),h-6,color)
    }
    private fun battle(c: Canvas) {
        val e=engine; val r=e.run!!; val b=e.bossInfo
        header(c,"${(r.stage+1).toString().padStart(2,'0')} / 32   ${b.id.uppercase()}",false)
        shifted(c,0f,headerTop) { button(c,"II",880f+extra,12f,57f,31f) { e.pause() } }
        text(c,b.name,28f,80f,19f)
        bar(c,225f,65f,307f+extra,16f,e.boss.hp,e.boss.maxHp)
        pixel(c,"${ceil(e.boss.hp/e.boss.maxHp*100).toInt()}%",543f+extra,69f,1.35f)
        backgrounds.battle(c,b.id,28f,96f,600f+extra,334f)
        border(c,25f,93f,606f+extra,340f,if(e.ultimateActive) UltimateColors.forBoss(b.id).energy else Ink.mid)
        if(extra>0) {
            rect(c,28f,96f,extra/2,334f,Color.argb(80,16,29,26))
            rect(c,628f+extra/2,96f,extra/2,334f,Color.argb(80,16,29,26))
        }
        c.save(); c.translate(28f+extra/2,96f); c.clipRect(0f,0f,600f,334f)
        p.color=Ink.mid; p.style=Paint.Style.STROKE; p.strokeWidth=1f
        c.drawOval(80f,12f,520f,327f,p); c.drawOval(92f,20f,508f,319f,p); p.style=Paint.Style.FILL
        for(i in 0..7) {
            val a=i*PI/4; val x=300+cos(a)*215; val y=170+sin(a)*146
            pixel(c,"${i+1}",x.toFloat(),y.toFloat(),1.4f,Ink.mid,true)
        }
        playerEffects.ground(c,e)
        e.impacts.forEach { battleEffects.impact(c,it,b.id) }
        e.hazards.forEach { battleEffects.telegraph(c,it,b.id) }
        e.hazards.firstOrNull { !it.resolved }?.let {
            battleEffects.charge(c,e.boss.x,e.boss.y,it.time/it.delay,it.ultimate,b.id)
        }
        val motion=e.bossMove
        motion?.let { battleEffects.movement(c,it,b.id) }
        val lift=(motion?.lift ?: .0).coerceAtMost((e.boss.y-108).coerceAtLeast(.0)).toFloat()
        val shadowWidth=46f-lift*.35f
        p.color=Ink.dark; c.drawOval(e.boss.x.toFloat()-shadowWidth,e.boss.y.toFloat()-2,e.boss.x.toFloat()+shadowWidth,e.boss.y.toFloat()+18-lift*.15f,p)
        if(motion!=null && motion.progress>0 && !motion.finished && motion.kind!=BossMoveKind.BLINK) {
            for(i in 2 downTo 1) {
                val u=(motion.progress-i*.12).coerceAtLeast(.0); val at=motion.point(u)
                val trailLift=(if(motion.kind==BossMoveKind.LEAP) sin(u*PI)*48 else .0).coerceAtMost((at.second-108).coerceAtLeast(.0))
                art.boss(c,b.id,at.first.toFloat(),(at.second+5-trailLift).toFloat(),160f,110f,alpha=65-i*20)
            }
        }
        art.boss(c,b.id,e.boss.x.toFloat(),e.boss.y.toFloat()+5-lift+sin(clock*3).toFloat()*2,160f,110f,alpha=motion?.opacity ?: 255)
        p.color=Ink.light; p.style=Paint.Style.STROKE; p.strokeWidth=1f
        c.drawOval(e.boss.x.toFloat()-34,e.boss.y.toFloat()-9,e.boss.x.toFloat()+34,e.boss.y.toFloat()+17,p)
        p.style=Paint.Style.FILL
        for(s in e.summons) {
            val summonScale=(if(s.kind==0) 1.2 else .85)*(1+.20*Skills.progress(s.level))
            art.sprite(c,when(s.kind) { 0 -> "giant"; 1 -> "rabbit"; else -> "haniwa" },s.x.toFloat(),s.y.toFloat(),summonScale.toFloat())
            bar(c,s.x.toFloat()-14,s.y.toFloat()+8,28f,8f,s.life,if(s.finisher) Skills.finisherDuration(e.job,s.level) else Skills.summonDuration(s.kind,s.level))
            if(s.finisher) pixel(c,"V",s.x.toFloat(),s.y.toFloat()-47,1.0f,Ink.paper,true)
        }
        val px=e.player.x.toFloat(); val py=e.player.y.toFloat()
        if((e.buffs["clones"] ?: .0)>0) {
            art.sprite(c,art.heroKey(e.job),px-32,py+7,1.1f,alpha=135)
            art.sprite(c,art.heroKey(e.job),px+32,py+7,1.1f,alpha=135)
        }
        val alpha=if(e.isInvisible) 70 else if(e.invulnerability>0) 155 else 255
        art.sprite(c,art.heroKey(e.job),px,py,1.15f,alpha=alpha)
        if((e.buffs["armor"] ?: .0)>0) {
            p.style=Paint.Style.STROKE; p.strokeWidth=2f; c.drawCircle(px,py-15,24f,p); p.style=Paint.Style.FILL
        }
        if((e.buffs["vanish"] ?: 0.0)>0) {
            pixel(c,"VANISH",px,py-61,1.05f,Ink.light,true)
            bar(c,px-24,py-48,48f,5f,e.buffs.getValue("vanish"),10.0)
        }
        e.projectiles.forEach { playerEffects.projectile(c,it) }
        e.particles.forEach { q ->
            pixel(c,q.text.replace('−','-'),q.x.toFloat()+1,q.y.toFloat()+1,1.7f,Ink.dark,true)
            pixel(c,q.text.replace('−','-'),q.x.toFloat(),q.y.toFloat(),1.7f,Ink.light,true)
        }
        // The seven-unit collision marker stays above every skill and damage number.
        p.style=Paint.Style.STROKE; p.color=Ink.dark; p.strokeWidth=4f; c.drawCircle(px,py,7f,p)
        p.color=Ink.light; p.strokeWidth=2f; c.drawCircle(px,py,7f,p); p.style=Paint.Style.FILL
        rect(c,px-2,py-2,4f,4f,Ink.light)
        c.restore()
        // Cast information stays outside the playfield so it never hides a telegraph.
        if(e.castEnd>e.elapsed) {
            val color=if(e.ultimateActive) UltimateColors.forBoss(b.id).energy else Ink.light
            text(c,e.castName,28f,455f,if(e.castName.length>19) 11f else 14f,color)
            bar(c,28f,464f,241f,8f,e.castEnd-e.elapsed,e.castDuration.coerceAtLeast(.001),color)
        } else pixel(c,"READ. DODGE. STRIKE.",28f,456f,1.3f,Ink.mid)
        val hint=if(e.messageTime>0) e.message else e.castHint
        wrap(c,hint,282f,451f,340f+extra,12f,Ink.light,17f)
        // Floating thumb stick. Anywhere in the left half of the arena can be used.
        p.color=Ink.dark; c.drawCircle(103f,374f,48f,p); p.style=Paint.Style.STROKE; p.color=Ink.mid; p.strokeWidth=2f; c.drawCircle(103f,374f,47f,p); p.style=Paint.Style.FILL
        rect(c,77f,370f,52f,8f,Ink.deep); rect(c,99f,348f,8f,52f,Ink.deep)
        p.color=Ink.mid; c.drawCircle(103f+stickX*25,374f+stickY*25,18f,p)
        p.color=Ink.light; c.drawCircle(103f+stickX*25,374f+stickY*25,5f,p)
        shifted(c,extra) {
            rect(c,653f,72f,283f,221f,Ink.deep); border(c,653f,72f,283f,221f)
            art.sprite(c,art.heroKey(e.job),687f,127f,1.05f)
            text(c,e.job.label,723f,101f,19f); pixel(c,"${e.elapsed.toInt()/60}:${(e.elapsed.toInt()%60).toString().padStart(2,'0')}",861f,89f,1.7f,Ink.light)
            text(c,"HP ${ceil(e.player.hp).toInt()} / ${e.player.maxHp.toInt()}",723f,123f,13f)
            bar(c,671f,141f,247f,15f,e.player.hp,e.player.maxHp)
            text(c,if(e.job==Job.SUMMONER) "召喚ゲージ  +10/s" else "アクションゲージ  +20/s",671f,181f,12f,Ink.mid)
            bar(c,671f,190f,247f,12f,e.gauge,100.0)
            text(c,"${r.gold} G",671f,229f,17f); pixel(c,"SCORE ${r.score}",768f,218f,1.25f,Ink.mid)
            val buffNames=mapOf("shield" to "盾","focus" to "魔力","power" to "攻↑","armor" to "守↑","haste" to "速↑","speed" to "気合","invisible" to "無敵","vanish" to "無影","clones" to "三影")
            val active=e.buffs.filter { it.value>0 }.entries.joinToString(" ") { "${buffNames[it.key]}${ceil(it.value).toInt()}s" }
            text(c,if(active.isEmpty()) (if(e.job==Job.SUMMONER) "通常 ${e.normalSummons}/2 ・ 必殺 ${e.summons.count { it.finisher }}/5" else "足元の小さな丸が当たり判定") else active,671f,260f,12f,Ink.light)
            val guard=e.summons.firstOrNull { it.kind==2 }
            if(guard!=null) text(c,"はにわ 耐久${guard.guardHits}/${Skills.haniwaDurability(guard.level)}回 ・ 残り${ceil(guard.life).toInt()}秒${if(e.fortune) " ・ 金×3" else ""}",671f,281f,11f,Ink.light)
            else if(e.fortune) text(c,"黄金の印：報酬 ×3",671f,281f,11f,Ink.mid)
            Skills.all.getValue(e.job).forEachIndexed { i,s ->
                val x=653f+(i%2)*145; val y=310f+(i/2)*88
                val ready=if(i==3) e.canUsePlayerFinisher else e.cooldowns[i]<=0
                val held=e.heldSkill==i
                rect(c,x,y,138f,77f,if(held) Ink.light else Ink.deep); border(c,x,y,138f,77f,if(i==3&&ready) Color.rgb(248,222,146) else if(ready) Ink.light else Ink.mid)
                art.icon(c,s.glyph,x+11,y+12,1.7f,if(held) Ink.dark else Ink.light)
                pixel(c,"${if(controller.active) listOf("A","B","X","Y")[i]+" / " else ""}LV${e.levels[i]}",x+(if(controller.active) 54 else 92),y+11,1.2f,if(held) Ink.dark else Ink.mid)
                text(c,if(e.job==Job.SUMMONER&&i==2&&e.summons.any { it.kind==2 }) "はにわで殴る" else s.name,x+69,y+58,if(s.name.length>7) 13f else 15f,if(held) Ink.dark else Ink.light,Paint.Align.CENTER)
                if(i==3) {
                    text(c,e.finisherStatus,x+80,y+36,11f,if(held) Ink.dark else if(ready) Color.rgb(248,222,146) else Ink.mid,Paint.Align.CENTER)
                } else if(!ready && e.cooldowns[i]>0) {
                    rect(c,x+3,y+68,(132*e.cooldowns[i]/Skills.cooldown(e.job,i,e.levels[i])).toFloat(),5f,Ink.mid)
                    text(c,"%.1fs".format(java.util.Locale.ROOT,e.cooldowns[i]),x+62,y+28,12f,if(held) Ink.dark else Ink.light)
                }
                buttons.add(UiButton("技${i+1} ${s.name}",RectF(x,y,x+138,y+77),true,i) { e.useSkill(i) })
            }
        }
        pixel(c,"ITEMS",29f,500f,1.4f,Ink.mid)
        for(i in 0..4) {
            val x=116f+i*(99+extra/4)
            rect(c,x,484f,89f,41f,Ink.deep); border(c,x,484f,89f,41f,Ink.mid,1f)
            if(i<r.inventory.size) {
                if(controller.active && i==controller.item) border(c,x-2,482f,93f,45f,Ink.light,2f)
                val item=r.inventory[i]; art.icon(c,item.icon,x+7,491f,1.6f)
                text(c,item.title.take(3),x+40,508f,12f)
                buttons.add(UiButton("アイテム${i+1} ${item.title}",RectF(x,484f,x+89,525f)) { e.selectedItem=i; e.pause() })
            } else text(c,"—",x+45,509f,14f,Ink.mid,Paint.Align.CENTER)
        }
        text(c,if(controller.active) "L1 / R1 選択・L2 アイテム" else "技は長押しで連続使用",795f+extra,502f,12f,Ink.mid,Paint.Align.CENTER)
        text(c,if(controller.active) "Y 必殺技：HP1/3以下・各戦1回" else "必殺技はHP1/3以下・各ボス戦1回",795f+extra,523f,11f,Ink.mid,Paint.Align.CENTER)
    }
    private fun scrim(c: Canvas) { fullRect(c,Color.argb(225,16,29,26)); buttons.clear() }
    private fun cutin(c: Canvas) {
        scrim(c)
        val b=engine.bossInfo
        val colors=UltimateColors.forBoss(b.id)
        battleEffects.cutin(c,b.id,viewport.fullLeft,130f,viewport.fullWidth,264f,engine.screenAge)
        border(c,viewport.fullLeft,128f,viewport.fullWidth,268f,colors.cutinEdge,2f)
        shifted(c,extra/2) {
            art.boss(c,b.id,228f,356f,272f,164f)
            pixel(c,"LIMIT BREAK",490f,218f,2.8f,colors.energy)
            text(c,b.ultimate,490f,282f,24f,colors.core)
            text(c,b.name,490f,321f,18f,colors.accent)
            text(c,"残り1/2 ── 神々の真なる力",480f,100f,18f,colors.core,Paint.Align.CENTER)
            wrap(c,b.hint,170f,430f,620f,19f,Ink.light,30f)
            pixel(c,"READ THE SIGNS",480f,502f,1.7f,Ink.mid,true)
        }
    }
    private fun paused(c: Canvas) {
        scrim(c)
        shifted(c,extra/2) {
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
    }
    private fun reward(c: Canvas) {
        val e=engine
        header(c,"VICTORY",false)
        pixel(c,"BOSS DEFEATED",35f,82f,3.6f)
        text(c,"${e.bossInfo.name}を越えた。",37f,134f,17f,Ink.mid)
        text(c,"%.1f秒  /  被ダメージ %.0f  /  +%d G".format(java.util.Locale.ROOT,e.lastTime,e.lastDamage,e.lastGold),37f,165f,16f)
        pixel(c,"+${e.lastScore}",704f+extra,105f,3.4f,Ink.light)
        val thief=e.job==Job.THIEF
        text(c,if(e.pendingUpgrade>=0) "${Skills.all.getValue(e.job)[e.pendingUpgrade].name}を選択中・まだ確定していません" else "強化する技を1つ選択",37f,202f,19f)
        Skills.all.getValue(e.job).forEachIndexed { i,s ->
            val x=36f+i*(226+extra/3); val y=221f
            val selected=e.pendingUpgrade==i
            rect(c,x,y,211f,if(thief) 113f else 157f,if(selected) Ink.dark else Ink.deep)
            border(c,x,y,211f,if(thief) 113f else 157f,if(selected) Ink.light else Ink.mid,if(selected) 3f else 2f)
            art.icon(c,s.glyph,x+14,y+17,2f)
            text(c,s.name,x+62,y+36,15f)
            text(c,if(e.levels[i]>=16) "Lv.16 MAX" else "Lv.${e.levels[i]} → ${e.levels[i]+1}",x+16,y+70,20f)
            val preview=e.levels[i]+if(selected) 1 else 0
            text(c,Skills.detail(e.job,i,preview),x+16,y+if(thief) 91 else 99,11f,Ink.mid)
            text(c,Skills.rangeDetail(e.job,i,preview),x+16,y+if(thief) 107 else 117,10f,if(selected) Ink.light else Ink.mid)
            if(!thief) wrap(c,Skills.description(e.job,i,preview),x+16,y+135,178f,11f,Ink.mid,16f)
            buttons.add(UiButton("${s.name}を選択",RectF(x,y,x+211,y+(if(thief) 113 else 157)),e.levels[i]<16) { e.selectUpgrade(i) })
        }
        if(thief) {
            text(c,if(e.lootChosen) "特殊アイテムを獲得しました" else "盗賊の戦利品：4つから1つ選ぶ",37f,364f,16f)
            Item.entries.filter { it.special }.forEachIndexed { i,item ->
                val x=36f+i*(226+extra/3)
                button(c,item.title,x,378f,211f,34f,enabled=!e.lootChosen) { e.chooseLoot(item) }
                text(c,item.description,x+105,429f,10f,Ink.mid,Paint.Align.CENTER)
            }
            if(e.pendingLoot!=null) {
                text(c,"交換する所持品を選択：",37f,459f,14f)
                e.run!!.inventory.forEachIndexed { i,item -> button(c,item.title,217f+i*(141+extra/4),441f,135f,27f) { e.replaceLoot(i) } }
            } else text(c,"特殊アイテムもストック5個に含まれます。",37f,459f,12f,Ink.mid)
        } else {
            text(c,if(e.job==Job.SUMMONER) "はにわは最大4回・20秒。白ウサギの回復は8〜16。" else "威力・効果はLv.1で最大の25%。Lv.16まで直線的に成長。",37f,421f,14f,Ink.mid)
            text(c,"範囲も拡大し、待機時間も短くなります。",37f,447f,14f,Ink.mid)
        }
        button(c,"技の選択をキャンセル",36f,476f,211f,45f,enabled=e.pendingUpgrade>=0) { e.cancelUpgrade() }
        text(c,"進むと強化が確定します",278f,504f,14f,Ink.mid)
        button(c,if(e.run!!.storyEnabled) "物語のつづきへ →" else if(e.run!!.stage==31) "夜明けへ  →" else "旅の商人へ  →",674f+extra,476f,250f,45f,true,e.canFinishReward) { e.finishReward() }
    }
    private fun shop(c: Canvas) {
        val e=engine; val r=e.run!!
        header(c,"THE WANDERING MERCHANT")
        pixel(c,"A MOMENT OF REST",36f,83f,2.8f)
        text(c,"「次の神に挑む前に、旅の支度はいかが？」",36f,132f,16f,Ink.mid)
        text(c,"${r.gold} G",920f+extra,111f,27f,Ink.light,Paint.Align.RIGHT)
        Item.entries.filter { !it.special }.forEachIndexed { i,item ->
            val x=36f+i*(226+extra/3)
            rect(c,x,159f,211f,198f,Ink.deep); border(c,x,159f,211f,198f)
            art.icon(c,item.icon,x+81,178f,3f)
            text(c,item.title,x+105,251f,18f,Ink.light,Paint.Align.CENTER)
            text(c,item.description,x+105,281f,12f,Ink.mid,Paint.Align.CENTER)
            button(c,"${item.price} G  購入",x+16,307f,179f,35f,enabled=r.gold>=item.price&&r.inventory.size<5) { e.buy(item) }
        }
        text(c,"旅のかばん  ${r.inventory.size}/5",36f,393f,18f)
        for(i in 0..4) {
            val x=36f+i*(179+extra/4)
            val item=r.inventory.getOrNull(i)
            if(item!=null) button(c,item.title,x,410f,168f,44f) {
                controller.prepareDialog(AlertDialog.Builder(context).setTitle(item.title).setMessage("${item.description}\n\nこのアイテムを手放して、かばんに空きを作りますか？")
                    .setPositiveButton("手放す") { _,_ -> e.discardItem(i) }.setNegativeButton("戻る",null).show())
            } else { border(c,x,410f,168f,44f,Ink.deep); text(c,"空き",x+84,438f,13f,Ink.mid,Paint.Align.CENTER) }
        }
        text(c,"${if(e.messageTime>0) e.message else "ボス戦ごとにHPとゲージは全回復。ここで自動保存されます。"}",36f,499f,13f,Ink.mid)
        button(c,"次のボスへ  →",675f+extra,475f,249f,46f,true) { e.leaveShop() }
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
        if(!portraitExpanded) contentDescription="BOSSRUSH 神話図鑑 ${b.name}。${b.epithet}。${b.lore}"
        shifted(c,extra) {
            rect(c,459f,81f,465f,381f,Ink.deep)
            art.boss(c,b.id,538f,232f,145f,143f)
            text(c,"タップで拡大",538f,248f,10f,Ink.mid,Paint.Align.CENTER)
            buttons.add(UiButton("${b.name}の姿を拡大",RectF(465f,84f,609f,253f)) { portraitExpanded=true })
            text(c,b.name,614f,121f,24f)
            text(c,b.epithet,614f,150f,13f,Ink.mid)
            wrap(c,b.lore,614f,180f,289f,14f)
            text(c,b.ultimate,481f,270f,19f)
            wrap(c,b.hint,481f,302f,418f,15f)
            text(c,"♪ ${b.theme}",481f,378f,17f)
            text(c,"${b.bpm} BPM  /  ${BattleScore.themes[engine.selectedBoss].beats}拍子",481f,401f,13f,Ink.mid)
            wrap(c,BattleScore.themes[engine.selectedBoss].character,481f,426f,418f,12f,Ink.light,18f)
        }
        button(c,"←",36f,483f,62f,36f,enabled=codexPage>0) { codexPage--; engine.selectedBoss=codexPage*8 }
        pixel(c,"${codexPage+1} / 4",233f,496f,1.5f,Ink.mid,true)
        button(c,"→",364f,483f,62f,36f,enabled=codexPage<3) { codexPage++; engine.selectedBoss=codexPage*8 }
        text(c,"参考：散文エッダ / 古エッダ（詳細はREADME）",481f+extra,506f,13f,Ink.mid)
        if(portraitExpanded) portrait(c)
    }
    private fun portrait(c: Canvas) {
        val b=Bosses.all[engine.selectedBoss]
        contentDescription="BOSSRUSH 神話図鑑 拡大 ${b.name}。${b.epithet}。${b.lore}"
        fullRect(c,Ink.dark)
        border(c,22f,22f,916f+extra,496f,Ink.mid)
        pixel(c,"BESTIARY / ${(engine.selectedBoss+1).toString().padStart(2,'0')}",48f,45f,1.8f,Ink.mid)
        shifted(c,extra) {
            text(c,b.name,645f,169f,28f)
            wrap(c,b.epithet,647f,204f,255f,15f,Ink.mid)
            wrap(c,b.lore,647f,249f,255f,15f,Ink.light,25f)
        }
        text(c,b.realm,326f+extra/2,488f,16f,Ink.mid,Paint.Align.CENTER)
        art.boss(c,b.id,326f+extra/2,455f,530f+extra,363f)
        // Only the modal controls remain in the virtual accessibility tree.
        buttons.clear()
        shifted(c,extra) {
            button(c,"図鑑へ戻る",789f,40f,123f,37f) { portraitExpanded=false }
            button(c,"前の神",649f,446f,118f,44f,enabled=engine.selectedBoss>0) {
                engine.selectedBoss--; codexPage=engine.selectedBoss/8
            }
            button(c,"次の神",785f,446f,118f,44f,enabled=engine.selectedBoss<31) {
                engine.selectedBoss++; codexPage=engine.selectedBoss/8
            }
        }
    }
    private fun help(c: Canvas) {
        header(c,"HOW TO PLAY")
        pixel(c,"READ. DODGE. STRIKE.",36f,83f,2.8f)
        button(c,if(controllerHelp) "基本の遊び方" else "コントローラー操作",692f+extra,74f,232f,39f) { controllerHelp=!controllerHelp }
        val topics=if(controllerHelp) listOf(
            "01  接続と移動" to "AndroidにBluetoothまたはUSBで接続して操作。左スティック／十字キーで移動します。スティックは倒し具合で速度が変わります。",
            "02  4つの技" to "A＝技1、B＝技2、X＝技3、Y＝技4。押し続けると連続使用。ボタンの配置は機種で異なるので、戦闘画面のA・B・X・Y表示を確認しましょう。",
            "03  アイテム" to "L1／R1でかばんの選択枠を移動。L2で時間を止めて効果を確認。Aで使用、Bでキャンセルします。何度も押しても一度に1個だけ使います。",
            "04  メニューと一時停止" to "左スティック／十字キーで白い選択枠を移動、Aで決定、Bで戻ります。強化画面ではBで選択を取消。STARTで一時停止／再開します。",
            "05  ボタン表記" to "A／B／X／YはAndroidの標準ボタン名です。L1・R1は上側の肩ボタン、L2は左トリガー。確認ダイアログ内の選択には十字キーを使います。",
            "06  安心して再開" to "操作中のコントローラーが切断されると戦闘を一時停止します。再接続してSTARTで再開。タッチ操作にもいつでも切り替えられます。"
        ) else listOf(
            "01  移動と攻撃" to "戦場の左半分をドラッグして移動。右の技をタップ、長押しで連続使用。攻撃は自動でボスの方向を狙います。足元の丸が当たり判定です。",
            "02  予兆を読む" to "斜線は危険地帯。突進は帯の横へ、飛び込みは着地点の円の外へ。輪・月印・白いルーンは内側へ。吹き飛ばしは中央へ。前後攻撃は切り返します。",
            "03  技と召喚" to "技にはゲージと待機時間が必要。召喚士は回復速度が半分で仲間は2体まで。はにわは成長で耐久1〜4回・5〜20秒。再タップで近接攻撃。白ウサギの回復は8〜16。",
            "04  必殺技とアイテム" to "4番目の技はHP1/3以下で各ボス戦1回だけ使える必殺技。ゲージ消費なし。回復には薬草や白ウサギを使います。下のアイテムを選ぶと時間が止まり、効果を確認できます。かばんは5個まで。",
            "05  成長と物語" to "撃破後に技を選び、次へ進むと確定。Lv.16が最大。盗賊は特殊品も選択。物語は前後のページへ移動・スキップが可能。ページごと、戦闘前、買い物後に自動保存。",
            "06  高いスコアへ" to "素早く倒し、被ダメージを減らすと高得点。全32体を越えると世界に色が戻ります。物理キー：WASD/矢印で移動、1〜4で技、Escで一時停止。"
        )
        topics.forEachIndexed { i,pair ->
            val x=36f+(i%2)*(455+extra); val y=145f+(i/2)*123
            text(c,pair.first,x,y,18f)
            wrap(c,pair.second,x,y+27,420f,14f,Ink.mid,23f)
        }
    }
    private fun result(c: Canvas,clear: Boolean) {
        val r=engine.run!!
        if(clear) landscape(c,true) else landscape(c)
        if(!clear) fullRect(c,Color.argb(200,16,29,26))
        header(c,if(clear) "A NEW DAWN" else "THE JOURNEY ENDS",false)
        pixel(c,if(clear) "THE WORLD" else "GAME OVER",40f,103f,4.5f,if(clear) Ink.dawn[3] else Ink.light)
        if(clear) pixel(c,"IN COLOR",40f,155f,4.5f,Ink.dawn[2])
        text(c,if(clear) "世界に、色が戻った。" else "夜は、まだ明けない。",40f,if(clear) 233f else 188f,26f,if(clear) Ink.dawn[3] else Ink.light)
        wrap(c,if(clear) "最後の神が槍を下ろした。\n灰色だった葉に緑が、空に青が宿る。\n旅人の帰る道にも、新しい朝が来た。" else "倒れるたび、予兆は記憶になる。\n次の冒険では、きっと一歩先へ。",40f,if(clear) 274f else 234f,505f,16f,if(clear) Ink.dawn[3] else Ink.mid,26f)
        rect(c,39f,365f,462f,93f,Ink.dark); border(c,39f,365f,462f,93f,if(clear) Ink.dawn[2] else Ink.mid)
        pixel(c,"SCORE ${r.score.toString().padStart(6,'0')}",58f,383f,2.4f,if(clear) Ink.dawn[3] else Ink.light)
        text(c,"${r.job.label}  /  ${r.kills}体撃破  /  %.1f秒  /  被ダメージ %.0f".format(java.util.Locale.ROOT,r.totalTime,r.totalDamage),58f,438f,13f,if(clear) Ink.dawn[2] else Ink.mid)
        button(c,"タイトルへ",40f,478f,215f,44f,true) { engine.changeScreen(Screen.TITLE) }
        if(clear) shifted(c,extra) {
            rect(c,566f,fullBottom-66f,358f,49f,Color.argb(215,16,29,26))
            text(c,"THANK YOU FOR PLAYING",736f,fullBottom-48f,15f,Ink.dawn[3],Paint.Align.CENTER)
            text(c,"BOSSRUSH / ORIGINAL ART & MUSIC",736f,fullBottom-26f,11f,Ink.dawn[2],Paint.Align.CENTER)
        }
        else button(c,"もう一度挑む",274f,478f,227f,44f) { engine.changeScreen(Screen.JOBS) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index=event.actionIndex; val id=event.getPointerId(index)
        val x=(event.getX(index)-ox)/scale; val y=(event.getY(index)-oy)/scale
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN -> {
                controller.touch()
                val b=buttons.lastOrNull { it.rect.contains(x,y) }
                if(b!=null) {
                    if(b.enabled) {
                        pressed=b.label
                        if(b.skill>=0 && engine.screen==Screen.BATTLE) { skillPointer=id; touchSkill=b.skill; refreshControls(); b.action() }
                    }
                } else if(engine.screen==Screen.BATTLE && x>=0 && x<640+extra && y in 95f..433f && joystickId<0) {
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
                if(id==joystickId) { joystickId=-1; stickX=0f; stickY=0f; refreshControls() }
                else if(id==skillPointer) { skillPointer=-1; touchSkill=-1; refreshControls(); pressed=null }
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
        refreshControls()
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onKeyDown(keyCode: Int,event: KeyEvent): Boolean = controller.key(keyCode,event) || super.onKeyDown(keyCode,event)
    override fun onKeyUp(keyCode: Int,event: KeyEvent): Boolean = controller.key(keyCode,event) || super.onKeyUp(keyCode,event)
    override fun onGenericMotionEvent(event: MotionEvent): Boolean = controller.motion(event) || super.onGenericMotionEvent(event)
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
