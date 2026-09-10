package com.romurbex.app.search

import com.romurbex.app.data.LocationEntity
import java.text.Normalizer
import kotlin.math.min

/**
 * Recherche locale sur le nom du lieu, le nom du dossier d'origine (import/photos) et la
 * description : tape n'importe quel mot de l'un de ces champs et le lieu remonte, avec
 * tolérance aux fautes de frappe et aux accents. Entièrement hors-ligne — pas de modèle
 * embarqué nécessaire pour ce cas d'usage (recherche par mots, pas par sens).
 */
object SearchEngine {

    fun search(locations: List<LocationEntity>, query: String): List<LocationEntity> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return locations

        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }

        return locations
            .map { it to score(it, queryTokens) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun score(location: LocationEntity, queryTokens: List<String>): Int {
        val haystackTokens = tokensOf(location)
        var total = 0
        for (q in queryTokens) {
            val best = haystackTokens.maxOfOrNull { tokenScore(q, it) } ?: 0
            if (best == 0) return 0 // chaque mot tapé doit correspondre à quelque chose
            total += best
        }
        return total
    }

    private fun tokenScore(query: String, candidate: String): Int = when {
        candidate == query -> 100
        candidate.startsWith(query) -> 80
        candidate.contains(query) -> 60
        // Les préfixes/sous-chaînes couvrent déjà les lettres manquantes en fin de mot — la
        // tolérance floue ne sert donc qu'aux fautes au milieu d'un mot, et doit rester stricte :
        // à distance 2, "Paulin" (un nom de famille) matchait "moulin" par coïncidence.
        query.length >= 5 && levenshtein(query, candidate) <= maxEditDistance(query.length) -> 40
        else -> 0
    }

    private fun maxEditDistance(len: Int): Int = when {
        len <= 7 -> 1
        else -> 2
    }

    // L'adresse est volontairement exclue : les noms de rue français référencent très souvent un
    // moulin, un château, une église (toponymie ou hommage à une personne) sans rapport avec le
    // bâtiment lui-même — une recherche par mot-clé remontait alors des lieux hors-sujet juste
    // parce qu'ils se trouvaient "Rue du Moulin" ou "Rue Jean Moulin".
    private fun tokensOf(location: LocationEntity): List<String> {
        val fields = listOf(location.name, location.sourceFolderName, location.description, location.notes)
        return fields.flatMap { normalize(it).split(Regex("[\\s/_\\-.]+")) }.filter { it.isNotBlank() }
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }
}
