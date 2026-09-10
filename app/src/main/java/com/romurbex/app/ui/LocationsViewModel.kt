package com.romurbex.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.romurbex.app.ai.GeminiClient
import com.romurbex.app.data.ListSummary
import com.romurbex.app.data.LocationCategory
import com.romurbex.app.data.LocationEntity
import com.romurbex.app.data.LocationRepository
import com.romurbex.app.data.SecurePrefs
import com.romurbex.app.search.SearchEngine
import com.romurbex.app.ui.components.label
import com.romurbex.app.voice.VoiceSearchController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListUiState(val name: String, val count: Int, val isVisible: Boolean)

class LocationsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocationRepository(application)
    private val securePrefs = SecurePrefs.get(application)
    private val voiceController = VoiceSearchController(application)

    val query = MutableStateFlow("")
    val categoryFilter = MutableStateFlow<LocationCategory?>(null)
    val favoritesOnly = MutableStateFlow(false)

    val isListening = MutableStateFlow(false)
    val isSpeaking = MutableStateFlow(false)
    val voiceMessage = MutableStateFlow<String?>(null)
    val voiceResponsesEnabled: StateFlow<Boolean> = securePrefs.voiceResponsesEnabled

    private val allLocations: StateFlow<List<LocationEntity>> = repository.observeLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val listSummaries: StateFlow<List<ListSummary>> = repository.observeListSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val hiddenLists: StateFlow<Set<String>> = repository.observeHiddenLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val lists: StateFlow<List<ListUiState>> = combine(listSummaries, hiddenLists) { summaries, hidden ->
        summaries.map { ListUiState(it.name, it.count, it.name !in hidden) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Recherche avec tolérance aux fautes de frappe (distance de Levenshtein) sur des centaines
    // de lieux : trop coûteux pour tourner sur le thread principal à chaque frappe sans faire
    // ramer le clavier. debounce() attend une pause dans la saisie, flowOn() déporte le calcul
    // sur un thread de fond — le champ de recherche lui-même reste instantané (state local Compose
    // séparé), seule la liste de résultats se met à jour avec un léger délai.
    private val debouncedQuery = query.debounce(200)

    val visibleLocations: StateFlow<List<LocationEntity>> = combine(
        allLocations, debouncedQuery, categoryFilter, favoritesOnly, hiddenLists,
    ) { locations, q, category, favOnly, hidden ->
        var result = locations
        if (hidden.isNotEmpty()) result = result.filter { it.sourceFolderName !in hidden }
        if (category != null) result = result.filter { it.category == category }
        if (favOnly) result = result.filter { it.isFavorite }
        if (q.isNotBlank()) result = SearchEngine.search(result, q)
        result
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun importLocations(locations: List<LocationEntity>) {
        viewModelScope.launch { repository.importLocations(locations) }
    }

    fun toggleFavorite(location: LocationEntity) {
        viewModelScope.launch { repository.saveLocation(location.copy(isFavorite = !location.isFavorite)) }
    }

    fun setCategory(location: LocationEntity, category: LocationCategory) {
        viewModelScope.launch { repository.saveLocation(location.copy(category = category)) }
    }

    fun delete(location: LocationEntity) {
        viewModelScope.launch { repository.deleteLocation(location) }
    }

    fun setListVisible(name: String, visible: Boolean) {
        viewModelScope.launch { repository.setListVisible(name, visible) }
    }

    fun deleteList(name: String) {
        viewModelScope.launch { repository.deleteList(name) }
    }

    fun toggleVoiceResponses() {
        securePrefs.setVoiceResponsesEnabled(!voiceResponsesEnabled.value)
    }

    /** Lance une écoute unique : le texte reconnu remplit la recherche, puis (si activé) Gemini
     *  résume les résultats à voix haute. Appelant responsable d'avoir déjà la permission micro. */
    fun startVoiceSearch() {
        if (isListening.value) return
        voiceController.stopSpeaking()
        isSpeaking.value = false
        voiceMessage.value = null
        isListening.value = true
        voiceController.listenOnce(
            onResult = { text ->
                isListening.value = false
                query.value = text
                if (voiceResponsesEnabled.value) speakSearchSummary(text)
            },
            onError = { message ->
                isListening.value = false
                voiceMessage.value = message
            },
        )
    }

    private fun speakSearchSummary(spokenQuery: String) {
        val apiKey = securePrefs.geminiApiKey.value
        if (apiKey.isBlank()) {
            voiceMessage.value = "Ajoute ta clé API Gemini dans Réglages pour activer les réponses vocales."
            return
        }
        val matches = SearchEngine.search(allLocations.value, spokenQuery)
        val resultsBlock = if (matches.isEmpty()) {
            "Aucun lieu ne correspond à cette recherche dans la base locale."
        } else {
            matches.take(6).joinToString("\n") { location ->
                val listPart = location.sourceFolderName.ifBlank { "aucune liste" }
                "- ${location.name} (${location.category.label()}, liste : $listPart)"
            }
        }
        val prompt = "Requête vocale : \"$spokenQuery\"\n\nRésultats trouvés localement :\n$resultsBlock"

        viewModelScope.launch {
            GeminiClient.ask(apiKey, prompt)
                .onSuccess { reply ->
                    isSpeaking.value = true
                    voiceController.speak(reply) { isSpeaking.value = false }
                }
                .onFailure { error ->
                    voiceMessage.value = error.message ?: "Erreur Gemini"
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.destroy()
    }
}
