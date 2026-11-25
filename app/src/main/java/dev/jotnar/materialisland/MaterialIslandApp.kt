package dev.jotnar.materialisland

import android.app.Application
import dev.jotnar.materialisland.core.Haptics
import dev.jotnar.materialisland.data.SettingsStore

class MaterialIslandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        Haptics.init(this)
    }
}
