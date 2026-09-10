package com.romurbex.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Visibilité d'une liste importée (regroupée par [LocationEntity.sourceFolderName]).
 * Un nom de liste absent de cette table est considéré visible par défaut — seules les
 * listes explicitement masquées y ont une ligne, ce qui évite d'avoir à migrer les données
 * déjà importées avant l'ajout de cette fonctionnalité.
 */
@Entity(tableName = "list_visibility")
data class ListVisibilityEntity(
    @PrimaryKey val name: String,
    val isVisible: Boolean,
)

data class ListSummary(
    val name: String,
    val count: Int,
)
