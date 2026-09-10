package com.romurbex.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Petites préférences d'affichage de la carte (pas de données sensibles, pas besoin de chiffrement). */
class MapPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("romurbex_map_prefs", Context.MODE_PRIVATE)

    val satelliteMode: StateFlow<Boolean> = MutableStateFlow(prefs.getBoolean(KEY_SATELLITE, false))

    fun setSatelliteMode(value: Boolean) {
        prefs.edit().putBoolean(KEY_SATELLITE, value).apply()
        (satelliteMode as MutableStateFlow).value = value
    }

    companion object {
        private const val KEY_SATELLITE = "satellite_mode"

        @Volatile private var instance: MapPreferences? = null
        fun get(context: Context): MapPreferences =
            instance ?: synchronized(this) {
                instance ?: MapPreferences(context.applicationContext).also { instance = it }
            }
    }
}
