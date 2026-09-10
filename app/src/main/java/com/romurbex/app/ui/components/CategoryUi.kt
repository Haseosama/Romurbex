package com.romurbex.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Train
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.romurbex.app.data.LocationCategory
import com.romurbex.app.data.RiskLevel
import com.romurbex.app.ui.theme.DangerRed
import com.romurbex.app.ui.theme.Moss
import com.romurbex.app.ui.theme.Rust

// Aucune icône "moulin" dans le jeu Material Icons utilisé par l'appli (Icons.Filled.*) — dessiné
// à la main plutôt que de deviner un nom d'icône au risque de casser la compilation : une tour
// trapézoïdale et 4 pales en croix autour d'un moyeu, silhouette pleine comme les autres icônes.
private val WindmillIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Windmill",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Tour
            moveTo(10.2f, 9f)
            lineTo(13.8f, 9f)
            lineTo(15.2f, 22f)
            lineTo(8.8f, 22f)
            close()
            // Pale du haut
            moveTo(12f, 7.2f)
            lineTo(10.8f, 1f)
            lineTo(15.2f, 3.6f)
            close()
            // Pale de droite
            moveTo(12f, 7.2f)
            lineTo(18.4f, 5.8f)
            lineTo(15.4f, 10.2f)
            close()
            // Pale du bas
            moveTo(12f, 7.2f)
            lineTo(13.4f, 13.4f)
            lineTo(9f, 10.4f)
            close()
            // Pale de gauche
            moveTo(12f, 7.2f)
            lineTo(5.6f, 8.6f)
            lineTo(8.6f, 4.2f)
            close()
        }
    }.build()
}

fun LocationCategory.label(): String = when (this) {
    LocationCategory.USINE -> "Usine"
    LocationCategory.HOPITAL -> "Hôpital"
    LocationCategory.MAISON -> "Maison"
    LocationCategory.CHATEAU -> "Château"
    LocationCategory.EGLISE -> "Église"
    LocationCategory.ECOLE -> "École"
    LocationCategory.MILITAIRE -> "Militaire"
    LocationCategory.LOISIR -> "Loisir"
    LocationCategory.CARRIERE -> "Carrière"
    LocationCategory.FERME -> "Ferme"
    LocationCategory.GARE -> "Gare"
    LocationCategory.THEATRE -> "Théâtre"
    LocationCategory.PISCINE -> "Piscine"
    LocationCategory.MOULIN -> "Moulin"
    LocationCategory.AUTRE -> "Autre"
}

fun LocationCategory.icon(): ImageVector = when (this) {
    LocationCategory.USINE -> Icons.Filled.Factory
    LocationCategory.HOPITAL -> Icons.Filled.LocalHospital
    LocationCategory.MAISON -> Icons.Filled.House
    LocationCategory.CHATEAU -> Icons.Filled.Castle
    LocationCategory.EGLISE -> Icons.Filled.Church
    LocationCategory.ECOLE -> Icons.Filled.School
    LocationCategory.MILITAIRE -> Icons.Filled.Security
    LocationCategory.LOISIR -> Icons.Filled.Park
    LocationCategory.CARRIERE -> Icons.Filled.Terrain
    LocationCategory.FERME -> Icons.Filled.Agriculture
    LocationCategory.GARE -> Icons.Filled.Train
    LocationCategory.THEATRE -> Icons.Filled.TheaterComedy
    LocationCategory.PISCINE -> Icons.Filled.Pool
    LocationCategory.MOULIN -> WindmillIcon
    LocationCategory.AUTRE -> Icons.Filled.Apartment
}

fun RiskLevel.label(): String = when (this) {
    RiskLevel.FAIBLE -> "Risque faible"
    RiskLevel.MOYEN -> "Risque moyen"
    RiskLevel.ELEVE -> "Risque élevé"
    RiskLevel.INCONNU -> "Risque inconnu"
}

fun RiskLevel.color(): Color = when (this) {
    RiskLevel.FAIBLE -> Moss
    RiskLevel.MOYEN -> Rust
    RiskLevel.ELEVE -> DangerRed
    RiskLevel.INCONNU -> Color(0xFF7A7268)
}
