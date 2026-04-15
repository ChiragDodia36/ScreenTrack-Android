package com.chiragdodia.screentrack

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ServiceStatusStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _isRunning = MutableStateFlow(prefs.getBoolean(KEY_RUNNING, false))
    private val _lastError = MutableStateFlow(prefs.getString(KEY_LAST_ERROR, null))
    private val _lastUploadAt = MutableStateFlow(prefs.getLong(KEY_LAST_UPLOAD, 0L).takeIf { it > 0 })
    private val _lastNotificationAt = MutableStateFlow(prefs.getLong(KEY_LAST_NOTIFY, 0L).takeIf { it > 0 })

    val isRunning: StateFlow<Boolean> = _isRunning
    val lastError: StateFlow<String?> = _lastError
    val lastUploadAt: StateFlow<Long?> = _lastUploadAt
    val lastNotificationAt: StateFlow<Long?> = _lastNotificationAt

    fun setRunning(value: Boolean) {
        prefs.edit().putBoolean(KEY_RUNNING, value).apply()
        _isRunning.value = value
    }

    fun setLastError(message: String?) {
        prefs.edit().putString(KEY_LAST_ERROR, message).apply()
        _lastError.value = message
    }

    fun setLastUploadAt(timestampMs: Long?) {
        if (timestampMs == null) {
            prefs.edit().remove(KEY_LAST_UPLOAD).apply()
            _lastUploadAt.value = null
        } else {
            prefs.edit().putLong(KEY_LAST_UPLOAD, timestampMs).apply()
            _lastUploadAt.value = timestampMs
        }
    }

    fun setLastNotificationAt(timestampMs: Long?) {
        if (timestampMs == null) {
            prefs.edit().remove(KEY_LAST_NOTIFY).apply()
            _lastNotificationAt.value = null
        } else {
            prefs.edit().putLong(KEY_LAST_NOTIFY, timestampMs).apply()
            _lastNotificationAt.value = timestampMs
        }
    }

    companion object {
        private const val PREFS_NAME = "screentrack_service"
        private const val KEY_RUNNING = "is_running"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_UPLOAD = "last_upload_at"
        private const val KEY_LAST_NOTIFY = "last_notify_at"

        @Volatile
        private var instance: ServiceStatusStore? = null

        fun getInstance(context: Context): ServiceStatusStore {
            return instance ?: synchronized(this) {
                instance ?: ServiceStatusStore(context).also { instance = it }
            }
        }
    }
}
