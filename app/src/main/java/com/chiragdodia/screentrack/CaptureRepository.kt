package com.chiragdodia.screentrack

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class CaptureRepository(private val dao: CaptureDao) {
    fun observeLatest(): Flow<CaptureResultEntity?> = dao.observeLatest()

    fun observeRecentSince(sinceMs: Long, limit: Int): Flow<List<CaptureResultEntity>> {
        val since = maxOf(0, sinceMs)
        return dao.observeRecent(since, limit)
    }

    suspend fun getSince(sinceMs: Long): List<CaptureResultEntity> {
        return dao.getSince(maxOf(0, sinceMs))
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    suspend fun addResult(result: CaptureResultEntity, retentionDays: Long) {
        dao.insert(result)
        dao.deleteOlderThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays))
    }

    companion object {
        @Volatile
        private var instance: CaptureRepository? = null

        fun getInstance(context: Context): CaptureRepository {
            return instance ?: synchronized(this) {
                instance ?: CaptureRepository(AppDatabase.getInstance(context).captureDao())
                    .also { instance = it }
            }
        }
    }
}
