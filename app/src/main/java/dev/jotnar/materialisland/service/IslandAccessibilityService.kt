package dev.jotnar.materialisland.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import dev.jotnar.materialisland.core.IslandStore
import dev.jotnar.materialisland.core.SystemHeadsUp
import dev.jotnar.materialisland.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private var privacy: dev.jotnar.materialisland.core.PrivacyMonitor? = null
    private var privacyJob: Job? = null
    private var headsUpJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = IslandOverlay(this).also { it.start() }
        system = SystemEventMonitor(this).also { it.start() }
        privacy = dev.jotnar.materialisland.core.PrivacyMonitor(this).also { it.start() }
        privacyJob = CoroutineScope(Dispatchers.Main).launch {
            SettingsStore.flow.map { it.privacyIndicator }.distinctUntilChanged().collect { privacy?.refresh() }
        }
        running.value = true
        // Mientras la isla vive y es "la principal", el sistema no muestra emergentes.
        headsUpJob = CoroutineScope(Dispatchers.Main).launch {
            SettingsStore.flow.map { it.islandIsPrimary && !it.paused }.distinctUntilChanged().collect { primary ->
                SystemHeadsUp.setEnabled(this@IslandAccessibilityService, !primary)
            }
        }
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
        headsUpJob?.cancel(); headsUpJob = null
        // Red de seguridad: si la isla se apaga, vuelven los emergentes del sistema.
        if (SettingsStore.current.islandIsPrimary) SystemHeadsUp.setEnabled(this, true)
        system?.stop(); system = null
        privacyJob?.cancel(); privacyJob = null
        privacy?.stop(); privacy = null
        overlay?.stop(); overlay = null
    }

    companion object {
        val running = MutableStateFlow(false)
    }
}
