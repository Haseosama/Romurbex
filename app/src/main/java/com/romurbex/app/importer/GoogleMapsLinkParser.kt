package com.romurbex.app.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Convertit un lien Google Maps (partagé depuis l'appli ou collé depuis un navigateur) en
 * lieu(x) avec coordonnées.
 *
 * Vérifié en pratique (pas seulement documenté) contre de vrais liens partagés depuis l'appli
 * Google Maps Android, qui résolvent vers des formats très différents selon ce qui est partagé :
 * - une LISTE de lieux enregistrés (ex. « À visiter ») résout vers une URL contenant
 *   `!2s<jeton>!3e2` — cette URL ne contient elle-même aucune coordonnée, mais le jeton permet
 *   d'interroger l'API publique (non authentifiée) qui alimente l'aperçu de liste partagée et
 *   d'en récupérer tous les lieux d'un coup ;
 * - une épingle posée à la main résout vers `/maps/search/<lat>,+<lng>` — coordonnées dans l'URL ;
 * - un lieu nommé (fiche établissement) résout vers `/maps/place/<nom>/data=!4m2!3m1!1s<id>` sans
 *   aucune coordonnée (juste un identifiant interne) : on géocode alors le nom/l'adresse extrait
 *   de l'URL via Nominatim (OpenStreetMap, gratuit, déjà utilisé par la carte de l'appli).
 */
object GoogleMapsLinkParser {

    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    // Jeton d'une liste partagée : .../data=...!2s<jeton>!3eN — le chiffre après !3e varie
    // selon le type de liste (vérifié : !3e2 pour une liste "à visiter", !3e3 pour une liste
    // "visité"), donc on ne fige pas sa valeur, seulement la forme générale.
    private val LIST_TOKEN = Regex("""!2s([A-Za-z0-9_-]{15,})!3e\d+""")

    // Coordonnée précise du point, présente sur certains liens (copie depuis la barre
    // d'adresse d'un navigateur) : !3d<lat>!4d<lng>.
    private val PIN_COORD = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")

    // Épingle posée à la main ("partager cette position") : /maps/search/lat,+lng
    private val SEARCH_COORD = Regex("""/maps/search/(-?\d+\.\d+),\+?(-?\d+\.\d+)""")

    // Position de la caméra sur la carte, copiée depuis la barre d'adresse : /@lat,lng,zoom
    private val AT_COORD = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")

    // Lien de recherche/partage à l'ancienne : ?q=lat,lng ou &ll=lat,lng
    private val QUERY_COORD = Regex("""[?&](?:q|ll|query)=(-?\d+\.\d+),(-?\d+\.\d+)""")

    // Nom/adresse d'un lieu nommé uniquement.
    private val PLACE_NAME = Regex("""/maps/place/([^/@]+)""")

    private val COORD_STRING = Regex("""^-?\d+\.\d+,\s*-?\d+\.\d+$""")

    fun looksLikeMapsLink(text: String): Boolean {
        val t = text.trim().lowercase()
        return t.contains("google.com/maps") || t.contains("goo.gl/maps") ||
            t.contains("maps.app.goo.gl") || t.contains("maps.google.")
    }

    /**
     * Résout le lien et retourne le(s) lieu(x) trouvé(s) : une liste entière si c'est un lien de
     * liste partagée, sinon un seul lieu.
     * @throws IllegalArgumentException si aucune coordonnée n'a pu être extraite du lien.
     */
    suspend fun parse(rawUrl: String): List<ImportedPlace> {
        val trimmed = rawUrl.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        val chain = runCatching { resolveRedirectChain(trimmed) }.getOrDefault(listOf(trimmed))
        // Les coordonnées (ou le jeton de liste) apparaissent dès le premier saut de
        // redirection : pas besoin d'aller jusqu'à la page finale (qui passe souvent par un
        // écran de consentement cookies côté UE).
        val combined = chain.joinToString("\n")

        LIST_TOKEN.find(combined)?.groupValues?.get(1)?.let { token ->
            val places = runCatching { fetchList(token) }.getOrDefault(emptyList())
            if (places.isNotEmpty()) return places
        }

        val single = parseSinglePlace(combined)
        if (single != null) return listOf(single)

        throw IllegalArgumentException(
            "Aucune coordonnée trouvée dans ce lien. Pour une épingle posée à la main, partage " +
                "« cette position » depuis Maps plutôt que la fiche d'un établissement, ou saisis " +
                "les coordonnées à la main dans Romurbex.",
        )
    }

    private fun parseSinglePlace(resolved: String): ImportedPlace? {
        val rawName = PLACE_NAME.find(resolved)?.groupValues?.get(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?.replace('+', ' ')
            ?.takeIf { it.isNotBlank() }

        val coords = PIN_COORD.find(resolved)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            ?: SEARCH_COORD.find(resolved)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            ?: QUERY_COORD.find(resolved)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            ?: AT_COORD.find(resolved)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            ?: rawName?.let { geocodeAddress(it) }
            ?: return null

        return ImportedPlace(rawName ?: "Lieu importé", "", coords.first, coords.second)
    }

    /** Suit les redirections HTTP d'un lien court (maps.app.goo.gl, goo.gl/maps) et retourne chaque URL visitée. */
    private fun resolveRedirectChain(url: String): List<String> {
        val chain = mutableListOf(url)
        var current = url
        repeat(6) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", MOBILE_UA)
            try {
                connection.connect()
                val code = connection.responseCode
                val location = connection.getHeaderField("Location")
                if (code in 300..399 && !location.isNullOrBlank()) {
                    current = location
                    chain += current
                } else {
                    return chain
                }
            } finally {
                connection.disconnect()
            }
        }
        return chain
    }

    /**
     * Récupère tous les lieux d'une liste partagée via l'API publique (non authentifiée) qui
     * alimente son aperçu — la même utilisée par la page web quand on ouvre le lien sans être
     * connecté. Jusqu'à 2000 lieux en un seul appel.
     */
    private fun fetchList(token: String): List<ImportedPlace> {
        val pb = "!1m6!1s$token!2e3!3m1!1e1!3m1!1e9!2e2!3e2!4i2000!6m3!1sr!15i1!28e2!16b1"
        val query = "authuser=0&hl=fr&gl=fr&pb=" + URLEncoder.encode(pb, "UTF-8")
        val connection = URL("https://www.google.com/maps/preview/entitylist/getlist?$query")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", MOBILE_UA)
        return try {
            connection.connect()
            if (connection.responseCode != 200) return emptyList()
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            parseListEntries(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseListEntries(body: String): List<ImportedPlace> {
        val cleaned = body.removePrefix(")]}'").trimStart('\n', '\r', ' ')
        val root = runCatching { Json.parseToJsonElement(cleaned) }.getOrNull() as? JsonArray ?: return emptyList()
        val outer = root.getOrNull(0) as? JsonArray ?: return emptyList()
        val entries = outer.getOrNull(8) as? JsonArray ?: return emptyList()

        return entries.mapNotNull { entryElement ->
            val entry = entryElement as? JsonArray ?: return@mapNotNull null
            val locationInfo = entry.getOrNull(1) as? JsonArray ?: return@mapNotNull null
            val coords = locationInfo.getOrNull(5) as? JsonArray ?: return@mapNotNull null
            val lat = coords.getOrNull(2)?.asDouble() ?: return@mapNotNull null
            val lng = coords.getOrNull(3)?.asDouble() ?: return@mapNotNull null

            val name = entry.getOrNull(3)?.asNonBlankString()
                ?: entry.getOrNull(2)?.asNonBlankString()
                ?: "Lieu importé"

            val description = locationInfo.getOrNull(4)?.asNonBlankString()
                ?.takeIf { !COORD_STRING.matches(it) }
                ?: ""

            ImportedPlace(name, description, lat, lng)
        }
    }

    private fun JsonElement.asDouble(): Double? =
        (this as? JsonPrimitive)?.takeIf { it != JsonNull }?.content?.toDoubleOrNull()

    private fun JsonElement.asNonBlankString(): String? =
        (this as? JsonPrimitive)?.takeIf { it != JsonNull }?.content?.takeIf { it.isNotBlank() }

    /**
     * Repli pour les liens de fiche établissement, qui ne contiennent aucune coordonnée :
     * géocode le nom/l'adresse extrait de l'URL via l'API publique Nominatim (OpenStreetMap).
     */
    private fun geocodeAddress(query: String): Pair<Double, Double>? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val connection = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.requestMethod = "GET"
        // Nominatim exige un User-Agent identifiant l'appli — voir sa politique d'usage.
        connection.setRequestProperty("User-Agent", "Romurbex/0.1 (Android, usage personnel, non commercial)")
        return try {
            connection.connect()
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().readText()
            val first = (Json.parseToJsonElement(body) as? JsonArray)?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                ?: return null
            val lat = first["lat"]?.asDouble() ?: return null
            val lon = first["lon"]?.asDouble() ?: return null
            lat to lon
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
