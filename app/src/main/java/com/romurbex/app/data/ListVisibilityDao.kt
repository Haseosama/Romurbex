package com.romurbex.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListVisibilityDao {
    @Query("SELECT * FROM list_visibility")
    fun observeAll(): Flow<List<ListVisibilityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setVisibility(entity: ListVisibilityEntity)

    @Query("DELETE FROM list_visibility WHERE name = :name")
    suspend fun clearVisibility(name: String)
}
