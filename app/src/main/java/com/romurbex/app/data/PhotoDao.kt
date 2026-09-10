package com.romurbex.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE locationId = :locationId ORDER BY dateAdded ASC")
    fun observeForLocation(locationId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT uri FROM photos WHERE locationId = :locationId ORDER BY dateAdded ASC LIMIT 1")
    fun observeFirstPhotoUri(locationId: Long): Flow<String?>

    @Insert
    suspend fun insert(photo: PhotoEntity): Long

    @Insert
    suspend fun insertAll(photos: List<PhotoEntity>): List<Long>

    @Delete
    suspend fun delete(photo: PhotoEntity)
}
