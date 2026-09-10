package com.romurbex.app.ui.map

import com.romurbex.app.data.LocationEntity
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection

/** Un ou plusieurs lieux regroupés parce qu'ils tombent à moins de [radiusPx] les uns des
 *  autres à l'écran, au zoom actuel. */
data class MapCluster(val center: GeoPoint, val locations: List<LocationEntity>)

/**
 * Regroupement glouton en pixels-écran : pas de bibliothèque de clustering dédiée dans la
 * version d'osmdroid utilisée (retirée du cœur de la lib), donc implémentation directe —
 * simple mais suffisante pour quelques milliers de lieux, recalculée seulement après un
 * temporisateur au pan/zoom (voir [LocationMapView]), pas à chaque frame.
 */
fun clusterLocations(
    locations: List<LocationEntity>,
    projection: Projection,
    radiusPx: Double,
): List<MapCluster> {
    if (locations.isEmpty()) return emptyList()

    data class Projected(val location: LocationEntity, val x: Int, val y: Int)

    val projected = locations.map { location ->
        val point = projection.toPixels(GeoPoint(location.latitude, location.longitude), null)
        Projected(location, point.x, point.y)
    }

    val used = BooleanArray(projected.size)
    val radiusSq = radiusPx * radiusPx
    val clusters = mutableListOf<MapCluster>()

    for (i in projected.indices) {
        if (used[i]) continue
        val seed = projected[i]
        used[i] = true
        val group = mutableListOf(seed.location)

        for (j in i + 1 until projected.size) {
            if (used[j]) continue
            val other = projected[j]
            val dx = (seed.x - other.x).toDouble()
            val dy = (seed.y - other.y).toDouble()
            if (dx * dx + dy * dy <= radiusSq) {
                group += other.location
                used[j] = true
            }
        }

        val centerLat = group.sumOf { it.latitude } / group.size
        val centerLng = group.sumOf { it.longitude } / group.size
        clusters += MapCluster(GeoPoint(centerLat, centerLng), group)
    }

    return clusters
}
