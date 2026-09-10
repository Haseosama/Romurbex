package com.romurbex.app.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.romurbex.app.data.LocationEntity
import com.romurbex.app.data.LocationRepository
import kotlinx.coroutines.launch

class EditLocationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocationRepository(application)

    suspend fun load(id: Long): LocationEntity? = repository.getLocation(id)

    fun save(location: LocationEntity, onSaved: (Long) -> Unit) {
        viewModelScope.launch { onSaved(repository.saveLocation(location)) }
    }
}
