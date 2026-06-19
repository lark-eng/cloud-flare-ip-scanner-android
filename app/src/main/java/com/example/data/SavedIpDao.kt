package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedIpDao {
    @Query("SELECT * FROM saved_ips ORDER BY latency ASC")
    fun getAllSavedIps(): Flow<List<SavedIpEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIp(ip: SavedIpEntity)

    @Delete
    suspend fun deleteIp(ip: SavedIpEntity)

    @Query("DELETE FROM saved_ips")
    suspend fun clearAll()
}
