package com.romurbex.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.romurbex.app.ui.detail.LocationDetailScreen
import com.romurbex.app.ui.discover.DiscoverScreen
import com.romurbex.app.ui.edit.EditLocationScreen
import com.romurbex.app.ui.importscreen.ImportScreen
import com.romurbex.app.ui.list.LocationListScreen
import com.romurbex.app.ui.lists.ListsScreen
import com.romurbex.app.ui.map.MapScreen
import com.romurbex.app.ui.settings.SettingsScreen

private val bottomTabs = listOf(Routes.Map, Routes.List)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomurbexNavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.Map.route || currentRoute == Routes.List.route

    Scaffold(
        topBar = {
            if (showBottomBar) {
                TopAppBar(
                    title = { Text("Romurbex") },
                    actions = {
                        IconButton(onClick = { navController.navigate(Routes.Discover.route) }) {
                            Icon(Icons.Filled.TravelExplore, contentDescription = "Découvrir des lieux")
                        }
                        IconButton(onClick = { navController.navigate(Routes.Lists.route) }) {
                            Icon(Icons.Filled.Layers, contentDescription = "Listes importées")
                        }
                        IconButton(onClick = { navController.navigate(Routes.Import.route) }) {
                            Icon(Icons.Filled.UploadFile, contentDescription = "Importer depuis Google Maps")
                        }
                        IconButton(onClick = { navController.navigate(Routes.Settings.route) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.Map.route,
                        onClick = { navController.navigateToTab(Routes.Map.route) },
                        icon = { Icon(Icons.Filled.Map, contentDescription = null) },
                        label = { Text("Carte") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.List.route,
                        onClick = { navController.navigateToTab(Routes.List.route) },
                        icon = { Icon(Icons.Filled.List, contentDescription = null) },
                        label = { Text("Liste") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Map.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.Map.route) {
                MapScreen(
                    onLocationClick = { id -> navController.navigate(Routes.Detail.of(id)) },
                    onAddClick = { navController.navigate(Routes.Add.of()) },
                )
            }
            composable(Routes.List.route) {
                LocationListScreen(onLocationClick = { id -> navController.navigate(Routes.Detail.of(id)) })
            }
            composable(
                route = Routes.Detail.route,
                arguments = listOf(navArgument("locationId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("locationId") ?: return@composable
                LocationDetailScreen(
                    locationId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { editId -> navController.navigate(Routes.Edit.of(editId)) },
                    onDeleted = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.Add.route,
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("description") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("lat") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("lng") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("list") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                EditLocationScreen(
                    locationId = null,
                    initialName = entry.arguments?.getString("name").orEmpty(),
                    initialDescription = entry.arguments?.getString("description").orEmpty(),
                    initialLatitude = entry.arguments?.getString("lat").orEmpty(),
                    initialLongitude = entry.arguments?.getString("lng").orEmpty(),
                    initialSourceFolderName = entry.arguments?.getString("list").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onSaved = { id -> navController.navigate(Routes.Detail.of(id)) { popUpTo(Routes.Map.route) } },
                )
            }
            composable(
                route = Routes.Edit.route,
                arguments = listOf(navArgument("locationId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("locationId") ?: return@composable
                EditLocationScreen(
                    locationId = id,
                    onBack = { navController.popBackStack() },
                    onSaved = { savedId -> navController.navigate(Routes.Detail.of(savedId)) { popUpTo(Routes.Map.route) } },
                )
            }
            composable(Routes.Import.route) {
                ImportScreen(
                    onBack = { navController.popBackStack() },
                    onImported = { navController.popBackStack() },
                )
            }
            composable(Routes.Lists.route) {
                ListsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.Discover.route) {
                DiscoverScreen(
                    onBack = { navController.popBackStack() },
                    onAddPlace = { place ->
                        navController.navigate(Routes.Add.of(name = place.name, description = place.description))
                    },
                    onAddParsedPin = { pin ->
                        navController.navigate(
                            Routes.Add.of(
                                name = pin.name,
                                description = pin.description,
                                lat = pin.latitude,
                                lng = pin.longitude,
                                list = "DianeG",
                            ),
                        )
                    },
                )
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
