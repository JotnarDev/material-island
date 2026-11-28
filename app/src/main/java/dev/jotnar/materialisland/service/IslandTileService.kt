package dev.jotnar.materialisland.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.jotnar.materialisland.R
import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.i18n.tr

/**
 * Mosaico de ajustes rápidos: pausa o reanuda la isla. En pausa la isla se oculta y, si estaba como
 * "la principal", vuelven las notificaciones emergentes del sistema. Si falta el servicio de accesibilidad,
 * el mosaico sale como no disponible y al tocarlo abre la app.
 */
class IslandTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onTileAdded() { super.onTileAdded(); SettingsStore.update { it.copy(tileAdded = true) } }
    override fun onTileRemoved() { super.onTileRemoved(); SettingsStore.update { it.copy(tileAdded = false) } }

    override fun onClick() {
        super.onClick()
        if (!IslandAccessibilityService.running.value) {
            val intent = android.content.Intent(this, dev.jotnar.materialisland.ui.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
            return
        }
        SettingsStore.update { it.copy(paused = !it.paused) }
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        dev.jotnar.materialisland.i18n.Tr.update(resources.configuration)
        val running = IslandAccessibilityService.running.value
        val paused = SettingsStore.current.paused
        t.label = getString(R.string.tile_label)
        t.icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_tile)
        t.state = when {
            !running -> Tile.STATE_UNAVAILABLE
            paused -> Tile.STATE_INACTIVE
            else -> Tile.STATE_ACTIVE
        }
        t.subtitle = tr(
            when {
                !running -> "Setup needed"
                paused -> "Paused"
                else -> "Active"
            },
        )
        t.updateTile()
    }
}
