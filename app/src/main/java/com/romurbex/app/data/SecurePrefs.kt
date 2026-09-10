package com.romurbex.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stockage chiffré pour la recherche vocale : 3 emplacements de clé API Gemini interchangeables
 * (pour basculer sur une autre en un tap si celle active se fait limiter — erreur 429) et le
 * réglage de réponse vocale. Volontairement vides par défaut — les clés sont saisies par
 * l'utilisateur dans Réglages, jamais codées en dur dans les sources.
 */
class SecurePrefs(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "romurbex_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    init {
        // Migration ponctuelle depuis l'ancienne clé unique (avant l'ajout des 3 emplacements) :
        // récupère ce qui a déjà été saisi plutôt que de le perdre silencieusement.
        val legacy = prefs.getString(KEY_GEMINI_API_LEGACY, null)
        if (!legacy.isNullOrBlank() && prefs.getString(KEY_SLOT_0, null).isNullOrBlank()) {
            prefs.edit().putString(KEY_SLOT_0, legacy).remove(KEY_GEMINI_API_LEGACY).apply()
        }
    }

    val geminiApiKeySlots: StateFlow<List<String>> = MutableStateFlow(
        SLOT_KEYS.map { prefs.getString(it, "") ?: "" },
    )
    val activeGeminiSlotIndex: StateFlow<Int> = MutableStateFlow(
        prefs.getInt(KEY_ACTIVE_SLOT, 0).coerceIn(0, SLOT_KEYS.lastIndex),
    )
    val geminiApiKey: StateFlow<String> = MutableStateFlow(
        geminiApiKeySlots.value.getOrElse(activeGeminiSlotIndex.value) { "" },
    )
    val voiceResponsesEnabled: StateFlow<Boolean> = MutableStateFlow(prefs.getBoolean(KEY_VOICE_ENABLED, true))

    /** Enregistre la clé d'un emplacement (0 à 2) sans forcément le rendre actif. */
    fun setGeminiApiKeySlot(index: Int, value: String) {
        val slotKey = SLOT_KEYS.getOrNull(index) ?: return
        prefs.edit().putString(slotKey, value).apply()
        val updated = geminiApiKeySlots.value.toMutableList()
        if (index !in updated.indices) return
        updated[index] = value
        (geminiApiKeySlots as MutableStateFlow).value = updated
        if (activeGeminiSlotIndex.value == index) {
            (geminiApiKey as MutableStateFlow).value = value
        }
    }

    /** Bascule la clé utilisée par la recherche vocale sur un autre emplacement déjà enregistré. */
    fun selectGeminiApiKeySlot(index: Int) {
        val value = geminiApiKeySlots.value.getOrNull(index) ?: return
        prefs.edit().putInt(KEY_ACTIVE_SLOT, index).apply()
        (activeGeminiSlotIndex as MutableStateFlow).value = index
        (geminiApiKey as MutableStateFlow).value = value
    }

    fun setVoiceResponsesEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()
        (voiceResponsesEnabled as MutableStateFlow).value = value
    }

    companion object {
        private const val KEY_GEMINI_API_LEGACY = "gemini_api_key"
        private const val KEY_SLOT_0 = "gemini_api_key_slot_0"
        private const val KEY_SLOT_1 = "gemini_api_key_slot_1"
        private const val KEY_SLOT_2 = "gemini_api_key_slot_2"
        private val SLOT_KEYS = listOf(KEY_SLOT_0, KEY_SLOT_1, KEY_SLOT_2)
        private const val KEY_ACTIVE_SLOT = "gemini_active_slot"
        private const val KEY_VOICE_ENABLED = "voice_responses_enabled"

        @Volatile private var instance: SecurePrefs? = null
        fun get(context: Context): SecurePrefs =
            instance ?: synchronized(this) {
                instance ?: SecurePrefs(context.applicationContext).also { instance = it }
            }
    }
}
