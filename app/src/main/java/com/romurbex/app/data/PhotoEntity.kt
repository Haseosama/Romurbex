package com.romurbex.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("locationId")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationId: Long,
    /** URI persistée (SAF) ou chemin de fichier interne vers la photo. */
    val uri: String,
    val dateAdded: Long = System.currentTimeMillis(),
)
