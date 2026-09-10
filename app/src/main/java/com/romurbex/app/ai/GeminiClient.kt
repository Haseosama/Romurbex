package com.romurbex.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class DiscoveredPlace(val name: String, val description: String)
data class GroundingSource(val title: String, val uri: String)
data class DiscoveryResult(val places: List<DiscoveredPlace>, val sources: List<GroundingSource>)

/**
 * Petit client Gemini non-streaming (une question, une réponse complète) — inspiré du moteur
 * Gemini du projet Eve, simplifié : pas de chat multi-tour, pas de clé codée en dur (l'utilisateur
 * saisit la sienne dans Réglages). Deux usages : réponse vocale après une recherche locale
 * ([ask]), et découverte de nouveaux lieux via la recherche web de Gemini ([discoverPlaces]).
 */
object GeminiClient {
    private const val MODEL = "gemini-3.6-flash"

    // Non-streaming : on attend la réponse complète avant de la lire à voix haute ou de la
    // parser, donc pas besoin d'un timeout de lecture aussi long qu'un flux SSE — mais Gemini
    // peut rester silencieux plusieurs secondes en "réflexion" avant de répondre, et une requête
    // avec recherche web (discoverPlaces) est plus lente qu'une réponse directe.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    const val DEFAULT_SYSTEM_PROMPT =
        "Tu es l'assistant de recherche vocale de Romurbex, une appli personnelle de repérage de " +
            "lieux abandonnés (urbex). L'utilisateur vient de faire une recherche vocale dans sa " +
            "base de lieux ; on te donne sa requête et les résultats trouvés localement. Réponds " +
            "en une ou deux phrases orales pour confirmer ce qui a été trouvé, en citant les noms " +
            "des lieux les plus pertinents. Jamais de markdown (pas de **gras**, listes à puces, " +
            "titres) ni d'émojis : uniquement du texte brut en phrases naturelles, lu à voix haute."

    private const val DISCOVERY_SYSTEM_PROMPT =
        "Tu es l'assistant de découverte de Romurbex, une appli personnelle de repérage de lieux " +
            "abandonnés (urbex). Utilise la recherche web pour trouver des lieux abandonnés réels " +
            "correspondant à la demande de l'utilisateur (forums urbex, presse locale, blogs, " +
            "réseaux sociaux publics…). Réponds UNIQUEMENT avec un tableau JSON valide, sans " +
            "markdown ni texte autour, au format exact : [{\"nom\": \"...\", \"description\": \"...\"}]. " +
            "Maximum 8 résultats, triés par pertinence. Description en une phrase : type de lieu, " +
            "état approximatif, ville ou région si connue. Ne fabrique jamais un résultat : base-toi " +
            "uniquement sur ce que tu trouves réellement via la recherche web ; si tu ne trouves rien " +
            "de pertinent et vérifiable, réponds []."

    /** Réponse directe, sans recherche web — utilisé pour la synthèse vocale après une recherche locale. */
    suspend fun ask(
        apiKey: String,
        prompt: String,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    ): Result<String> {
        val body = buildRequestBody(prompt, systemPrompt, useSearchGrounding = false)
        return call(apiKey, body).mapCatching { candidate ->
            extractText(candidate)?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Réponse vide de Gemini")
        }
    }

    /**
     * Cherche sur le web des lieux abandonnés correspondant à [query] via la recherche Google
     * intégrée à Gemini (grounding) — consomme davantage de quota qu'un appel direct, chaque
     * recherche fait un ou plusieurs appels de recherche web en coulisses côté Google.
     */
    suspend fun discoverPlaces(apiKey: String, query: String): Result<DiscoveryResult> {
        val body = buildRequestBody("Lieux abandonnés : $query", DISCOVERY_SYSTEM_PROMPT, useSearchGrounding = true)
        return call(apiKey, body, usedGrounding = true).mapCatching { candidate ->
            val text = extractText(candidate) ?: throw IllegalStateException("Réponse vide de Gemini")
            DiscoveryResult(places = parseDiscoveredPlaces(text), sources = extractSources(candidate))
        }
    }

    private fun buildRequestBody(prompt: String, systemPrompt: String, useSearchGrounding: Boolean): JsonObject =
        buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", systemPrompt) }) })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) })
                })
            })
            if (useSearchGrounding) {
                put("tools", buildJsonArray { add(buildJsonObject { put("google_search", buildJsonObject {}) }) })
            }
            put("generationConfig", buildJsonObject {
                put("thinkingConfig", buildJsonObject { put("thinkingLevel", "low") })
            })
        }

    /** Appel HTTP bas niveau partagé — retourne le premier `candidates[]` (contenu + éventuelles
     *  métadonnées de recherche), commun à [ask] et [discoverPlaces]. */
    private suspend fun call(apiKey: String, body: JsonObject, usedGrounding: Boolean = false): Result<JsonObject> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("Clé API Gemini manquante"))

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(describeHttpError(response.code, raw, usedGrounding)))
                }
                val candidate = (Json.parseToJsonElement(raw.orEmpty()) as? JsonObject)
                    ?.get("candidates")
                    ?.let { it as? JsonArray }
                    ?.firstOrNull() as? JsonObject
                if (candidate == null) {
                    Result.failure(IllegalStateException("Réponse vide de Gemini"))
                } else {
                    Result.success(candidate)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remonte le message d'erreur exact renvoyé par Google (error.message / error.status) plutôt
     * qu'un message générique deviné à partir du seul code HTTP — un 429 peut venir de plusieurs
     * quotas différents (par minute, par jour, par modèle, capacité momentanée du modèle côté
     * Google) et le corps JSON dit toujours lequel.
     */
    private fun describeHttpError(code: Int, body: String?, usedGrounding: Boolean = false): String {
        // La recherche web (google_search) a son propre quota, séparé et bien plus strict que
        // les appels Gemini classiques — sur une clé gratuite sans facturation, il est souvent
        // à 0. Un 429 ici peut donc arriver même quand le quota "normal" (ask()) va très bien.
        val groundingNote = if (usedGrounding && code == 429) {
            " La recherche web de Gemini a son propre quota, séparé du quota Gemini normal — sur " +
                "une clé sans facturation activée, ce quota est souvent inutilisable. Active la " +
                "facturation sur le projet Google Cloud de cette clé pour débloquer Découvrir."
        } else {
            ""
        }

        val parsed = runCatching {
            val error = (Json.parseToJsonElement(body.orEmpty()) as? JsonObject)?.get("error") as? JsonObject
            val message = (error?.get("message") as? JsonPrimitive)?.content ?: return@runCatching null
            val status = (error?.get("status") as? JsonPrimitive)?.content

            // Pour un 429, le détail précise EXACTEMENT quel quota est dépassé (par minute, par
            // jour, par modèle…) — sans ça on ne peut que deviner lequel des quatre limites du
            // palier gratuit a été touché.
            val quotaMetric = (error?.get("details") as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { (it["@type"] as? JsonPrimitive)?.content?.contains("QuotaFailure") == true }
                ?.get("violations")?.let { it as? JsonArray }
                ?.mapNotNull { (it as? JsonObject)?.get("quotaMetric") as? JsonPrimitive }
                ?.joinToString(", ") { it.content }

            buildString {
                append(message)
                if (status != null) append(" ($status)")
                if (!quotaMetric.isNullOrBlank()) append(" — quota concerné : $quotaMetric")
            }
        }.getOrNull()
        if (parsed != null) return "Erreur $code — $parsed$groundingNote"

        return when (code) {
            400 -> "Requête invalide (400) — vérifie le nom du modèle."
            401, 403 -> "Clé refusée ($code) — vérifie qu'elle est correctement copiée et active dans Google AI Studio."
            429 -> "Limite atteinte (429) — quota dépassé côté Google pour cette clé/ce modèle en ce moment, réessaie dans une minute.$groundingNote"
            else -> "Erreur HTTP $code${body?.let { " : ${it.take(200)}" } ?: ""}"
        }
    }

    private fun extractText(candidate: JsonObject): String? {
        val content = candidate["content"] as? JsonObject
        val parts = content?.get("parts") as? JsonArray
        return parts?.joinToString("") { part ->
            ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.content ?: ""
        }
    }

    private fun extractSources(candidate: JsonObject): List<GroundingSource> {
        val grounding = candidate["groundingMetadata"] as? JsonObject ?: return emptyList()
        val chunks = grounding["groundingChunks"] as? JsonArray ?: return emptyList()
        return chunks.mapNotNull { chunk ->
            val web = (chunk as? JsonObject)?.get("web") as? JsonObject ?: return@mapNotNull null
            val uri = (web["uri"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val title = (web["title"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: uri
            GroundingSource(title, uri)
        }
    }

    /** Gemini répond parfois avec des barrières de code markdown malgré la consigne — on les
     *  retire avant de parser, plutôt que d'échouer sur un JSON par ailleurs valide. */
    private fun parseDiscoveredPlaces(text: String): List<DiscoveredPlace> {
        val cleaned = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            val array = Json.parseToJsonElement(cleaned) as? JsonArray ?: return emptyList()
            array.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val name = (obj["nom"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val description = (obj["description"] as? JsonPrimitive)?.content.orEmpty()
                DiscoveredPlace(name, description)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
