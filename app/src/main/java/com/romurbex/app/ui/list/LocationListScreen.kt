package com.romurbex.app.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.romurbex.app.data.LocationEntity
import com.romurbex.app.data.LocationRepository
import com.romurbex.app.ui.LocationsViewModel
import com.romurbex.app.ui.components.CategoryFilterRow
import com.romurbex.app.ui.components.VoiceSearchField
import com.romurbex.app.ui.components.color
import com.romurbex.app.ui.components.icon
import com.romurbex.app.ui.components.label
import androidx.compose.runtime.remember

@Composable
fun LocationListScreen(
    onLocationClick: (Long) -> Unit,
    viewModel: LocationsViewModel = viewModel(),
) {
    val locations by viewModel.visibleLocations.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val category by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        VoiceSearchField(
            viewModel = viewModel,
            modifier = Modifier.padding(12.dp),
        )
        CategoryFilterRow(
            selected = category,
            favoritesOnly = favoritesOnly,
            onSelect = { viewModel.categoryFilter.value = it },
            onFavoritesToggle = { viewModel.favoritesOnly.value = !favoritesOnly },
        )
        if (locations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (query.isBlank()) "Aucun lieu pour l'instant — importe ta liste Google Maps ou ajoute un lieu."
                    else "Aucun résultat pour « $query ».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(locations, key = { it.id }) { location ->
                    LocationRow(
                        location = location,
                        onClick = { onLocationClick(location.id) },
                        onFavoriteToggle = { viewModel.toggleFavorite(location) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationRow(location: LocationEntity, onClick: () -> Unit, onFavoriteToggle: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { LocationRepository(context) }
    val thumbnail by repository.observeFirstPhotoUri(location.id).collectAsStateWithLifecycle(initialValue = null)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(location.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        location.category.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        " ${location.category.label()} · ${location.riskLevel.label()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = location.riskLevel.color(),
                        maxLines = 1,
                    )
                }
            }

            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    if (location.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Favori",
                    tint = if (location.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
