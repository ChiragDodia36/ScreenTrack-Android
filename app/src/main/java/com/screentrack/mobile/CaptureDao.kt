package com.screentrack.mobile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: CaptureResultEntity)

    @Query("SELECT * FROM capture_results ORDER BY timestampMs DESC LIMIT 1")
    fun observeLatest(): Flow<CaptureResultEntity?>

    @Query(
        "SELECT * FROM capture_results WHERE timestampMs >= :since ORDER BY timestampMs DESC LIMIT :limit"
    )
    fun observeRecent(since: Long, limit: Int): Flow<List<CaptureResultEntity>>

    @Query("SELECT * FROM capture_results WHERE timestampMs >= :since ORDER BY timestampMs DESC")
    suspend fun getSince(since: Long): List<CaptureResultEntity>

    @Query("DELETE FROM capture_results")
    suspend fun deleteAll()

    @Query("DELETE FROM capture_results WHERE timestampMs < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
