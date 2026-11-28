package dev.jotnar.materialisland

import android.app.Application
import dev.jotnar.materialisland.core.Haptics
import dev.jotnar.materialisland.data.SettingsStore

class MaterialIslandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        Haptics.init(this)
        dev.jotnar.materialisland.i18n.Tr.update(resources.configuration)
    }

    /** El idioma de la app puede cambiar con el proceso vivo (Ajustes › Apps › Idioma). */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        dev.jotnar.materialisland.i18n.Tr.update(newConfig)
    }
}
