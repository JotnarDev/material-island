package dev.jotnar.materialisland.core

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.jotnar.materialisland.data.IdleAction
import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.model.IslandAction
import dev.jotnar.materialisland.ui.MainActivity

/** Vibraciones cortas con las primitivas del Pixel (se sienten mucho más finas que un vibrate()). */
object Haptics {
    private var vibrator: Vibrator? = null

    fun init(ctx: Context) {
        vibrator = ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
    }

    private fun play(primitive: Int, scale: Float) {
        if (!SettingsStore.current.haptics) return
        val v = vibrator ?: return
        runCatching {
            if (v.areAllPrimitivesSupported(primitive)) {
                v.vibrate(VibrationEffect.startComposition().addPrimitive(primitive, scale).compose())
            } else {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }
        }
    }

    fun tick() = play(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
    fun click() = play(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
    fun expand() = play(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f)
    fun collapse() = play(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.5f)
    fun arrive() = play(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1f)
}

object Actions {
    /** Lanza un PendingIntent desde segundo plano (permitido por tener la ventana visible). */
    fun send(ctx: Context, pi: PendingIntent?) {
        pi ?: return
        val opts = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 36) {
            opts.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        } else {
            @Suppress("DEPRECATION")
            opts.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        runCatching { pi.send(ctx, 0, null, null, null, null, opts.toBundle()) }
    }

    fun run(ctx: Context, action: IslandAction) {
        Haptics.click()
        when {
            action.onClick != null -> action.onClick.invoke()
            else -> send(ctx, action.intent)
        }
    }

    fun openApp(ctx: Context, pkg: String?, fallback: PendingIntent?) {
        if (fallback != null) {
            send(ctx, fallback); return
        }
        pkg ?: return
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(it) }
        }
    }

    private var torchOn = false

    fun idle(service: AccessibilityService?, ctx: Context, action: IdleAction) {
        if (action == IdleAction.NONE) return
        Haptics.click()
        when (action) {
            IdleAction.NONE -> Unit
            IdleAction.FLASHLIGHT -> toggleTorch(ctx)
            IdleAction.SCREENSHOT -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            IdleAction.NOTIFICATIONS -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            IdleAction.QUICK_SETTINGS -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            IdleAction.LOCK_SCREEN -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            IdleAction.OPEN_ISLAND_APP -> ctx.startActivity(
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun toggleTorch(ctx: Context) {
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        torchOn = !torchOn
        runCatching { cm.setTorchMode(id, torchOn) }
    }
}
