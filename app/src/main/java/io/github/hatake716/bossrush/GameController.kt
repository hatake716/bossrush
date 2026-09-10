package io.github.hatake716.bossrush

import android.app.AlertDialog
import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.*

/** Android gamepad events, independent of touch and TalkBack focus. */
internal class GameController(private val view: GameView): InputManager.InputDeviceListener {
    private val e get()=view.engine
    private val manager=view.context.getSystemService(Context.INPUT_SERVICE) as InputManager
    var active=false; private set
    var focusLabel: String?=null; private set
    private var focusKey: String?=null
    var item=0; private set
    private var screen=e.screen
    private var deviceId: Int?=null
    private var axis=ControllerMath.Point(0f,0f)
    private var hat=ControllerMath.Point(0f,0f)
    private var nav=ControllerMath.Point(0f,0f)
    private var nextNavigation=0L
    private var triggerDown=false
    private var rightTriggerDown=false
    private var cutinHatDown=false
    // Physical edges survive scene changes; held attack buttons cannot dismiss a new cut-in.
    private val physicalDown=mutableSetOf<Int>()
    private val consumedUntilUp=mutableSetOf<Int>()
    private val down=linkedSetOf<Int>()
    private val skills=linkedMapOf<Int,Int>()
    val heldSkill get()=skills.values.lastOrNull() ?: -1
    val movement: ControllerMath.Point get() {
        val x=(if(down.any { it==KeyEvent.KEYCODE_D || it==KeyEvent.KEYCODE_DPAD_RIGHT }) 1f else 0f)-
            (if(down.any { it==KeyEvent.KEYCODE_A || it==KeyEvent.KEYCODE_DPAD_LEFT }) 1f else 0f)
        val y=(if(down.any { it==KeyEvent.KEYCODE_S || it==KeyEvent.KEYCODE_DPAD_DOWN }) 1f else 0f)-
            (if(down.any { it==KeyEvent.KEYCODE_W || it==KeyEvent.KEYCODE_DPAD_UP }) 1f else 0f)
        return when { x!=0f || y!=0f -> ControllerMath.Point(x,y); hat.x!=0f || hat.y!=0f -> hat; else -> axis }
    }
    fun attach() { manager.registerInputDeviceListener(this,null) }
    fun detach() { manager.unregisterInputDeviceListener(this); reset() }
    fun reset() {
        down.clear(); skills.clear(); axis=ControllerMath.Point(0f,0f); hat=axis; nav=axis
        nextNavigation=0
        view.refreshControls()
    }
    private fun syncScreen() {
        if(screen!=e.screen) { screen=e.screen; focusLabel=null; focusKey=null; reset() }
    }
    private fun mark(eventDeviceId: Int) { active=true; deviceId=eventDeviceId; syncScreen(); view.invalidate() }
    fun touch() { active=false }
    private fun choices()=view.controllerButtons().filter { (it.enabled || it.upgradeSlot>=0) && it.skill<0 }
    private fun identity(button: UiButton)="${button.label}:${button.rect.left}:${button.rect.top}"
    private fun focus(button: UiButton?) { focusLabel=button?.label; focusKey=button?.let { identity(it) } }
    fun focused(): UiButton? {
        syncScreen()
        if(view.buttonsScreen!=e.screen) return null
        val choices=choices()
        val current=choices.firstOrNull { identity(it)==focusKey }
            ?: choices.firstOrNull { e.screen==Screen.REWARD && it.upgradeSlot==0 }
            ?: choices.firstOrNull { it.rect.top>=60 } ?: choices.firstOrNull()
        focus(current)
        return current
    }
    private fun navigate(dx: Float,dy: Float) {
        if(e.screen in listOf(Screen.BATTLE,Screen.CUTIN,Screen.DEFEAT)) return
        val current=focused() ?: return
        val choices=choices()
        val i=ControllerMath.neighbor(choices.map { ControllerMath.Point(it.rect.centerX(),it.rect.centerY()) },choices.indexOf(current),dx,dy)
        focus(choices.getOrNull(i))
        view.invalidate()
    }
    private fun confirm() {
        focused()?.takeIf { it.enabled }?.let { it.action(); view.audio.effect("click"); view.invalidate() }
    }
    private fun chooseItem(delta: Int) {
        val count=e.run?.inventory?.size ?: 0
        if(count>0) item=Math.floorMod(item.coerceIn(0,count-1)+delta,count)
        view.invalidate()
    }
    private fun openItem() {
        val count=e.run?.inventory?.size ?: 0
        if(e.screen==Screen.BATTLE && count>0) {
            item=item.coerceIn(0,count-1); e.selectedItem=item; e.pause(); view.invalidate()
        }
    }
    private fun skill(key: Int)=when(key) {
        KeyEvent.KEYCODE_BUTTON_A -> 0; KeyEvent.KEYCODE_BUTTON_B -> 1
        KeyEvent.KEYCODE_BUTTON_X -> 2; KeyEvent.KEYCODE_BUTTON_Y -> 3
        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_4 -> key-KeyEvent.KEYCODE_1
        else -> -1
    }
    private val movementKeys=setOf(KeyEvent.KEYCODE_W,KeyEvent.KEYCODE_A,KeyEvent.KEYCODE_S,KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN)
    private val actionKeys=setOf(KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_X,KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1,KeyEvent.KEYCODE_BUTTON_R1,KeyEvent.KEYCODE_BUTTON_L2,KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_DPAD_CENTER)
    fun key(key: Int,event: KeyEvent): Boolean {
        val gameButton=KeyEvent.isGamepadButton(key) || (key==KeyEvent.KEYCODE_BACK && event.isFromSource(InputDevice.SOURCE_GAMEPAD))
        if(key !in movementKeys && key !in actionKeys && skill(key)<0 && !gameButton && key!=KeyEvent.KEYCODE_SPACE) return false
        mark(event.deviceId)
        if(event.action==KeyEvent.ACTION_UP) {
            physicalDown.remove(key); consumedUntilUp.remove(key)
            down.remove(key); skills.remove(key)
            if(key==KeyEvent.KEYCODE_BUTTON_L2) triggerDown=false
            view.refreshControls(); return true
        }
        if(event.action!=KeyEvent.ACTION_DOWN) return false
        val fresh=physicalDown.add(key) && event.repeatCount==0
        if(key in consumedUntilUp) return true
        if(e.screen==Screen.CUTIN || e.screen==Screen.DEFEAT) {
            if(fresh && e.screen==Screen.CUTIN) {
                // Some pads report a trigger as both a key and an axis in the same press.
                if(key==KeyEvent.KEYCODE_BUTTON_L2) triggerDown=true
                if(key==KeyEvent.KEYCODE_BUTTON_R2) rightTriggerDown=true
                consumedUntilUp.add(key); e.dismissCutin(); reset()
            }
            return true
        }
        if(key in movementKeys) {
            down.add(key)
            if(e.screen==Screen.BATTLE) view.refreshControls()
            else when(key) {
                KeyEvent.KEYCODE_W,KeyEvent.KEYCODE_DPAD_UP -> navigate(0f,-1f)
                KeyEvent.KEYCODE_S,KeyEvent.KEYCODE_DPAD_DOWN -> navigate(0f,1f)
                KeyEvent.KEYCODE_A,KeyEvent.KEYCODE_DPAD_LEFT -> navigate(-1f,0f)
                else -> navigate(1f,0f)
            }
            return true
        }
        // Android's key repeat must never buy, consume or confirm twice.
        if(event.repeatCount>0 || !down.add(key)) return true
        when {
            key==KeyEvent.KEYCODE_BUTTON_START -> {
                if(e.screen in listOf(Screen.BATTLE,Screen.CUTIN)) e.pause()
                else if(e.screen==Screen.PAUSED) e.unpause()
            }
            key==KeyEvent.KEYCODE_ESCAPE || key==KeyEvent.KEYCODE_BACK -> view.goBack()
            e.screen==Screen.BATTLE && skill(key)>=0 -> {
                skills[key]=skill(key); view.refreshControls(); e.useSkill(skill(key))
            }
            e.screen==Screen.BATTLE && key==KeyEvent.KEYCODE_BUTTON_L1 -> chooseItem(-1)
            e.screen==Screen.BATTLE && key==KeyEvent.KEYCODE_BUTTON_R1 -> chooseItem(1)
            key==KeyEvent.KEYCODE_BUTTON_L2 -> { if(!triggerDown) openItem(); triggerDown=true }
            e.screen !in listOf(Screen.BATTLE,Screen.CUTIN) && key in listOf(KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_DPAD_CENTER) -> confirm()
            e.screen!=Screen.CUTIN && key==KeyEvent.KEYCODE_BUTTON_B -> view.goBack()
        }
        view.invalidate(); return true
    }
    fun motion(event: MotionEvent): Boolean {
        if(event.actionMasked!=MotionEvent.ACTION_MOVE || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        mark(event.deviceId)
        val device=event.device
        val flat=max(device?.getMotionRange(MotionEvent.AXIS_X,event.source)?.flat ?: 0f,
            device?.getMotionRange(MotionEvent.AXIS_Y,event.source)?.flat ?: 0f)
        axis=ControllerMath.stick(event.getAxisValue(MotionEvent.AXIS_X),event.getAxisValue(MotionEvent.AXIS_Y),flat)
        fun hatAxis(id: Int): Float = event.getAxisValue(id).let { if(abs(it)>.5f) sign(it) else 0f }
        hat=ControllerMath.Point(hatAxis(MotionEvent.AXIS_HAT_X),hatAxis(MotionEvent.AXIS_HAT_Y))
        val trigger=max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rightTrigger=max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),event.getAxisValue(MotionEvent.AXIS_GAS))
        val hatPressed=hat.x!=0f || hat.y!=0f
        val dismiss=(trigger>.55f && !triggerDown) || (rightTrigger>.55f && !rightTriggerDown) || (hatPressed && !cutinHatDown)
        rightTriggerDown=if(rightTriggerDown) rightTrigger>=.35f else rightTrigger>.55f
        cutinHatDown=hatPressed
        if(e.screen==Screen.CUTIN || e.screen==Screen.DEFEAT) {
            triggerDown=if(triggerDown) trigger>=.35f else trigger>.55f
            if(dismiss && e.screen==Screen.CUTIN) { e.dismissCutin(); reset() }
            return true
        }
        if(trigger>.55f && !triggerDown) { openItem(); triggerDown=true }
        else if(trigger<.35f && KeyEvent.KEYCODE_BUTTON_L2 !in down) triggerDown=false
        if(e.screen==Screen.BATTLE) view.refreshControls()
        else {
            val direction=if(hat.x!=0f || hat.y!=0f) ControllerMath.direction(hat.x,hat.y) else ControllerMath.direction(axis.x,axis.y)
            if(direction!=nav) {
                nav=direction
                if(nav.x!=0f || nav.y!=0f) navigate(nav.x,nav.y)
                nextNavigation=SystemClock.uptimeMillis()+360
            }
        }
        return true
    }
    fun tick() {
        syncScreen()
        val count=e.run?.inventory?.size ?: 0
        item=item.coerceIn(0,(count-1).coerceAtLeast(0))
        if(active && e.screen !in listOf(Screen.BATTLE,Screen.CUTIN,Screen.DEFEAT) && (nav.x!=0f || nav.y!=0f) && SystemClock.uptimeMillis()>=nextNavigation) {
            navigate(nav.x,nav.y); nextNavigation=SystemClock.uptimeMillis()+150
        }
    }
    override fun onInputDeviceAdded(deviceId: Int) = Unit
    override fun onInputDeviceChanged(deviceId: Int) {
        if(this.deviceId==deviceId && manager.getInputDevice(deviceId)==null) onInputDeviceRemoved(deviceId)
    }
    override fun onInputDeviceRemoved(deviceId: Int) {
        if(this.deviceId==deviceId) {
            this.deviceId=null; focusLost(); e.pause(); view.invalidate()
        }
    }
    fun focusLost() { triggerDown=false; rightTriggerDown=false; cutinHatDown=false; physicalDown.clear(); consumedUntilUp.clear(); reset() }

    /** Native confirmations retain directional focus and accept standard gamepad A/B. */
    fun prepareDialog(dialog: AlertDialog): AlertDialog {
        var confirmHeld=false
        dialog.setOnKeyListener { _,key,event ->
            when(key) {
                KeyEvent.KEYCODE_BUTTON_A -> {
                    if(event.action==KeyEvent.ACTION_DOWN && event.repeatCount==0) confirmHeld=true
                    if(event.action==KeyEvent.ACTION_UP) {
                        // Releasing A after opening the dialog is not a second confirmation.
                        if(confirmHeld && !event.isCanceled) dialog.currentFocus?.performClick()
                        confirmHeld=false
                    }
                    true
                }
                KeyEvent.KEYCODE_BUTTON_B -> { if(event.action==KeyEvent.ACTION_UP) dialog.dismiss(); true }
                else -> false
            }
        }
        if(active) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply { isFocusableInTouchMode=true; requestFocus() }
        return dialog
    }
}
