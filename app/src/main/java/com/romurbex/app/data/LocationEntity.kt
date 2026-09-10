package com.romurbex.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LocationCategory {
    USINE, HOPITAL, MAISON, CHATEAU, EGLISE, ECOLE, MILITAIRE, LOISIR,
    CARRIERE, FERME, GARE, THEATRE, PISCINE, MOULIN, AUTRE
}

enum class RiskLevel {
    FAIBLE, MOYEN, ELEVE, INCONNU
}

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val notes: String = "",
    val category: LocationCategory = LocationCategory.AUTRE,
    val riskLevel: RiskLevel = RiskLevel.INCONNU,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    /** Nom du dossier/liste d'origine (import Google Maps ou dossier de photos) — utilisé par la recherche. */
    val sourceFolderName: String = "",
    val isFavorite: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateVisited: Long? = null,
)
