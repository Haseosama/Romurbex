package com.romurbex.app.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.romurbex.app.ui.LocationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onBack: () -> Unit,
    viewModel: LocationsViewModel = viewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    var listToDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listes importées") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { padding ->
        if (lists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Aucune liste pour l'instant — les lieux importés en groupe (fichier ou lien) " +
                        "apparaîtront ici, avec de quoi les afficher, les masquer ou les supprimer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp)) {
                items(lists, key = { it.name }) { list ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(list.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${list.count} lieu(x)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = list.isVisible,
                                onCheckedChange = { viewModel.setListVisible(list.name, it) },
                            )
                            IconButton(onClick = { listToDelete = list.name }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Supprimer la liste",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val target = listToDelete
    if (target != null) {
        val count = lists.firstOrNull { it.name == target }?.count ?: 0
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text("Supprimer « $target » ?") },
            text = { Text("Ça supprime définitivement les $count lieu(x) de cette liste (et leurs photos attachées). Les autres listes ne sont pas touchées.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteList(target); listToDelete = null }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) { Text("Annuler") }
            },
        )
    }
}
