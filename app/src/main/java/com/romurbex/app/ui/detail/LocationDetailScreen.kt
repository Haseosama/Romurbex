package com.romurbex.app.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.romurbex.app.data.LocationEntity
import com.romurbex.app.ui.LocationDetailViewModel
import com.romurbex.app.ui.components.color
import com.romurbex.app.ui.components.icon
import com.romurbex.app.ui.components.label
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    locationId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(locationId) {
        viewModelFactory {
            initializer {
                LocationDetailViewModel(context.applicationContext as android.app.Application, locationId)
            }
        }
    }
    val viewModel: LocationDetailViewModel = viewModel(factory = factory)
    val location by viewModel.location.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addPhotos(uris)
    }

    val currentLocation = location
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentLocation?.name ?: "Lieu") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    if (currentLocation != null) {
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                if (currentLocation.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "Favori",
                                tint = if (currentLocation.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onEdit(locationId) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modifier")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (currentLocation == null) return@Scaffold

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PhotoGallery(
                photos = photos.map { Uri.parse(it.uri) },
                onAddClick = { photoPicker.launch(arrayOf("image/*")) },
                onPhotoClick = { index -> viewerStartIndex = index },
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(currentLocation.category.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "  ${currentLocation.category.label()}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    currentLocation.riskLevel.label(),
                    color = currentLocation.riskLevel.color(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )

                if (currentLocation.description.isNotBlank()) {
                    Text(currentLocation.description, modifier = Modifier.padding(top = 12.dp))
                }
                if (currentLocation.notes.isNotBlank()) {
                    Text(
                        "Notes : ${currentLocation.notes}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Text(
                    "GPS : %.6f, %.6f".format(currentLocation.latitude, currentLocation.longitude),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (currentLocation.sourceFolderName.isNotBlank()) {
                    Text(
                        "Origine : ${currentLocation.sourceFolderName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Ajouté le ${formatDate(currentLocation.dateAdded)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextButton(
                    onClick = {
                        val uri = Uri.parse("geo:${currentLocation.latitude},${currentLocation.longitude}?q=${currentLocation.latitude},${currentLocation.longitude}(${Uri.encode(currentLocation.name)})")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null)
                    Text("  Ouvrir dans une appli de cartes")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Supprimer ce lieu ?") },
            text = { Text("Cette action est définitive. Les photos attachées ne seront pas supprimées de ton stockage, seulement le lien dans Romurbex.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete(onDeleted) }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") }
            },
        )
    }

    viewerStartIndex?.let { index ->
        PhotoViewerDialog(
            photos = photos.map { Uri.parse(it.uri) },
            startIndex = index,
            onDismiss = { viewerStartIndex = null },
        )
    }
}

@Composable
private fun PhotoGallery(photos: List<Uri>, onAddClick: () -> Unit, onPhotoClick: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) {
        itemsIndexed(photos) { index, uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(180.dp, 220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPhotoClick(index) },
            )
        }
        item {
            Box(
                modifier = Modifier
                    .size(140.dp, 220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onAddClick),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = "Ajouter des photos")
                    Text("Ajouter", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.FRENCH).format(Date(timestamp))
