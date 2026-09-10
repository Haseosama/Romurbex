package com.romurbex.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.romurbex.app.data.LocationEntity
import com.romurbex.app.data.MapPreferences
import com.romurbex.app.ui.LocationsViewModel
import com.romurbex.app.ui.components.CategoryFilterRow
import com.romurbex.app.ui.components.CategoryPickerDialog
import com.romurbex.app.ui.components.VoiceSearchField
import com.romurbex.app.ui.components.color
import com.romurbex.app.ui.components.icon
import com.romurbex.app.ui.components.label
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.TilesOverlay

private val FRANCE_CENTER = GeoPoint(46.6, 2.4)

/** Rayon « autour de moi » dans lequel un pin s'affiche agrandi. */
private const val NEARBY_RADIUS_METERS = 2000.0

@Composable
fun MapScreen(
    onLocationClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    viewModel: LocationsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val locations by viewModel.visibleLocations.collectAsStateWithLifecycle()
    val searchQuery by viewModel.query.collectAsStateWithLifecycle()
    val category by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    var selectedLocation by remember { mutableStateOf<LocationEntity?>(null) }
    var iconPickerFor by remember { mutableStateOf<LocationEntity?>(null) }
    var clusterPicker by remember { mutableStateOf<List<LocationEntity>?>(null) }
    val mapPrefs = remember { MapPreferences.get(context) }
    val isSatellite by mapPrefs.satelliteMode.collectAsStateWithLifecycle()
    var hasAutoFitted by remember { mutableStateOf(false) }
    var hasAttemptedGpsCenter by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }

    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var downloadTotal by remember { mutableIntStateOf(1) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }

    // Re-résolu depuis la liste vivante à chaque recomposition : reflète tout de suite un
    // changement d'icône fait via le sélecteur, sans dépendre d'un second aller-retour Room.
    val livePreview = selectedLocation?.let { sel -> locations.firstOrNull { it.id == sel.id } ?: sel }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(6.0)
            controller.setCenter(FRANCE_CENTER)
        }
    }

    // Repli si la position GPS n'a pas pu centrer la carte (permission refusée, pas de fix,
    // GPS coupé…) : cadre au moins sur les lieux plutôt que de rester bloqué sur la France.
    LaunchedEffect(locations, hasAttemptedGpsCenter, userLocation) {
        if (!hasAutoFitted && hasAttemptedGpsCenter && userLocation == null && locations.isNotEmpty()) {
            hasAutoFitted = true
            val box = BoundingBox.fromGeoPoints(locations.map { GeoPoint(it.latitude, it.longitude) })
            mapView.post { mapView.zoomToBoundingBox(box, false, 100) }
        }
    }

    // Dézoome pour cadrer tous les résultats d'une recherche d'un coup — sinon le zoom précédent
    // peut laisser des correspondances hors écran, invisibles tant qu'on n'a pas pensé à dézoomer
    // soi-même. Se redéclenche à chaque nouvelle recherche (query non vide), pas seulement la
    // première fois — clé sur searchQuery pour ignorer les mises à jour de locations sans rapport
    // (icône changée, favori basculé…).
    LaunchedEffect(locations, searchQuery) {
        // Clé sur locations (pas seulement searchQuery) : les résultats arrivent avec le délai du
        // debounce de la recherche, donc il faut réagir à leur mise à jour, pas au texte tapé.
        if (searchQuery.isNotBlank() && locations.isNotEmpty()) {
            val box = BoundingBox.fromGeoPoints(locations.map { GeoPoint(it.latitude, it.longitude) })
            mapView.post { mapView.zoomToBoundingBox(box, true, 100) }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            centerOnCurrentLocation(context, mapView) { point -> userLocation = point }
        } else {
            hasAttemptedGpsCenter = true
        }
    }

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // L'appli s'ouvre systématiquement sur la position actuelle plutôt que sur la France ou
    // sur le cadrage des lieux — demande la permission au premier passage sur cet écran si
    // besoin, sans attendre un tap sur le bouton dédié.
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            hasAttemptedGpsCenter = true
            centerOnCurrentLocation(context, mapView) { point -> userLocation = point }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // Même logique pour la galerie : demandée ici une bonne fois pour toutes plutôt qu'au
        // moment précis où une fonctionnalité en a besoin (rattachement de photos par GPS).
        if (ContextCompat.checkSelfPermission(context, mediaPermission) != PackageManager.PERMISSION_GRANTED) {
            mediaPermissionLauncher.launch(mediaPermission)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LocationMapView(
            mapView = mapView,
            locations = locations,
            isSatellite = isSatellite,
            userLocation = userLocation,
            onMarkerTap = { selectedLocation = it },
            onClusterTap = { clusterPicker = it },
            onMapTap = { selectedLocation = null },
            modifier = Modifier.fillMaxSize(),
        )

        clusterPicker?.let { grouped ->
            AlertDialog(
                onDismissRequest = { clusterPicker = null },
                title = { Text("${grouped.size} lieux au même endroit") },
                text = {
                    LazyColumn {
                        items(grouped) { loc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedLocation = loc
                                        clusterPicker = null
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(loc.category.icon(), contentDescription = null)
                                Text(loc.name, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { clusterPicker = null }) { Text("Fermer") }
                },
            )
        }

        Column(modifier = Modifier.padding(top = 12.dp)) {
            VoiceSearchField(
                viewModel = viewModel,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
            CategoryFilterRow(
                selected = category,
                favoritesOnly = favoritesOnly,
                onSelect = { viewModel.categoryFilter.value = it },
                onFavoritesToggle = { viewModel.favoritesOnly.value = !favoritesOnly },
            )
            downloadProgress?.let { progress ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Téléchargement de la zone hors-ligne…", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { (progress.toFloat() / downloadTotal.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                }
            }
            downloadMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = selectedLocation != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            livePreview?.let { location ->
                LocationPreviewCard(
                    location = location,
                    onClick = { onLocationClick(location.id) },
                    onIconClick = { iconPickerFor = location },
                    onDismiss = { selectedLocation = null },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SmallFloatingActionButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        centerOnCurrentLocation(context, mapView) { point -> userLocation = point }
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Ma position")
            }
            SmallFloatingActionButton(
                onClick = {
                    downloadMessage = null
                    downloadOfflineArea(
                        context = context,
                        mapView = mapView,
                        isSatellite = isSatellite,
                        onStart = { total -> downloadTotal = total.coerceAtLeast(1); downloadProgress = 0 },
                        onProgress = { progress -> downloadProgress = progress },
                        onDone = { errors ->
                            downloadProgress = null
                            downloadMessage = if (errors > 0) {
                                "$errors tuile(s) n'ont pas pu être téléchargées."
                            } else {
                                "Zone téléchargée — consultable hors connexion."
                            }
                        },
                        onRefused = { message -> downloadMessage = message },
                    )
                },
                modifier = Modifier,
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = "Télécharger la zone hors-ligne")
            }
            SmallFloatingActionButton(onClick = { mapPrefs.setSatelliteMode(!isSatellite) }) {
                Icon(
                    if (isSatellite) Icons.Filled.Map else Icons.Filled.Satellite,
                    contentDescription = if (isSatellite) "Vue carte" else "Vue satellite",
                )
            }
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter un lieu")
            }
        }
    }

    iconPickerFor?.let { location ->
        CategoryPickerDialog(
            current = location.category,
            onSelect = { newCategory ->
                viewModel.setCategory(location, newCategory)
                iconPickerFor = null
            },
            onDismiss = { iconPickerFor = null },
        )
    }
}

@SuppressLint("MissingPermission")
private fun centerOnCurrentLocation(context: android.content.Context, mapView: MapView, onResult: (point: GeoPoint?) -> Unit = {}) {
    LocationServices.getFusedLocationProviderClient(context).lastLocation
        .addOnSuccessListener { location ->
            if (location != null) {
                val point = GeoPoint(location.latitude, location.longitude)
                mapView.controller.animateTo(point, 16.0, 600L)
                onResult(point)
            } else {
                onResult(null)
            }
        }
        .addOnFailureListener { onResult(null) }
}

/**
 * La vue carte standard (Mapnik/OpenStreetMap) refuse le téléchargement en masse : c'est
 * osmdroid lui-même qui applique la politique d'usage officielle d'OpenStreetMap
 * (operations.osmfoundation.org/policies/tiles) en levant [TileSourcePolicyException] à la
 * construction du [CacheManager] — pas un bug, une règle du serveur de tuiles gratuit. La vue
 * satellite (Esri) n'a pas cette restriction et peut être téléchargée normalement.
 */
private fun downloadOfflineArea(
    context: android.content.Context,
    mapView: MapView,
    isSatellite: Boolean,
    onStart: (total: Int) -> Unit,
    onProgress: (progress: Int) -> Unit,
    onDone: (errors: Int) -> Unit,
    onRefused: (String) -> Unit,
) {
    if (!isSatellite) {
        onRefused(
            "OpenStreetMap interdit le téléchargement en masse depuis la vue carte standard " +
                "(règle de leur serveur de tuiles gratuit). Passe en vue satellite (bouton au-dessus) " +
                "pour télécharger une zone hors-ligne.",
        )
        return
    }

    val cacheManager = try {
        CacheManager(mapView)
    } catch (e: Exception) {
        onRefused("Le téléchargement hors-ligne n'est pas possible pour cette vue.")
        return
    }

    val boundingBox = mapView.boundingBox
    val zoomMin = mapView.zoomLevelDouble.toInt()
    val zoomMax = (zoomMin + 3).coerceAtMost(19)

    // Une zone dézoomée (ex. le pays entier) représente vite des dizaines de milliers de
    // tuiles — inutilisable en pratique et long à s'en rendre compte une fois lancé. On le
    // détecte avant de démarrer plutôt que de laisser l'utilisateur découvrir le problème
    // après une longue attente.
    val possibleTiles = cacheManager.possibleTilesInArea(boundingBox, zoomMin, zoomMax)
    if (possibleTiles > 4000) {
        onRefused(
            "Zone trop grande pour un téléchargement hors-ligne ($possibleTiles tuiles estimées) — " +
                "zoome sur une zone plus petite (une ville, pas tout un pays) avant de retélécharger.",
        )
        return
    }

    cacheManager.downloadAreaAsyncNoUI(
        context,
        boundingBox,
        zoomMin,
        zoomMax,
        object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() = onDone(0)
            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) = onProgress(progress)
            override fun downloadStarted() = Unit
            override fun setPossibleTilesInArea(total: Int) = onStart(total)
            override fun onTaskFailed(errors: Int) = onDone(errors)
        },
    )
}

/** Aperçu façon Google Maps affiché quand on tape un pin : nom, catégorie et son icône, risque. */
@Composable
private fun LocationPreviewCard(
    location: LocationEntity,
    onClick: () -> Unit,
    onIconClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                location.category.icon(),
                contentDescription = "Changer l'icône",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp).clickable(onClick = onIconClick),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(location.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "${location.category.label()} · ${location.riskLevel.label()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = location.riskLevel.color(),
                    maxLines = 1,
                )
                if (location.sourceFolderName.isNotBlank()) {
                    Text(
                        location.sourceFolderName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Fermer")
            }
        }
    }
}

@Composable
private fun LocationMapView(
    mapView: MapView,
    locations: List<LocationEntity>,
    isSatellite: Boolean,
    userLocation: GeoPoint?,
    onMarkerTap: (LocationEntity) -> Unit,
    onClusterTap: (List<LocationEntity>) -> Unit,
    onMapTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val onTap by rememberUpdatedState(onMarkerTap)
    val onClusterTapState by rememberUpdatedState(onClusterTap)
    val onMapClick by rememberUpdatedState(onMapTap)
    val currentLocations by rememberUpdatedState(locations)
    // Lu par le MapListener natif ajouté une seule fois plus bas (voir DisposableEffect) : sans
    // rememberUpdatedState, il verrait pour toujours la valeur de ce tout premier montage, même
    // après un bascule carte/satellite ou une nouvelle position GPS ultérieurs.
    val satelliteMode by rememberUpdatedState(isSatellite)
    val nearbyOrigin by rememberUpdatedState(userLocation)
    // Créé une seule fois et réutilisé à chaque refreshOverlays() (appelé à chaque pan/zoom) —
    // en fabriquer un nouveau à chaque fois recréerait aussi son pool de threads de
    // téléchargement de tuiles à chaque fois, une fuite de ressources.
    val labelsOverlay = remember {
        TilesOverlay(MapTileProviderBasic(context, SatelliteLabelsTileSource), context).apply {
            loadingBackgroundColor = android.graphics.Color.TRANSPARENT
        }
    }
    DisposableEffect(labelsOverlay) {
        onDispose { labelsOverlay.onDetach(mapView) }
    }

    fun refreshOverlays() {
        val projection = mapView.projection
        mapView.overlays.clear()

        if (satelliteMode) {
            mapView.overlays.add(labelsOverlay)
        }

        // Ferme la carte d'aperçu quand on tape la carte en dehors d'un pin.
        mapView.overlays.add(
            MapEventsOverlay(
                object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        onMapClick()
                        return false
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                },
            ),
        )

        // Regroupement désactivé de fait : rayon nul, donc seuls des lieux à des coordonnées
        // GPS strictement identiques (même pixel écran, peu importe le zoom) fusionnent encore —
        // tout le reste s'affiche toujours comme des pins individuels, jamais une bulle "X lieux".
        val clusters = clusterLocations(currentLocations, projection, radiusPx = 0.0)
        clusters.forEach { cluster ->
            val single = cluster.locations.singleOrNull()
            val marker = Marker(mapView)
            if (single != null) {
                val position = GeoPoint(single.latitude, single.longitude)
                val isNearby = nearbyOrigin?.let { it.distanceToAsDouble(position) <= NEARBY_RADIUS_METERS } ?: false
                marker.position = position
                marker.title = single.name
                marker.icon = MarkerIconFactory.iconFor(context, single.category, large = isNearby)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.setOnMarkerClickListener { _, _ ->
                    onTap(single)
                    true
                }
            } else {
                marker.position = cluster.center
                marker.icon = MarkerIconFactory.clusterIcon(context, cluster.locations.size)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                // Rayon de regroupement nul (voir plus haut) : si une bulle apparaît quand même,
                // c'est que ces lieux sont à des coordonnées GPS littéralement identiques — zoomer
                // ne les séparerait jamais. On propose donc de choisir lequel ouvrir.
                marker.setOnMarkerClickListener { _, _ ->
                    onClusterTapState(cluster.locations)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    // osmdroid ne fait pas partie de la composition Compose : un pan/zoom de l'utilisateur ne
    // déclenche aucune recomposition, donc le regroupement doit aussi se recalculer depuis ce
    // listener osmdroid natif, pas seulement depuis le bloc `update` d'AndroidView.
    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                refreshOverlays()
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                refreshOverlays()
                return true
            }
        }
        val delayed = DelayedMapListener(listener, 150L)
        mapView.addMapListener(delayed)
        onDispose {
            mapView.removeMapListener(delayed)
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier) { view ->
        view.setTileSource(if (isSatellite) SatelliteTileSource else TileSourceFactory.MAPNIK)
        refreshOverlays()
    }
}
