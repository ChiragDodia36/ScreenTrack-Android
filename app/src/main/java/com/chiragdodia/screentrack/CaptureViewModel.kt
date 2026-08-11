package com.chiragdodia.screentrack

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CaptureViewModel(
    private val repository: CaptureRepository,
    private val statusStore: ServiceStatusStore,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val selectedRange = MutableStateFlow(TimeRangeOption.LAST_24_HOURS)

    // Live "current emotion" is smoothed over the last few captures (display-only,
    // neutral-suppressed) so it doesn't flicker to neutral between emotional frames.
    val latestResult: StateFlow<CaptureResultEntity?> = repository.observeRecentSince(0, limit = 6)
        .map { EmotionSmoother.smooth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Fixed 30-day window feeding the mood chart's own Day/Week/Month tabs,
    // independent of the page's range chips.
    val moodResults: StateFlow<List<CaptureResultEntity>> =
        repository.observeRecentSince(
            System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30),
            limit = 20000
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentResults: StateFlow<List<CaptureResultEntity>> = selectedRange
        .flatMapLatest { range ->
            repository.observeRecentSince(range.sinceMs(), limit = 500)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRunning: StateFlow<Boolean> = statusStore.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isPaused: StateFlow<Boolean> = statusStore.isPaused
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastError: StateFlow<String?> = statusStore.lastError
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastUploadAt: StateFlow<Long?> = statusStore.lastUploadAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedTimeRange: StateFlow<TimeRangeOption> = selectedRange
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimeRangeOption.LAST_24_HOURS)

    fun setTimeRange(option: TimeRangeOption) {
        selectedRange.value = option
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun clearError() {
        statusStore.setLastError(null)
    }

    suspend fun loadRange(hours: Int): List<CaptureResultEntity> {
        val since = System.currentTimeMillis() - java.util.concurrent.TimeUnit.HOURS.toMillis(hours.toLong())
        return repository.getSince(since)
    }
}

class CaptureViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CaptureViewModel::class.java)) {
            val repository = CaptureRepository.getInstance(context)
            val statusStore = ServiceStatusStore.getInstance(context)
            val prefs = context.getSharedPreferences("screentrack_prefs", Context.MODE_PRIVATE)
            @Suppress("UNCHECKED_CAST")
            return CaptureViewModel(repository, statusStore, prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
