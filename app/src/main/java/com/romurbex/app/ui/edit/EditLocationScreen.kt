package com.romurbex.app.ui.edit

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.romurbex.app.data.LocationCategory
import com.romurbex.app.data.LocationEntity
import com.romurbex.app.data.RiskLevel
import com.romurbex.app.ui.components.icon
import com.romurbex.app.ui.components.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLocationScreen(
    locationId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    initialName: String = "",
    initialDescription: String = "",
    initialLatitude: String = "",
    initialLongitude: String = "",
    initialSourceFolderName: String = "",
    viewModel: EditLocationViewModel = viewModel(),
) {
    val context = LocalContext.current
    var existing by remember { mutableStateOf<LocationEntity?>(null) }
    var loaded by remember { mutableStateOf(locationId == null) }

    // Préremplis quand on arrive depuis Découvrir ou un import d'épingle Pinterest — vide sinon
    // (bouton "Ajouter" normal).
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(LocationCategory.AUTRE) }
    var risk by remember { mutableStateOf(RiskLevel.INCONNU) }
    var latitude by remember { mutableStateOf(initialLatitude) }
    var longitude by remember { mutableStateOf(initialLongitude) }
    var sourceFolderName by remember { mutableStateOf(initialSourceFolderName) }

    LaunchedEffect(locationId) {
        if (locationId != null) {
            val loc = viewModel.load(locationId)
            existing = loc
            if (loc != null) {
                name = loc.name
                description = loc.description
                notes = loc.notes
                category = loc.category
                risk = loc.riskLevel
                latitude = loc.latitude.toString()
                longitude = loc.longitude.toString()
                sourceFolderName = loc.sourceFolderName
            }
            loaded = true
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetchCurrentLocation(context) { lat, lng -> latitude = lat.toString(); longitude = lng.toString() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (locationId == null) "Nouveau lieu" else "Modifier le lieu") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom du lieu") }, modifier = Modifier.fillMaxWidth())

            Text("Catégorie", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LocationCategory.entries) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c.label()) },
                        leadingIcon = { Icon(c.icon(), contentDescription = null) },
                    )
                }
            }

            Text("Niveau de risque", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RiskLevel.entries) { r ->
                    FilterChip(selected = risk == r, onClick = { risk = r }, label = { Text(r.label()) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text("Latitude") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text("Longitude") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fetchCurrentLocation(context) { lat, lng -> latitude = lat.toString(); longitude = lng.toString() }
                } else {
                    locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = null)
                Text("  Utiliser ma position actuelle")
            }

            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes personnelles") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    val lat = latitude.toDoubleOrNull() ?: return@Button
                    val lng = longitude.toDoubleOrNull() ?: return@Button
                    val toSave = (existing ?: LocationEntity(name = "", latitude = 0.0, longitude = 0.0)).copy(
                        name = name.ifBlank { "Lieu sans nom" },
                        description = description,
                        notes = notes,
                        category = category,
                        riskLevel = risk,
                        latitude = lat,
                        longitude = lng,
                        sourceFolderName = sourceFolderName,
                    )
                    viewModel.save(toSave, onSaved)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null,
            ) {
                Text(if (locationId == null) "Ajouter le lieu" else "Enregistrer")
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(context: android.content.Context, onResult: (Double, Double) -> Unit) {
    LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
        if (location != null) onResult(location.latitude, location.longitude)
    }
}
