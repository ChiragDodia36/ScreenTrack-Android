package com.chiragdodia.screentrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.view.Display
import androidx.core.app.NotificationCompat
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private var intervalMs: Long = 5000L
    private var jpegQuality: Int = 65
    private var maxDimension: Int = 720
    private val backendUrl: String = BuildConfig.BACKEND_URL
    private val apiKey: String = BuildConfig.BACKEND_API_KEY
    private var retentionDays: Long = 30L
    private var pauseWhenScreenOff: Boolean = true

    private var lastCaptureAt: Long = 0L
    private var isPaused: Boolean = false
    private var screenReceiver: BroadcastReceiver? = null
    private var lastSuggestionAt: Long = 0L
    private var consecutiveErrors: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)

        intervalMs = (intent?.getLongExtra(EXTRA_INTERVAL_MS, intervalMs) ?: intervalMs).coerceIn(1000L, 60_000L)
        jpegQuality = (intent?.getIntExtra(EXTRA_JPEG_QUALITY, jpegQuality) ?: jpegQuality).coerceIn(10, 100)
        maxDimension = (intent?.getIntExtra(EXTRA_MAX_DIMENSION, maxDimension) ?: maxDimension).coerceIn(240, 2160)
        retentionDays = intent?.getLongExtra(EXTRA_RETENTION_DAYS, retentionDays) ?: retentionDays
        pauseWhenScreenOff = intent?.getBooleanExtra(EXTRA_PAUSE_SCREEN_OFF, pauseWhenScreenOff) ?: pauseWhenScreenOff

        if (data == null || resultCode == 0) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        // Validate URL — blank means backend was not configured in keystore.properties
        if (backendUrl.isBlank()) {
            ServiceStatusStore.getInstance(applicationContext).setLastError("Backend URL not configured")
            stopSelfSafely()
            return START_NOT_STICKY
        }
        try {
            java.net.URL(backendUrl)
        } catch (e: java.net.MalformedURLException) {
            ServiceStatusStore.getInstance(applicationContext).setLastError("Invalid backend URL")
            stopSelfSafely()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        ServiceStatusStore.getInstance(applicationContext).apply {
            setRunning(true)
            setLastError(null)
        }

        FirebaseAnalytics.getInstance(this).logEvent("capture_started", null)
        startProjection(resultCode, data)
        registerScreenReceiver()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        stopProjection()
        ServiceStatusStore.getInstance(applicationContext).setRunning(false)
        FirebaseAnalytics.getInstance(this).logEvent("capture_stopped", null)
        super.onDestroy()
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = projectionManager.getMediaProjection(resultCode, data)

        // Android 14+ requires registering a callback before createVirtualDisplay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelfSafely()
                }
            }, Handler(mainLooper))
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        handlerThread = HandlerThread("ScreenCaptureThread").also { it.start() }
        handler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastCaptureAt < intervalMs) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            if (isPaused) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            lastCaptureAt = now

            serviceScope.launch {
                try {
                    val bitmap = ImageUtils.imageToBitmap(image)
                    image.close()

                    val scaled = ImageUtils.scaleToMaxDimension(bitmap, maxDimension)
                    bitmap.recycle()

                    val outputStream = ByteArrayOutputStream()
                    val safeQuality = jpegQuality.coerceIn(10, 100)
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, safeQuality, outputStream)
                    scaled.recycle()

                    val bytes = outputStream.toByteArray()
                    if (!isNetworkAvailable()) {
                        ServiceStatusStore.getInstance(applicationContext)
                            .setLastError("No network connection")
                        return@launch
                    }

                    val result = FrameUploader.uploadFrame(bytes, backendUrl, apiKey)
                    if (result.analysis != null) {
                        val bundle = android.os.Bundle().apply {
                            putString("emotion", result.analysis.emotion)
                            putString("content_type", result.analysis.contentType)
                        }
                        FirebaseAnalytics.getInstance(this@ScreenCaptureService).logEvent("analysis_received", bundle)
                        val repository = CaptureRepository.getInstance(applicationContext)
                        repository.addResult(
                            CaptureResultEntity(
                                timestampMs = System.currentTimeMillis(),
                                emotion = result.analysis.emotion,
                                emotionScore = result.analysis.emotionConfidence,
                                sentiment = result.analysis.sentimentScore,
                                contentType = result.analysis.contentType,
                                contentConfidence = result.analysis.contentConfidence,
                                textSnippet = result.analysis.textPreview
                            ),
                            retentionDays = retentionDays
                        )
                        ServiceStatusStore.getInstance(applicationContext).apply {
                            setLastError(null)
                            setLastUploadAt(System.currentTimeMillis())
                        }
                        consecutiveErrors = 0
                        maybeNotify(repository)
                    } else if (result.error != null) {
                        consecutiveErrors++
                        ServiceStatusStore.getInstance(applicationContext)
                            .setLastError("${result.error} (errors: $consecutiveErrors)")
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    ServiceStatusStore.getInstance(applicationContext)
                        .setLastError("Capture error: ${e.message} (errors: $consecutiveErrors)")
                }
            }
        }, handler)

        virtualDisplay = projection?.createVirtualDisplay(
            "ScreenTrackCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
    }

    private fun stopProjection() {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        projection?.stop()
        projection = null

        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    private fun stopSelfSafely() {
        stopProjection()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerScreenReceiver() {
        if (!pauseWhenScreenOff || screenReceiver != null) return
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> isPaused = true
                    Intent.ACTION_USER_PRESENT -> isPaused = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            unregisterReceiver(it)
            screenReceiver = null
        }
        isPaused = false
    }

    private fun isNetworkAvailable(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun maybeNotify(repository: CaptureRepository) {
        val now = System.currentTimeMillis()
        if (now - lastSuggestionAt < NOTIFICATION_COOLDOWN_MS) return

        val recent = repository.getSince(now - ONE_HOUR_MS)
        val stats = SuggestionEngine.summarize(recent)
        val suggestion = SuggestionEngine.suggestionFor(stats) ?: return

        NotificationHelper.showWellbeingNotification(
            context = applicationContext,
            title = "ScreenTrack Suggestion",
            message = suggestion
        )
        lastSuggestionAt = now
        ServiceStatusStore.getInstance(applicationContext).setLastNotificationAt(now)
    }

    private fun buildNotification(): Notification {
        val channelId = ensureNotificationChannel()
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("ScreenTrack")
            .setContentText("Capturing screen activity")
            .setSmallIcon(R.drawable.ic_notification)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel(): String {
        val channelId = "screentrack_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.screentrack.mobile.action.STOP"
        private const val NOTIFICATION_COOLDOWN_MS = 15 * 60 * 1000L
        private const val ONE_HOUR_MS = 60 * 60 * 1000L

        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        private const val EXTRA_INTERVAL_MS = "extra_interval_ms"
        private const val EXTRA_JPEG_QUALITY = "extra_jpeg_quality"
        private const val EXTRA_MAX_DIMENSION = "extra_max_dimension"
        private const val EXTRA_RETENTION_DAYS = "extra_retention_days"
        private const val EXTRA_PAUSE_SCREEN_OFF = "extra_pause_screen_off"

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            intervalMs: Long,
            jpegQuality: Int,
            maxDimension: Int,
            retentionDays: Long,
            pauseWhenScreenOff: Boolean
        ) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_PROJECTION_DATA, data)
                putExtra(EXTRA_INTERVAL_MS, intervalMs)
                putExtra(EXTRA_JPEG_QUALITY, jpegQuality)
                putExtra(EXTRA_MAX_DIMENSION, maxDimension)
                putExtra(EXTRA_RETENTION_DAYS, retentionDays)
                putExtra(EXTRA_PAUSE_SCREEN_OFF, pauseWhenScreenOff)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
