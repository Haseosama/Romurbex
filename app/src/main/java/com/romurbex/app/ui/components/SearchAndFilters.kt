package com.romurbex.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romurbex.app.data.LocationCategory
import com.romurbex.app.ui.LocationsViewModel

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Chercher un lieu, un dossier, un mot-clé…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = trailingIcon,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        )
    }
}

/** Barre de recherche avec micro (recherche vocale) et haut-parleur (réponse parlée de l'IA) — gère
 *  elle-même la demande de permission RECORD_AUDIO. À utiliser à la place de [SearchField] seul. */
@Composable
fun VoiceSearchField(viewModel: LocationsViewModel, modifier: Modifier = Modifier) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val voiceEnabled by viewModel.voiceResponsesEnabled.collectAsStateWithLifecycle()
    val voiceMessage by viewModel.voiceMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceSearch()
    }

    Column {
        SearchField(
            query = query,
            onQueryChange = { viewModel.query.value = it },
            modifier = modifier,
            trailingIcon = {
                VoiceSearchButtons(
                    isListening = isListening,
                    isSpeaking = isSpeaking,
                    voiceResponsesEnabled = voiceEnabled,
                    onMicClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.startVoiceSearch() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onToggleVoice = { viewModel.toggleVoiceResponses() },
                )
            },
        )
        voiceMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

/** Micro (recherche vocale) + haut-parleur (active/coupe la réponse parlée de l'IA). */
@Composable
fun VoiceSearchButtons(
    isListening: Boolean,
    isSpeaking: Boolean,
    voiceResponsesEnabled: Boolean,
    onMicClick: () -> Unit,
    onToggleVoice: () -> Unit,
) {
    Row {
        IconButton(onClick = onMicClick) {
            if (isListening) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Recherche vocale",
                    tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onToggleVoice) {
            Icon(
                if (voiceResponsesEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = "Réponse vocale de l'IA",
                tint = if (voiceResponsesEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CategoryFilterRow(
    selected: LocationCategory?,
    favoritesOnly: Boolean,
    onSelect: (LocationCategory?) -> Unit,
    onFavoritesToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) {
        item {
            FilterChip(
                selected = favoritesOnly,
                onClick = onFavoritesToggle,
                label = { Text("Favoris") },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
            )
        }
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Tous") })
        }
        items(LocationCategory.entries) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(category.label()) },
                leadingIcon = { Icon(category.icon(), contentDescription = null) },
            )
        }
    }
}
