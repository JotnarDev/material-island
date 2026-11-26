package dev.jotnar.materialisland.overlay

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.jotnar.materialisland.core.Actions
import dev.jotnar.materialisland.core.Demo
import dev.jotnar.materialisland.core.Haptics
import dev.jotnar.materialisland.core.IslandStore
import dev.jotnar.materialisland.core.NotificationParser
import dev.jotnar.materialisland.data.GestureAction
import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.model.IslandAction
import dev.jotnar.materialisland.model.IslandItem
import dev.jotnar.materialisland.model.Kind
import dev.jotnar.materialisland.service.IslandAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.lang.reflect.Proxy
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Dueño de la ventana de la isla. Usa TYPE_ACCESSIBILITY_OVERLAY (idea de Expressive Cutout): se dibuja
 * por encima de la barra de estado sin pedir el permiso de "mostrar sobre otras apps", y solo el
 * rectángulo de la isla recibe toques.
 */
class IslandOverlay(private val service: IslandAccessibilityService) : IslandCallbacks {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    @Volatile private var touchRects: List<Rect> = emptyList()

    private val geometry = MutableStateFlow(computeIslandGeometry(service))
    private val expandedId = MutableStateFlow<String?>(null)
    /** Sube cada vez que se usa un acceso, para que la tarjeta repinte su estado (linterna, sonido). */
    private val shortcutTick = MutableStateFlow(0)
    /** Tercer nivel de la música: tarjeta grande con la carátula. Solo vale mientras está expandida. */
    private val fullMedia = MutableStateFlow(false)
    private val replyingId = MutableStateFlow<String?>(null)
    private val copiedId = MutableStateFlow<String?>(null)
    private val landscape = MutableStateFlow(isLandscape())
    private val locked = MutableStateFlow(false)
    private val fullscreen = MutableStateFlow(false)

    private val hidden: StateFlow<Boolean> = combine(
        landscape, locked, fullscreen, SettingsStore.flow,
    ) { land, lock, full, s ->
        !s.calibration && ((s.hideInLandscape && land) || (s.hideOnLockscreen && lock) || (s.hideInFullscreen && full))
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val ui: StateFlow<IslandUi> = combine(
        IslandStore.state, expandedId, hidden, replyingId, copiedId,
    ) { st, exp, hide, reply, copied ->
        val primary = st.primary
        IslandUi(
            st, expanded = exp != null && exp == primary?.id, hidden = hide, replyingId = reply, copiedId = copied,
            shortcuts = exp == SHORTCUTS_ID && primary == null,
            glance = exp == GLANCE_ID,
        )
    }.combine(IslandStore.unread) { u, unread -> u.copy(unread = unread) }
        .combine(fullMedia) { u, full -> u.copy(full = full && u.expanded && u.state.primary?.kind == Kind.MEDIA) }
        .combine(shortcutTick) { u, t -> u.copy(shortcutTick = t) }
        .combine(dev.jotnar.materialisland.core.Glance.data) { u, g -> u.copy(glanceData = g) }
        .combine(SettingsStore.flow) { u, st -> u.copy(glanceOn = st.glanceEnabled, glance = u.glance && st.glanceEnabled) }.stateIn(scope, SharingStarted.Eagerly, IslandUi(IslandStore.state.value, false, false))

    private var collapseJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refreshLocked()
    }

    // ── Ciclo de vida ───────────────────────────────────────────────────

    fun start() {
        owner.start()
        addWindow()
        service.registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        refreshLocked()
        dev.jotnar.materialisland.core.Glance.start(service)

        // Llamada entrante → se abre sola.
        scope.launch { IslandStore.autoExpandRequests.collect { expand(it) } }
        // Vibración al llegar algo nuevo y limpieza si lo expandido desaparece.
        scope.launch {
            ui.map { it.state.primary?.id to it.state.primary?.kind }.distinctUntilChanged().collect { (id, _) ->
                if (id != null) Haptics.arrive()
                if (expandedId.value != null && expandedId.value != id) collapse(silent = true)
                if (replyingId.value != null && replyingId.value != id) endReply()
            }
        }
        // La ventana solo es táctil cuando hay algo que tocar.
        scope.launch { ui.map { resolveMode(it) }.distinctUntilChanged().collect { applyTouchable() } }
    }

    fun stop() {
        dev.jotnar.materialisland.core.Glance.stop()
        runCatching { service.unregisterReceiver(screenReceiver) }
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        view = null
        owner.stop()
        scope.cancel()
    }

    fun onConfigurationChanged(cfg: Configuration) {
        landscape.value = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
        geometry.value = computeIslandGeometry(service)
    }

    private fun refreshLocked() {
        val km = service.getSystemService(KeyguardManager::class.java)
        locked.value = km?.isKeyguardLocked == true
    }

    private fun isLandscape() = service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ── Ventana ─────────────────────────────────────────────────────────

    private fun windowHeightPx(): Int = (560 * service.resources.displayMetrics.density).toInt()

    private fun addWindow() {
        val v = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val state by ui.collectAsStateWithLifecycle()
                val geo by geometry.collectAsStateWithLifecycle()
                val settings by SettingsStore.flow.collectAsStateWithLifecycle()
                IslandView(state, geo, settings, this@IslandOverlay)
            }
            setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) onOutsideTouch()
                false
            }
            // Pantalla completa: si la barra de estado se oculta (vídeo, juegos), la isla también.
            setOnApplyWindowInsetsListener { _, insets ->
                fullscreen.value = !insets.isVisible(WindowInsets.Type.statusBars())
                insets
            }
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            windowHeightPx(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            title = "MaterialIsland"
            if (Build.VERSION.SDK_INT >= 31) setFitInsetsIgnoringVisibility(true)
        }
        wm.addView(v, p)
        view = v
        params = p
        installTouchableRegion(v)
    }

    private fun updateFlags(block: (Int) -> Int) {
        val v = view ?: return
        val p = params ?: return
        val f = block(p.flags)
        if (f != p.flags) {
            p.flags = f
            runCatching { wm.updateViewLayout(v, p) }
        }
    }

    private fun applyTouchable() {
        val touchable = touchRects.isNotEmpty()
        updateFlags { f ->
            if (touchable) f and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
    }

    private fun setFocusable(focusable: Boolean) = updateFlags { f ->
        if (focusable) f and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    /**
     * Limita la zona táctil de la ventana a la isla mediante el listener oculto
     * OnComputeInternalInsetsListener (por reflexión). Si no está disponible, la ventana sigue
     * funcionando, solo que entera táctil mientras la isla está visible.
     */
    private fun installTouchableRegion(v: View) {
        // touchableRegion está limitado a targetSdk ≤ 30; HiddenApiBypass (LSPosed) lo desbloquea.
        runCatching { HiddenApiBypass.addHiddenApiExemptions("Landroid/view/ViewTreeObserver") }
        runCatching {
            val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val infoClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val setInsets = infoClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val regionField = infoClass.getField("touchableRegion")
            val modeRegion = infoClass.getField("TOUCHABLE_INSETS_REGION").getInt(null)
            val add = ViewTreeObserver::class.java.getMethod("addOnComputeInternalInsetsListener", listenerClass)
            val proxy = Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) { self, m, args ->
                when (m.name) {
                    "onComputeInternalInsets" -> {
                        val info = args?.getOrNull(0)
                        if (info != null) {
                            setInsets.invoke(info, modeRegion)
                            val region = regionField.get(info) as Region
                            region.setEmpty()
                            touchRects.forEach { region.op(it, Region.Op.UNION) }
                        }
                        null
                    }
                    "equals" -> self === args?.getOrNull(0)
                    "hashCode" -> System.identityHashCode(self)
                    "toString" -> "IslandTouchRegion"
                    else -> null
                }
            }
            add.invoke(v.viewTreeObserver, proxy)
        }.onFailure { Log.w(TAG, "Partial touch region unavailable", it) }
    }

    // ── Expandir / contraer ─────────────────────────────────────────────

    private fun expand(id: String) {
        if (expandedId.value == id) return
        fullMedia.value = false
        expandedId.value = id
        Haptics.expand()
        scheduleCollapse()
    }

    private fun collapse(silent: Boolean = false) {
        if (expandedId.value == null) return
        val wasTransient = IslandStore.state.value.transient?.id == expandedId.value
        expandedId.value = null
        fullMedia.value = false
        dev.jotnar.materialisland.core.Glance.cardOpen = false
        endReply()
        if (wasTransient) IslandStore.hold(false)
        if (!silent) Haptics.collapse()
        collapseJob?.cancel()
    }

    private fun scheduleCollapse() {
        collapseJob?.cancel()
        val secs = SettingsStore.current.autoCollapseSeconds
        val item = ui.value.state.primary
        // Las llamadas entrantes no se cierran solas.
        if (secs <= 0 || (item?.kind == Kind.CALL && item.incoming && expandedId.value != GLANCE_ID)) return
        // At a glance se lee con calma: al menos 15 s desde el último gesto.
        val wait = if (expandedId.value == GLANCE_ID) maxOf(secs, 15) else secs
        collapseJob = scope.launch {
            delay(wait * 1000L)
            if (replyingId.value == null) collapse()
        }
    }

    private fun onOutsideTouch() {
        if (replyingId.value != null) return
        if (expandedId.value != null) collapse()
    }

    private fun endReply() {
        if (replyingId.value == null) return
        replyingId.value = null
        setFocusable(false)
    }

    // ── Callbacks de la isla ────────────────────────────────────────────

    override fun onBounds(rects: List<Rect>) {
        touchRects = rects
        applyTouchable()
        view?.invalidate()
    }

    override fun onPressChanged(pressed: Boolean) {
        val st = IslandStore.state.value
        if (st.transient != null && expandedId.value == null && replyingId.value == null) IslandStore.hold(pressed)
        if (!pressed && expandedId.value != null) scheduleCollapse()
    }

    override fun onTap(item: IslandItem?, mode: Mode) {
        val s = SettingsStore.current
        when (mode) {
            Mode.HIDDEN -> Unit
            Mode.REST -> idle(s.idleTap)
            Mode.WIDE -> when {
                item?.otp != null -> copyOtp(item)
                // Al tocar la píldora de carga se abre la tarjeta con el detalle.
                item != null && item.isExpandableCharge() -> { IslandStore.hold(true); expand(item.id) }
                else -> IslandStore.dismissTransient(item?.id)
            }
            Mode.PEEK -> if (item != null) {
                if (expandedId.value == item.id) openApp(item) else {
                    IslandStore.hold(true); expand(item.id)
                }
            }
            else -> perform(s.tap, item, mode)
        }
    }

    override fun onLongPress(item: IslandItem?, mode: Mode) {
        val s = SettingsStore.current
        if (mode == Mode.REST) idle(s.idleLongPress)
        else perform(s.longPress, item, mode)
    }

    override fun onDoubleTap(item: IslandItem?, mode: Mode) {
        if (mode == Mode.REST) idle(SettingsStore.current.idleDoubleTap)
    }

    override fun onShortcut(action: dev.jotnar.materialisland.data.IdleAction) {
        // El modo de sonido y la linterna se quedan en la tarjeta para ver el cambio; lo demás la cierra.
        val stay = action == dev.jotnar.materialisland.data.IdleAction.SOUND_MODE ||
            action == dev.jotnar.materialisland.data.IdleAction.FLASHLIGHT
        if (!stay) collapse(silent = true) else scheduleCollapse()
        Actions.idle(service, service, action)
        shortcutTick.value++
    }

    /** Gesto sobre la cámara en reposo: una acción, o abrir la tarjeta de accesos. */
    private fun idle(action: dev.jotnar.materialisland.data.IdleAction) {
        if (action == dev.jotnar.materialisland.data.IdleAction.SHORTCUTS) {
            expandedId.value = SHORTCUTS_ID
            Haptics.expand()
            scheduleCollapse()
        } else Actions.idle(service, service, action)
    }

    override fun onGlanceTap() {
        if (expandedId.value == GLANCE_ID) { collapse(); return }
        fullMedia.value = false
        endReply()
        expandedId.value = GLANCE_ID
        dev.jotnar.materialisland.core.Glance.cardOpen = true
        dev.jotnar.materialisland.core.Glance.refresh()
        Haptics.expand()
        scheduleCollapse()
    }

    override fun onGlancePage() { Haptics.tick(); scheduleCollapse() }

    override fun onSwipe(item: IslandItem?, mode: Mode, dir: Swipe) {
        if (mode == Mode.REST || mode == Mode.HIDDEN) return
        // At a glance: deslizar en vertical la cierra (en horizontal pasa de vista dentro de la tarjeta).
        if (expandedId.value == GLANCE_ID) {
            if (dir == Swipe.UP || dir == Swipe.DOWN) collapse()
            return
        }
        // La tarjeta de accesos no tiene actividad detrás: deslizar en vertical la cierra.
        if (expandedId.value == SHORTCUTS_ID && item == null) {
            if (dir == Swipe.UP || dir == Swipe.DOWN) collapse()
            return
        }
        val s = SettingsStore.current
        perform(
            when (dir) {
                Swipe.UP -> s.swipeUp
                Swipe.DOWN -> s.swipeDown
                Swipe.LEFT, Swipe.RIGHT -> s.swipeSideways
            },
            item, mode, swipeDown = dir == Swipe.DOWN,
        )
    }

    private fun perform(action: GestureAction, item: IslandItem?, mode: Mode, swipeDown: Boolean = false) {
        item ?: return
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.TOGGLE_EXPAND -> when (mode) {
                Mode.COMPACT -> expand(item.id)
                // Música: compacta → tarjeta → carátula grande. Tocar en la grande la cierra.
                Mode.EXPANDED -> when {
                    item.kind == Kind.MEDIA && !fullMedia.value -> { fullMedia.value = true; Haptics.expand(); scheduleCollapse() }
                    !swipeDown -> collapse()
                    else -> Unit
                }
                Mode.PEEK -> if (expandedId.value == item.id) { if (!swipeDown) collapse() } else { IslandStore.hold(true); expand(item.id) }
                Mode.WIDE -> if (item.isExpandableCharge()) { IslandStore.hold(true); expand(item.id) } else IslandStore.dismissTransient(item.id)
                else -> Unit
            }
            GestureAction.OPEN_APP -> openApp(item)
            GestureAction.PLAY_PAUSE -> if (item.kind == Kind.MEDIA) onMedia(item, MediaCmd.PLAY_PAUSE) else expand(item.id)
            GestureAction.NEXT_ACTIVITY -> {
                Haptics.tick(); collapse(silent = true); IslandStore.cycle()
            }
            GestureAction.DISMISS -> when {
                !item.kind.persistent -> { collapse(silent = true); Haptics.tick(); IslandStore.dismissTransient(item.id) }
                // Deslizar hacia arriba en la carátula grande baja un nivel en vez de cerrar del todo.
                fullMedia.value && expandedId.value != null -> { fullMedia.value = false; Haptics.collapse(); scheduleCollapse() }
                expandedId.value != null -> collapse()
                else -> Unit
            }
        }
    }

    private fun openApp(item: IslandItem) {
        Haptics.click()
        Actions.openApp(service, item.pkg, item.contentIntent)
        collapse(silent = true)
        if (!item.kind.persistent) IslandStore.dismissTransient(item.id)
    }

    private fun copyOtp(item: IslandItem) {
        NotificationParser.copyToClipboard(service, item.otp ?: return)
        Haptics.click()
        copiedId.value = item.id
        IslandStore.hold(true)
        scope.launch {
            delay(1300)
            copiedId.value = null
            IslandStore.dismissTransient(item.id)
        }
    }

    override fun onAction(item: IslandItem, action: IslandAction) {
        Actions.run(service, action)
        if (!item.kind.persistent) {
            collapse(silent = true); IslandStore.dismissTransient(item.id)
        } else scheduleCollapse()
    }

    override fun onReplyStart(item: IslandItem) {
        IslandStore.hold(true)
        collapseJob?.cancel()
        if (expandedId.value != item.id) expandedId.value = item.id
        replyingId.value = item.id
        setFocusable(true)
        Haptics.tick()
    }

    override fun onReplySend(item: IslandItem, action: IslandAction, text: String) {
        action.onReply?.invoke(text)
        Haptics.click()
        endReply()
        collapse(silent = true)
        IslandStore.dismissTransient(item.id)
    }

    override fun onReplyCancel() {
        endReply()
        scheduleCollapse()
    }

    override fun onMedia(item: IslandItem, cmd: MediaCmd) {
        Haptics.tick()
        scheduleCollapse()
        val m = item.media ?: return
        val c = m.controller?.transportControls
        if (c == null) {
            if (cmd == MediaCmd.PLAY_PAUSE) Demo.toggleDemoMedia(); return
        }
        when (cmd) {
            MediaCmd.PLAY_PAUSE -> if (m.playing) c.pause() else c.play()
            MediaCmd.NEXT -> c.skipToNext()
            MediaCmd.PREV -> c.skipToPrevious()
        }
    }

    override fun onSeek(item: IslandItem, fraction: Float) {
        val m = item.media ?: return
        Haptics.tick()
        scheduleCollapse()
        m.controller?.transportControls?.seekTo((fraction * m.durationMs).toLong())
    }

    override fun onBubbleTap(item: IslandItem) {
        Haptics.tick()
        // Abre el mensaje en la isla para leerlo y responder sin salir de lo que estás haciendo.
        IslandStore.show(item.copy(durationMs = SettingsStore.current.peekSeconds * 1000L))
        IslandStore.hold(true)
        expandedId.value = item.id
        scheduleCollapse()
    }

    override fun onBubbleLongPress(item: IslandItem) = openApp(item)

    override fun onBubbleOverflow() {
        Haptics.tick()
        service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    override fun onSecondaryTap() {
        Haptics.tick()
        IslandStore.cycle()
    }

    companion object {
        private const val TAG = "MaterialIsland"
    }
}

/** Lifecycle mínimo para alojar Compose en una ventana que no es una Activity. */
private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun start() {
        saved.performAttach()
        saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}

private fun IslandItem.isExpandableCharge() = kind == Kind.CHARGING && compactText == "on" && charge != null

/** expandedId especial: la tarjeta de accesos que se abre desde la cámara en reposo. */
private const val SHORTCUTS_ID = "island:shortcuts"
/** expandedId especial: la tarjeta de At a glance (icono a la izquierda de la cámara). */
private const val GLANCE_ID = "island:glance"
