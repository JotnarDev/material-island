package dev.jotnar.materialisland.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import dev.jotnar.materialisland.core.IslandStore
import dev.jotnar.materialisland.core.SystemEventMonitor
import dev.jotnar.materialisland.overlay.IslandOverlay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Aloja la ventana de la isla (TYPE_ACCESSIBILITY_OVERLAY). No lee el contenido de la pantalla:
 * solo el nombre del paquete de la app en primer plano, para ocultar la música cuando su app está abierta.
 */
class IslandAccessibilityService : AccessibilityService() {
    private var overlay: IslandOverlay? = null
    private var system: SystemEventMonitor? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = IslandOverlay(this).also { it.start() }
        system = SystemEventMonitor(this).also { it.start() }
        running.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || pkg.contains("inputmethod")) return
        IslandStore.foregroundPkg.value = pkg
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged(newConfig)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        running.value = false
        system?.stop(); system = null
        overlay?.stop(); overlay = null
    }

    companion object {
        val running = MutableStateFlow(false)
    }
}
