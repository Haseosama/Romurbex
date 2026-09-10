package com.romurbex.app.ui.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * Imagerie satellite Esri World Imagery — gratuite et sans clé API, dans le même esprit que le
 * choix d'OpenStreetMap pour la vue carte (pas de compte Google Cloud/facturation nécessaire).
 * Son schéma d'URL ArcGIS REST utilise l'ordre {z}/{y}/{x} (inversé par rapport au {z}/{x}/{y}
 * standard OSM), d'où l'implémentation manuelle de [getTileURLString] plutôt qu'un XYTileSource.
 */
object SatelliteTileSource : OnlineTileSourceBase(
    "EsriWorldImagery",
    0,
    19,
    256,
    "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Esri, Maxar, Earthstar Geographics, GIS User Community",
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "${baseUrl}$zoom/$y/$x"
    }
}

/**
 * Calque transparent (noms de villes/pays, frontières) posé par-dessus [SatelliteTileSource] —
 * la photo satellite seule n'a aucun texte, contrairement à la vue carte standard qui a déjà
 * les noms de lieux intégrés à ses tuiles. Même service Esri, gratuit et sans clé.
 */
object SatelliteLabelsTileSource : OnlineTileSourceBase(
    "EsriWorldBoundariesAndPlaces",
    0,
    19,
    256,
    "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/"),
    "Esri",
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "${baseUrl}$zoom/$y/$x"
    }
}
