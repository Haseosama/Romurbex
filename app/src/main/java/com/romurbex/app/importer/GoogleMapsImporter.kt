package com.romurbex.app.importer

import android.content.Context
import android.net.Uri
import com.romurbex.app.data.LocationCategory
import com.romurbex.app.data.LocationEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Importe une liste de lieux Google Maps exportée par l'utilisateur :
 * - KML (Google My Maps > Exporter vers KML)
 * - GeoJSON (Google Takeout > Maps (vos lieux) > Enregistrés > *.json)
 * - CSV simple (nom,latitude,longitude[,description])
 */
data class ImportedPlace(
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
)

object GoogleMapsImporter {

    fun parse(context: Context, uri: Uri): List<ImportedPlace> {
        val name = queryDisplayName(context, uri).lowercase()
        context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Impossible d'ouvrir le fichier sélectionné." }
            return when {
                name.endsWith(".kml") -> parseKml(stream)
                name.endsWith(".json") || name.endsWith(".geojson") -> parseGeoJson(stream)
                name.endsWith(".csv") -> parseCsv(stream)
                else -> parseAuto(stream)
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex("_display_name")
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else ""
        } ?: (uri.lastPathSegment ?: "")
    }

    private fun parseAuto(stream: InputStream): List<ImportedPlace> {
        val bytes = stream.readBytes()
        val text = bytes.decodeToString().trimStart()
        return when {
            text.startsWith("<?xml") || text.startsWith("<kml") -> parseKml(bytes.inputStream())
            text.startsWith("{") || text.startsWith("[") -> parseGeoJson(bytes.inputStream())
            else -> parseCsv(bytes.inputStream())
        }
    }

    private fun parseKml(stream: InputStream): List<ImportedPlace> {
        val results = mutableListOf<ImportedPlace>()
        val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, "UTF-8")

        var name = ""
        var description = ""
        var coordinatesText = ""
        var currentTag = ""
        var inPlacemark = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "Placemark") {
                        inPlacemark = true
                        name = ""
                        description = ""
                        coordinatesText = ""
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inPlacemark) {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "name" -> name = text
                                "description" -> description = text
                                "coordinates" -> coordinatesText = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "Placemark") {
                        inPlacemark = false
                        val firstPoint = coordinatesText.trim().split(Regex("\\s+")).firstOrNull()
                        val parts = firstPoint?.split(",")
                        val lng = parts?.getOrNull(0)?.toDoubleOrNull()
                        val lat = parts?.getOrNull(1)?.toDoubleOrNull()
                        if (name.isNotBlank() && lat != null && lng != null) {
                            results += ImportedPlace(name, description, lat, lng)
                        }
                    }
                }
            }
            event = parser.next()
        }
        return results
    }

    private fun parseGeoJson(stream: InputStream): List<ImportedPlace> {
        val root = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
        val features = root["features"]?.jsonArray ?: JsonArray(emptyList())
        return features.mapNotNull { feature ->
            val obj = feature.jsonObject
            val geometry = obj["geometry"]?.jsonObject ?: return@mapNotNull null
            val coords = geometry["coordinates"]?.jsonArray ?: return@mapNotNull null
            val lng = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val props = obj["properties"]?.jsonObject ?: JsonObject(emptyMap())
            val name = props["Title"]?.jsonPrimitive?.contentOrNull
                ?: props["name"]?.jsonPrimitive?.contentOrNull
                ?: "Lieu importé"
            val description = props["Location"]?.jsonObject?.get("Address")?.jsonPrimitive?.contentOrNull
                ?: props["description"]?.jsonPrimitive?.contentOrNull
                ?: ""
            ImportedPlace(name, description, lat, lng)
        }
    }

    private fun parseCsv(stream: InputStream): List<ImportedPlace> {
        val lines = stream.bufferedReader().readLines()
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().split(",").map { it.trim().lowercase() }
        val nameIdx = header.indexOfFirst { it.contains("name") || it.contains("nom") || it.contains("title") }
        val latIdx = header.indexOfFirst { it.contains("lat") }
        val lngIdx = header.indexOfFirst { it.contains("lon") || it.contains("lng") }
        val descIdx = header.indexOfFirst { it.contains("desc") || it.contains("note") }
        val dataLines = if (nameIdx >= 0 && latIdx >= 0 && lngIdx >= 0) lines.drop(1) else lines

        return dataLines.mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val cols = line.split(",").map { it.trim() }
            val n = if (nameIdx >= 0) nameIdx else 0
            val la = if (latIdx >= 0) latIdx else 1
            val lo = if (lngIdx >= 0) lngIdx else 2
            val lat = cols.getOrNull(la)?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = cols.getOrNull(lo)?.toDoubleOrNull() ?: return@mapNotNull null
            val name = cols.getOrNull(n)?.takeIf { it.isNotBlank() } ?: "Lieu importé"
            val description = descIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it) } ?: ""
            ImportedPlace(name, description, lat, lng)
        }
    }

    fun toEntities(places: List<ImportedPlace>, sourceFolderName: String): List<LocationEntity> =
        places.map {
            LocationEntity(
                name = it.name,
                description = it.description,
                category = LocationCategory.AUTRE,
                latitude = it.latitude,
                longitude = it.longitude,
                sourceFolderName = sourceFolderName,
            )
        }
}

private val JsonPrimitive?.contentOrNull: String?
    get() = this?.takeIf { !it.isString || it.content.isNotBlank() }?.content

private val JsonPrimitive?.doubleOrNull: Double?
    get() = this?.content?.toDoubleOrNull()
