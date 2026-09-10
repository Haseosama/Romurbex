package com.romurbex.app.ui.discover

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.romurbex.app.ai.DiscoveredPlace
import com.romurbex.app.ai.GeminiClient
import com.romurbex.app.ai.GroundingSource
import com.romurbex.app.data.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {
    private val securePrefs = SecurePrefs.get(application)

    val query = MutableStateFlow("")
    val isSearching = MutableStateFlow(false)
    val places = MutableStateFlow<List<DiscoveredPlace>>(emptyList())
    val sources = MutableStateFlow<List<GroundingSource>>(emptyList())
    val errorMessage = MutableStateFlow<String?>(null)

    val hasApiKey: StateFlow<Boolean> = securePrefs.geminiApiKey
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), securePrefs.geminiApiKey.value.isNotBlank())

    fun search() {
        val q = query.value.trim()
        if (q.isBlank() || isSearching.value) return
        val apiKey = securePrefs.geminiApiKey.value
        if (apiKey.isBlank()) {
            errorMessage.value = "Ajoute une clé API Gemini dans Réglages pour utiliser la découverte."
            return
        }
        isSearching.value = true
        errorMessage.value = null
        places.value = emptyList()
        sources.value = emptyList()
        viewModelScope.launch {
            GeminiClient.discoverPlaces(apiKey, q)
                .onSuccess { result ->
                    places.value = result.places
                    sources.value = result.sources
                    if (result.places.isEmpty()) {
                        errorMessage.value = "Aucun lieu trouvé pour cette recherche."
                    }
                }
                .onFailure { error ->
                    errorMessage.value = error.message ?: "Erreur Gemini"
                }
            isSearching.value = false
        }
    }
}
