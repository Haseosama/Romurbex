package com.romurbex.app.ui.discover

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.romurbex.app.ai.DiscoveredPlace
import com.romurbex.app.ai.GroundingSource
import com.romurbex.app.importer.ParsedPin
import com.romurbex.app.importer.PinDescriptionParser
import java.util.Locale

/** Épingles urbex publiques avec coordonnées GPS en description — voir aussi [PinPasteSection]
 *  pour l'extraction fiable (regex) d'une épingle précise collée à la main. */
private const val PINTEREST_BOARD_SITE = "pinterest.com/patrimoine43"
private const val PINTEREST_BOARD_URL = "https://fr.pinterest.com/patrimoine43/abandoned-urbex-locations/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onBack: () -> Unit,
    onAddPlace: (DiscoveredPlace) -> Unit,
    onAddParsedPin: (ParsedPin) -> Unit,
    viewModel: DiscoveryViewModel = viewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var autoSearchTried by remember { mutableStateOf(false) }

    // Recherche automatique autour de la position actuelle dès l'ouverture de l'écran, plutôt que
    // d'attendre que l'utilisateur tape une requête — biaisée vers le tableau Pinterest connu via
    // un "site:", en plus du reste du web. Ne remplace pas le collage manuel d'épingle
    // (PinPasteSection) : Google n'indexe pas forcément le texte de description complet d'une
    // épingle (coordonnées incluses), donc rien ne garantit que cette recherche retrouve des GPS
    // exacts — seulement des lieux candidats à vérifier, comme le reste de Découvrir.
    LaunchedEffect(hasApiKey) {
        if (autoSearchTried || !hasApiKey || query.isNotBlank()) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return@LaunchedEffect
        autoSearchTried = true
        launchNearbySearch(context, viewModel)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Découvrir") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Gemini cherche sur le web des lieux abandonnés correspondant à ta recherche " +
                        "(ville, région, type de lieu…) et te propose des candidats à vérifier toi-même — " +
                        "noms et descriptions, rarement des coordonnées exactes. Une recherche autour de " +
                        "ta position se lance automatiquement à l'ouverture (biaisée vers le tableau " +
                        "Pinterest urbex), mais rien ne garantit qu'elle retrouve les coordonnées GPS " +
                        "exactes d'une épingle — pour ça, colle sa description ci-dessous.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "La recherche web utilisée ici a son propre quota Google, séparé de la recherche " +
                        "vocale — sur une clé sans facturation activée, il est souvent inutilisable " +
                        "(erreur 429 dès la première recherche). Active la facturation sur le projet " +
                        "Google Cloud de ta clé (aistudio.google.com) si Découvrir ne répond jamais.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontStyle = FontStyle.Italic,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    placeholder = { Text("ex. usines abandonnées en Normandie") },
                    leadingIcon = { Icon(Icons.Filled.TravelExplore, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.search() }, enabled = query.isNotBlank() && !isSearching) {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Search, contentDescription = "Rechercher")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }

                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                Text("Ou en direct, sans IA ni quota — ouvre une recherche dans le navigateur :", style = MaterialTheme.typography.labelLarge)
                QuickSearchRow(query = query, context = context)
                AssistChip(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PINTEREST_BOARD_URL))) },
                    label = { Text("Ouvrir le tableau patrimoine43") },
                    leadingIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )

                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                PinPasteSection(onAddParsedPin = onAddParsedPin)
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(places) { place ->
                    DiscoveredPlaceCard(place = place, onAdd = { onAddPlace(place) })
                }
                if (sources.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                            Text("Sources consultées", style = MaterialTheme.typography.labelLarge)
                            sources.forEach { source ->
                                Text(
                                    "• ${source.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun launchNearbySearch(context: Context, viewModel: DiscoveryViewModel) {
    LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
        if (location == null) return@addOnSuccessListener
        val placeName = runCatching {
            Geocoder(context, Locale.FRANCE).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
        }.getOrNull()?.let { it.locality ?: it.adminArea } ?: return@addOnSuccessListener
        viewModel.query.value = "lieux abandonnés urbex près de $placeName (site:$PINTEREST_BOARD_SITE ou ailleurs sur le web)"
        viewModel.search()
    }
}

/** Recherche externe classique (navigateur), zéro IA et zéro quota — pour quand Découvrir est
 *  bloqué par le quota de recherche web de Gemini, ou juste pour fouiller soi-même les sources. */
@Composable
private fun QuickSearchRow(query: String, context: android.content.Context) {
    val enabled = query.isNotBlank()
    val engines = listOf(
        "Google" to { q: String -> "https://www.google.com/search?q=${Uri.encode("$q urbex lieu abandonné")}" },
        "Pinterest" to { q: String -> "https://www.pinterest.com/search/pins/?q=${Uri.encode("$q urbex")}" },
        "Reddit" to { q: String -> "https://www.reddit.com/r/urbanexploration/search/?q=${Uri.encode(q)}&restrict_sr=1" },
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(engines) { (label, urlFor) ->
            AssistChip(
                onClick = {
                    if (enabled) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlFor(query))))
                    }
                },
                enabled = enabled,
                label = { Text(label) },
                leadingIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

/**
 * Certaines épingles Pinterest urbex incluent des coordonnées GPS en DMS dans leur description
 * (ex. "45°20'51.3"N 0°11'41.3"W ..."). On ne peut pas lire Pinterest automatiquement (rendu
 * bloqué, contenu non public), mais l'utilisateur peut copier-coller le titre/adresse et la
 * description de l'épingle lui-même — parsing local, aucune IA ni quota.
 */
@Composable
private fun PinPasteSection(onAddParsedPin: (ParsedPin) -> Unit) {
    var titleText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<ParsedPin?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Coller le titre/adresse et la description d'une épingle Pinterest :", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = titleText,
            onValueChange = {
                titleText = it
                parsed = null
                parseError = null
            },
            label = { Text("Titre / adresse de l'épingle") },
            placeholder = { Text("ex. Abandoned (For Sale?) Château St Bernard, 16360 Touvérac, France") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = descriptionText,
            onValueChange = {
                descriptionText = it
                parsed = null
                parseError = null
            },
            label = { Text("Description (avec coordonnées GPS)") },
            placeholder = { Text("ex. 45°20'51.3\"N 0°11'41.3\"W Abandoned (For Sale?) Château St Bernard...") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val result = PinDescriptionParser.parse(descriptionText)
                parsed = result?.let { pin -> if (titleText.isNotBlank()) pin.copy(name = titleText.trim()) else pin }
                parseError = if (result == null) "Aucune coordonnée GPS (format degrés/minutes/secondes) trouvée dans la description." else null
            },
            enabled = descriptionText.isNotBlank(),
        ) {
            Text("Analyser")
        }
        parseError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        parsed?.let { pin ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pin.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "%.6f, %.6f".format(pin.latitude, pin.longitude),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onAddParsedPin(pin) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Ajouter ce lieu")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredPlaceCard(place: DiscoveredPlace, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                if (place.description.isNotBlank()) {
                    Text(
                        place.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter ce lieu")
            }
        }
    }
}
