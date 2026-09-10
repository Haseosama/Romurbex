package com.romurbex.app

import android.app.Application
import org.osmdroid.config.Configuration

class RomurbexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // osmdroid exige un user-agent explicite et un dossier de cache, sinon les tuiles
        // OpenStreetMap sont silencieusement refusées par le serveur de tuiles.
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().osmdroidBasePath = getDir("osmdroid", MODE_PRIVATE)
        Configuration.getInstance().osmdroidTileCache = java.io.File(cacheDir, "osmdroid/tiles")
    }
}
