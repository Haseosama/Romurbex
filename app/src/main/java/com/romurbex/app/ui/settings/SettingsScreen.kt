package com.romurbex.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.romurbex.app.ai.GeminiClient
import com.romurbex.app.data.SecurePrefs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SecurePrefs.get(context) }
    val slots by prefs.geminiApiKeySlots.collectAsStateWithLifecycle()
    val activeIndex by prefs.activeGeminiSlotIndex.collectAsStateWithLifecycle()
    val activeKey by prefs.geminiApiKey.collectAsStateWithLifecycle()
    val voiceEnabled by prefs.voiceResponsesEnabled.collectAsStateWithLifecycle()

    var input0 by remember(slots) { mutableStateOf(slots.getOrElse(0) { "" }) }
    var input1 by remember(slots) { mutableStateOf(slots.getOrElse(1) { "" }) }
    var input2 by remember(slots) { mutableStateOf(slots.getOrElse(2) { "" }) }
    val inputs = listOf(input0, input1, input2)
    val setInput: (Int, String) -> Unit = { index, value ->
        when (index) {
            0 -> input0 = value
            1 -> input1 = value
            2 -> input2 = value
        }
    }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Recherche vocale", style = MaterialTheme.typography.titleMedium)
            Text(
                "Jusqu'à 3 clés API Gemini (gratuites sur aistudio.google.com/apikey) pour que le " +
                    "micro de la recherche puisse te répondre à voix haute. Si celle active se fait " +
                    "limiter (erreur 429), bascule sur une autre en un tap au lieu d'attendre. Sans " +
                    "clé active, le micro continue de remplir la recherche, juste sans réponse parlée.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            inputs.forEachIndexed { index, value ->
                val isActive = index == activeIndex
                val isSaved = value == slots.getOrElse(index) { "" }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { setInput(index, it); testResult = null },
                        label = { Text("Clé API Gemini #${index + 1}") },
                        singleLine = true,
                        trailingIcon = {
                            if (isActive) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Clé active",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { prefs.setGeminiApiKeySlot(index, value.trim()) },
                            enabled = !isSaved,
                        ) {
                            Text("Enregistrer")
                        }
                        OutlinedButton(
                            onClick = { prefs.selectGeminiApiKeySlot(index) },
                            enabled = !isActive && slots.getOrElse(index) { "" }.isNotBlank(),
                        ) {
                            Text(if (isActive) "Active" else "Utiliser cette clé")
                        }
                    }
                }
                if (index != inputs.lastIndex) HorizontalDivider()
            }

            OutlinedButton(
                onClick = {
                    testing = true
                    testResult = null
                    scope.launch {
                        GeminiClient.ask(activeKey, "Réponds uniquement par le mot ok.")
                            .onSuccess { testResult = "Clé active valide ✓" }
                            .onFailure { testResult = it.message ?: "Échec du test" }
                        testing = false
                    }
                },
                enabled = activeKey.isNotBlank() && !testing,
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Tester la clé active")
                }
            }
            testResult?.let {
                Text(
                    it,
                    color = if (it.startsWith("Clé active valide")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Réponse vocale de l'IA", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Coupe-la pour que le micro remplisse juste la recherche, sans que Gemini réponde à voix haute.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = voiceEnabled, onCheckedChange = { prefs.setVoiceResponsesEnabled(it) })
            }
        }
    }
}
