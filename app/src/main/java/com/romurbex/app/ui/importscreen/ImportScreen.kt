package com.romurbex.app.ui.importscreen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.romurbex.app.importer.GoogleMapsImporter
import com.romurbex.app.importer.GoogleMapsLinkParser
import com.romurbex.app.importer.ImportedPlace
import com.romurbex.app.ui.LocationsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onImported: () -> Unit,
    viewModel: LocationsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var places by remember { mutableStateOf<List<ImportedPlace>>(emptyList()) }
    var sourceFolderName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var linksText by remember { mutableStateOf("") }
    var isParsingLinks by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { GoogleMapsImporter.parse(context, uri) }
            }.onSuccess {
                places = places + it
                if (sourceFolderName.isBlank()) {
                    sourceFolderName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: ""
                }
            }.onFailure {
                error = "Impossible de lire ce fichier : ${it.message ?: "format non reconnu"}."
            }
            isLoading = false
        }
    }

    fun parseLinks() {
        val lines = linksText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        isParsingLinks = true
        linkError = null
        scope.launch {
            val parsed = mutableListOf<ImportedPlace>()
            val failures = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                for (line in lines) {
                    runCatching { GoogleMapsLinkParser.parse(line) }
                        .onSuccess { parsed += it }
                        .onFailure { failures += (it.message ?: it.javaClass.simpleName) }
                }
            }
            if (parsed.isNotEmpty()) {
                places = places + parsed
                linksText = ""
                if (sourceFolderName.isBlank()) sourceFolderName = "Liens Google Maps"
            }
            linkError = failures.firstOrNull()?.let { first ->
                if (failures.size > 1) "$first (+${failures.size - 1} autre(s) lien(s) en échec)" else first
            }
            isParsingLinks = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importer depuis Google Maps") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Exporte ta liste depuis Google My Maps (Menu > Exporter vers KML) ou Google Takeout " +
                    "(Maps (vos lieux) > Enregistrés), puis choisis le fichier ici. Un CSV nom/latitude/longitude fonctionne aussi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FileOpen, contentDescription = null)
                Text("  Choisir un fichier (.kml, .json, .csv)")
            }

            if (isLoading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            HorizontalDivider()

            Text(
                "Ou colle un ou plusieurs liens Google Maps (un par ligne — lien complet, lien " +
                    "court maps.app.goo.gl, ou lien vers une LISTE entière partagée depuis l'appli : " +
                    "tous ses lieux sont importés d'un coup) :",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = linksText,
                onValueChange = { linksText = it },
                placeholder = { Text("https://maps.app.goo.gl/…") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { parseLinks() }, modifier = Modifier.fillMaxWidth(), enabled = linksText.isNotBlank() && !isParsingLinks) {
                Icon(Icons.Filled.Link, contentDescription = null)
                Text("  Analyser le(s) lien(s)")
            }
            if (isParsingLinks) CircularProgressIndicator()
            linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(modifier = Modifier.height(4.dp))

            if (places.isNotEmpty()) {
                OutlinedTextField(
                    value = sourceFolderName,
                    onValueChange = { sourceFolderName = it },
                    label = { Text("Nom de cette liste (utilisé par la recherche)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("${places.size} lieu(x) trouvé(s) :", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(places) { place ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(place.name, style = MaterialTheme.typography.titleMedium)
                                if (place.description.isNotBlank()) {
                                    Text(place.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                }
                                Text(
                                    "%.5f, %.5f".format(place.latitude, place.longitude),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.importLocations(GoogleMapsImporter.toEntities(places, sourceFolderName))
                        onImported()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Importer ${places.size} lieu(x)")
                }
            }
        }
    }
}
