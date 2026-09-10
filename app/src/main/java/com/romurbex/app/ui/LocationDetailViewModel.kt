package com.romurbex.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.romurbex.app.data.LocationEntity
import com.romurbex.app.data.LocationRepository
import com.romurbex.app.data.PhotoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocationDetailViewModel(
    application: Application,
    private val locationId: Long,
) : AndroidViewModel(application) {
    private val repository = LocationRepository(application)

    val location: StateFlow<LocationEntity?> = repository.observeLocation(locationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val photos: StateFlow<List<PhotoEntity>> = repository.observePhotos(locationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPhotos(uris: List<Uri>) {
        viewModelScope.launch { repository.attachPhotos(locationId, uris) }
    }

    fun removePhoto(photo: PhotoEntity) {
        viewModelScope.launch { repository.removePhoto(photo) }
    }

    fun toggleFavorite() {
        val current = location.value ?: return
        viewModelScope.launch { repository.saveLocation(current.copy(isFavorite = !current.isFavorite)) }
    }

    fun delete(onDone: () -> Unit) {
        val current = location.value ?: return
        viewModelScope.launch {
            repository.deleteLocation(current)
            onDone()
        }
    }
}
