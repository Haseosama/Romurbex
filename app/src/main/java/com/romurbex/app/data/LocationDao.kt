package com.romurbex.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query(
        "SELECT sourceFolderName AS name, COUNT(*) AS count FROM locations " +
            "WHERE sourceFolderName != '' GROUP BY sourceFolderName ORDER BY sourceFolderName COLLATE NOCASE",
    )
    fun observeListSummaries(): Flow<List<ListSummary>>

    @Query("SELECT * FROM locations WHERE id = :id")
    fun observeById(id: Long): Flow<LocationEntity?>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getById(id: Long): LocationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(location: LocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(locations: List<LocationEntity>): List<Long>

    @Update
    suspend fun update(location: LocationEntity)

    @Delete
    suspend fun delete(location: LocationEntity)

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM locations WHERE sourceFolderName = :name")
    suspend fun deleteByListName(name: String)
}
