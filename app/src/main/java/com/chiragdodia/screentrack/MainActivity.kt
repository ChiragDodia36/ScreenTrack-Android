@file:OptIn(ExperimentalMaterial3Api::class)

package com.chiragdodia.screentrack

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ── Dark Theme Colors ──
private val DarkBackground = Color(0xFF0F1118)
private val DarkSurface = Color(0xFF1A1D27)
private val DarkSurfaceVariant = Color(0xFF242836)
private val AccentTeal = Color(0xFF00D4AA)
private val AccentPurple = Color(0xFF8B5CF6)
private val AccentAmber = Color(0xFFF59E0B)
private val ErrorCoral = Color(0xFFFF6B6B)
private val TextPrimary = Color(0xFFE8ECF4)
private val TextSecondary = Color(0xFF9CA3AF)
private val DarkOutline = Color(0xFF3A3F4B)

private val ScreenTrackColorScheme = darkColorScheme(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    primary = AccentTeal,
    secondary = AccentPurple,
    tertiary = AccentAmber,
    error = ErrorCoral,
    errorContainer = Color(0xFF3B1A1A),
    onErrorContainer = ErrorCoral,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = DarkOutline,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground
)

// ── Navigation ──
private enum class AppPage(val label: String) {
    Home("Home"),
    Trends("Trends"),
    Insights("Insights"),
    Settings("Settings")
}

private data class AnalyticsStats(
    val totalCaptures: Int,
    val dominantEmotion: String,
    val avgSentiment: Double,
    val contentTypes: Int
)

class MainActivity : ComponentActivity() {
    private val viewModel: CaptureViewModel by viewModels {
        CaptureViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("screentrack_prefs", Context.MODE_PRIVATE)

        SurveyReminder.schedule(this)

        setContent {
            val recentResults by viewModel.recentResults.collectAsState()
            val latestResult by viewModel.latestResult.collectAsState()
            val isRunning by viewModel.isRunning.collectAsState()
            val isPaused by viewModel.isPaused.collectAsState()
            val lastError by viewModel.lastError.collectAsState()
            val lastUploadAt by viewModel.lastUploadAt.collectAsState()
            val selectedRange by viewModel.selectedTimeRange.collectAsState()

            val intervalSeconds = 15
            val retentionDays = 30
            var maxDimension by remember { mutableStateOf(prefs.getInt("max_dimension", 720)) }
            var pauseWhenScreenOff by remember { mutableStateOf(prefs.getBoolean("pause_screen_off", true)) }

            var showClearDialog by remember { mutableStateOf(false) }
            var showConsentDialog by remember { mutableStateOf(false) }
            var currentPage by remember { mutableStateOf(AppPage.Home) }

            val coroutineScope = rememberCoroutineScope()

            val dimensionError = validateRange(maxDimension, 240, 2160, "Max dimension")
            val hasErrors = listOf(dimensionError).any { it != null }

            val captureLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    savePrefs(prefs, intervalSeconds, maxDimension, retentionDays, pauseWhenScreenOff)
                    ScreenCaptureService.start(
                        context = this,
                        resultCode = result.resultCode,
                        data = result.data!!,
                        intervalMs = intervalSeconds.coerceIn(1, 60) * 1000L,
                        maxDimension = maxDimension.coerceIn(240, 2160),
                        retentionDays = retentionDays.coerceIn(1, 90).toLong(),
                        pauseWhenScreenOff = pauseWhenScreenOff
                    )
                }
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            val exportHoursState = remember { mutableStateOf(1) }
            val exportLabelState = remember { mutableStateOf("1 Hour") }

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/pdf")
            ) { uri ->
                if (uri != null) {
                    coroutineScope.launch {
                        val hours = exportHoursState.value
                        val label = exportLabelState.value
                        val data = viewModel.loadRange(hours)
                        val pdfBytes = PdfExporter.buildSummaryPdf("Last $label", data)
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(uri)?.use { output ->
                                output.write(pdfBytes)
                            }
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            }

            val mainHaptic = LocalHapticFeedback.current

            val filteredResults = remember(recentResults, selectedRange) {
                val since = selectedRange.sinceMs()
                recentResults.filter { it.timestampMs >= since }
            }

            MaterialTheme(colorScheme = ScreenTrackColorScheme) {
                Scaffold(
                    containerColor = DarkBackground,
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        "MOODITER",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp
                                        ),
                                        color = TextPrimary
                                    )
                                    Text(
                                        if (isRunning) "Capturing..." else "Idle",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isRunning) AccentTeal else TextSecondary
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = DarkSurface,
                                titleContentColor = TextPrimary
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = DarkSurface,
                            contentColor = TextPrimary,
                            tonalElevation = 0.dp
                        ) {
                            AppPage.values().forEach { page ->
                                val selected = currentPage == page
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { mainHaptic.performHapticFeedback(HapticFeedbackType.LongPress); currentPage = page },
                                    icon = {
                                        Icon(
                                            imageVector = getNavIcon(page, selected),
                                            contentDescription = page.label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            page.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentTeal,
                                        selectedTextColor = AccentTeal,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = AccentTeal.copy(alpha = 0.12f)
                                    )
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Crossfade(targetState = currentPage, label = "page_transition") { page ->
                        when (page) {
                            AppPage.Home -> HomeScreen(
                                paddingValues = paddingValues,
                                lastError = lastError,
                                onClearError = { viewModel.clearError() },
                                isRunning = isRunning,
                                latestResult = latestResult,
                                lastUploadAt = lastUploadAt,
                                onStartCapture = {
                                    if (!prefs.getBoolean("consent_given", false)) {
                                        showConsentDialog = true
                                    } else {
                                        val projectionManager = getSystemService<MediaProjectionManager>()
                                        projectionManager?.let { captureLauncher.launch(it.createScreenCaptureIntent()) }
                                    }
                                },
                                onStopCapture = { ScreenCaptureService.stop(this@MainActivity) },
                                canStartCapture = !isRunning && !hasErrors,
                                canStopCapture = isRunning,
                                isPaused = isPaused,
                                onPauseToggle = {
                                    if (isPaused) ScreenCaptureService.resume(this@MainActivity)
                                    else ScreenCaptureService.pause(this@MainActivity)
                                },
                                filteredResults = filteredResults,
                                selectedRange = selectedRange,
                                onViewTrends = { currentPage = AppPage.Trends }
                            )
                            AppPage.Trends -> TrendsScreen(
                                paddingValues = paddingValues,
                                lastError = lastError,
                                onClearError = { viewModel.clearError() },
                                selectedRange = selectedRange,
                                onRangeSelected = { viewModel.setTimeRange(it) },
                                filteredResults = filteredResults,
                                allResults = recentResults
                            )
                            AppPage.Insights -> InsightsScreen(
                                paddingValues = paddingValues,
                                filteredResults = filteredResults,
                                selectedRange = selectedRange,
                                allResults = recentResults
                            )
                            AppPage.Settings -> SettingsScreen(
                                paddingValues = paddingValues,
                                maxDimension = maxDimension,
                                onMaxDimensionChange = { maxDimension = it },
                                dimensionError = dimensionError,
                                pauseWhenScreenOff = pauseWhenScreenOff,
                                onPauseWhenScreenOffChange = { pauseWhenScreenOff = it },
                                onExportPdf = { hours ->
                                    exportHoursState.value = hours
                                    exportLabelState.value = when (hours) {
                                        1 -> "1 Hour"
                                        24 -> "24 Hours"
                                        168 -> "7 Days"
                                        else -> "14 Days"
                                    }
                                    exportLauncher.launch("screentrack-report.pdf")
                                },
                                onOpenSurvey = {
                                    startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(SurveyReminder.FORM_URL)
                                        )
                                    )
                                },
                                onTestReminder = { SurveyReminder.showNow(this@MainActivity) },
                                onClearData = { showClearDialog = true }
                            )
                        }
                    }
                }
            }

            if (showConsentDialog) {
                AlertDialog(
                    onDismissRequest = { showConsentDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            prefs.edit().putBoolean("consent_given", true).apply()
                            showConsentDialog = false
                            val projectionManager = getSystemService<MediaProjectionManager>()
                            projectionManager?.let { captureLauncher.launch(it.createScreenCaptureIntent()) }
                        }) { Text("I Understand, Continue", color = AccentTeal) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConsentDialog = false }) { Text("Cancel") }
                    },
                    title = { Text("Screen Recording Notice") },
                    text = {
                        Text(
                            "• ScreenTrack captures your screen to analyze your emotional wellbeing\n\n" +
                            "• All analysis happens entirely on your device — nothing is sent to any server\n\n" +
                            "• No screenshots are stored, only the result (emotion, sentiment score)\n\n" +
                            "• You can stop recording at any time from the Home screen",
                            color = TextSecondary
                        )
                    },
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    textContentColor = TextSecondary
                )
            }

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showClearDialog = false
                            viewModel.clearAll()
                        }) { Text("Clear", color = ErrorCoral) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
                    },
                    title = { Text("Clear all data?") },
                    text = { Text("This deletes all stored results from the device.") },
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    textContentColor = TextSecondary
                )
            }
        }
    }

    private fun savePrefs(
        prefs: android.content.SharedPreferences,
        intervalSeconds: Int,
        maxDimension: Int,
        retentionDays: Int,
        pauseWhenScreenOff: Boolean
    ) {
        prefs.edit()
            .putInt("interval_seconds", intervalSeconds)
            .putInt("max_dimension", maxDimension)
            .putInt("retention_days", retentionDays)
            .putBoolean("pause_screen_off", pauseWhenScreenOff)
            .apply()
    }
}

// ── Navigation Icons ──
@Composable
private fun getNavIcon(page: AppPage, selected: Boolean): ImageVector {
    return when (page) {
        AppPage.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        AppPage.Trends -> Icons.Filled.Star
        AppPage.Insights -> Icons.Filled.Info
        AppPage.Settings -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    }
}

// ── Shared Components ──

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ErrorCoral.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Upload Error", style = MaterialTheme.typography.titleSmall, color = ErrorCoral, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            TextButton(onClick = onDismiss) { Text("Dismiss", color = AccentTeal) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusPill(isRunning: Boolean) {
    val bgColor = if (isRunning) AccentTeal.copy(alpha = 0.15f) else DarkOutline.copy(alpha = 0.3f)
    val textColor = if (isRunning) AccentTeal else TextSecondary
    val text = if (isRunning) "Running" else "Stopped"
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isRunning) {
                PulsingDot(color = AccentTeal)
            } else {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(TextSecondary))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = textColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = TextSecondary.copy(alpha = 0.5f)
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun TimeRangeChips(selected: TimeRangeOption, onSelected: (TimeRangeOption) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeRangeOption.values().forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) AccentTeal else DarkSurfaceVariant,
                modifier = Modifier.clickable { onSelected(option) }
            ) {
                Text(
                    text = option.label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) DarkBackground else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

// ── HOME PAGE ──

@Composable
private fun HomeScreen(
    paddingValues: PaddingValues,
    lastError: String?,
    onClearError: () -> Unit,
    isRunning: Boolean,
    latestResult: CaptureResultEntity?,
    lastUploadAt: Long?,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    canStartCapture: Boolean,
    canStopCapture: Boolean,
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    filteredResults: List<CaptureResultEntity>,
    selectedRange: TimeRangeOption,
    onViewTrends: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (lastError != null) {
            item { ErrorBanner(message = lastError, onDismiss = onClearError) }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(AccentTeal, AccentPurple)),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        StatusPill(isRunning)
                    }

                    lastUploadAt?.let {
                        Text("Last upload: ${formatRelativeTime(it)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }

                    if (latestResult != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val glowColor = emotionColor(latestResult.emotion)
                                val infiniteTransition = rememberInfiniteTransition(label = "mood_glow")
                                val glowAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 0.08f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1500, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "glow_alpha"
                                )
                                Text(
                                    latestResult.emotion.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = glowColor,
                                    modifier = Modifier.drawBehind {
                                        drawCircle(color = glowColor.copy(alpha = glowAlpha), radius = size.maxDimension * 0.7f)
                                    }
                                )
                                Text("Current Emotion", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val sentimentColor = when {
                                    latestResult.sentiment > 0.2 -> EmotionJoy
                                    latestResult.sentiment < -0.2 -> EmotionAnger
                                    else -> TextSecondary
                                }
                                Text(
                                    formatScore(latestResult.sentiment),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = sentimentColor
                                )
                                Text("Sentiment", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SummaryRow("Content", "${latestResult.contentType} (${formatScore(latestResult.contentConfidence)})")
                            }
                        }
                    } else {
                        EmptyState(
                            icon = Icons.Filled.PlayArrow,
                            title = "No captures yet",
                            subtitle = "Start tracking to see your results here"
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onStartCapture() },
                    enabled = canStartCapture,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentTeal,
                        contentColor = DarkBackground,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = TextSecondary
                    )
                ) {
                    Text("Start Capture", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onStopCapture() },
                    enabled = canStopCapture,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorCoral)
                ) {
                    Text("Stop", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isRunning) {
            item {
                OutlinedButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onPauseToggle() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal)
                ) {
                    Text(if (isPaused) "Resume Collection" else "Pause Collection", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Quick Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        TextButton(onClick = onViewTrends) { Text("View Trends", color = AccentTeal, style = MaterialTheme.typography.labelMedium) }
                    }
                    if (filteredResults.isEmpty()) {
                        Text("No captures in ${selectedRange.label.lowercase()}.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    } else {
                        MiniSparkline(items = filteredResults)
                        Spacer(Modifier.height(4.dp))
                        SummaryRow("Captures", filteredResults.size.toString())
                        val avgSent = filteredResults.map { it.sentiment }.average()
                        SummaryRow("Avg Sentiment", formatScore(avgSent))
                        val topEmo = filteredResults.groupingBy { it.emotion }.eachCount().maxByOrNull { it.value }
                        if (topEmo != null) {
                            SummaryRow("Top Emotion", "${topEmo.key} (${topEmo.value})")
                        }
                    }
                }
            }
        }
    }
}

// ── TRENDS PAGE ──

@Composable
private fun TrendsScreen(
    paddingValues: PaddingValues,
    lastError: String?,
    onClearError: () -> Unit,
    selectedRange: TimeRangeOption,
    onRangeSelected: (TimeRangeOption) -> Unit,
    filteredResults: List<CaptureResultEntity>,
    allResults: List<CaptureResultEntity>
) {
    val analyticsStats = remember(filteredResults) { buildAnalyticsStats(filteredResults) }
    val emotionSlices = remember(filteredResults) { buildEmotionSlices(filteredResults) }
    val contentSlices = remember(filteredResults) { buildContentSlices(filteredResults) }
    val comparison = remember(filteredResults, allResults, selectedRange) {
        buildPeriodComparison(filteredResults, allResults, selectedRange)
    }

    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (lastError != null) {
            item { ErrorBanner(message = lastError, onDismiss = onClearError) }
        }

        item { TimeRangeChips(selected = selectedRange, onSelected = onRangeSelected) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTileWithDelta("Captures", analyticsStats.totalCaptures.toString(), AccentTeal, comparison.capturesDelta, Modifier.weight(1f))
                StatTileWithDelta("Emotion", analyticsStats.dominantEmotion.ifBlank { "-" }, AccentPurple, null, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTileWithDelta("Sentiment", formatScore(analyticsStats.avgSentiment), AccentAmber, comparison.sentimentDelta, Modifier.weight(1f))
                StatTileWithDelta("Types", analyticsStats.contentTypes.toString(), EmotionSadness, comparison.typesDelta, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DonutChart("Emotions", emotionSlices)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DonutChart("Content", contentSlices)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SentimentLineChart(filteredResults)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmotionBarChart(filteredResults)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ContentBarChart(filteredResults)
                }
            }
        }
    }
}

private data class PeriodComparison(
    val capturesDelta: String?,
    val sentimentDelta: String?,
    val typesDelta: String?
)

private fun buildPeriodComparison(
    current: List<CaptureResultEntity>,
    allResults: List<CaptureResultEntity>,
    range: TimeRangeOption
): PeriodComparison {
    val periodMs = java.util.concurrent.TimeUnit.HOURS.toMillis(range.hours.toLong())
    val now = System.currentTimeMillis()
    val prevStart = now - periodMs * 2
    val prevEnd = now - periodMs
    val previous = allResults.filter { it.timestampMs in prevStart until prevEnd }
    if (previous.isEmpty() || current.isEmpty()) return PeriodComparison(null, null, null)

    val capDiff = current.size - previous.size
    val capDelta = if (capDiff > 0) "+$capDiff" else if (capDiff < 0) "$capDiff" else null

    val currSent = current.map { it.sentiment }.average()
    val prevSent = previous.map { it.sentiment }.average()
    val sentDiff = currSent - prevSent
    val sentDelta = when {
        sentDiff > 0.05 -> "+${String.format("%.2f", sentDiff)}"
        sentDiff < -0.05 -> String.format("%.2f", sentDiff)
        else -> null
    }

    val currTypes = current.map { it.contentType }.distinct().count()
    val prevTypes = previous.map { it.contentType }.distinct().count()
    val typeDiff = currTypes - prevTypes
    val typesDelta = if (typeDiff > 0) "+$typeDiff" else if (typeDiff < 0) "$typeDiff" else null

    return PeriodComparison(capDelta, sentDelta, typesDelta)
}

@Composable
private fun StatTileWithDelta(label: String, value: String, accentColor: Color, delta: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (delta != null) {
                    val deltaColor = if (delta.startsWith("+")) EmotionJoy else EmotionAnger
                    Text(delta, style = MaterialTheme.typography.labelSmall, color = deltaColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── INSIGHTS PAGE ──

@Composable
private fun InsightsScreen(
    paddingValues: PaddingValues,
    filteredResults: List<CaptureResultEntity>,
    selectedRange: TimeRangeOption,
    allResults: List<CaptureResultEntity>
) {
    val stats = remember(filteredResults) { SuggestionEngine.summarize(filteredResults) }
    val suggestion = remember(stats) { SuggestionEngine.suggestionFor(stats) }
    val summaries = remember(filteredResults) { buildRecentSummaries(filteredResults) }
    val comparison = remember(filteredResults, allResults, selectedRange) {
        buildPeriodComparison(filteredResults, allResults, selectedRange)
    }

    val wellnessScore = remember(stats) {
        if (filteredResults.isEmpty()) 50
        else ((stats.avgSentiment + 1.0) / 2.0 * 100).toInt().coerceIn(0, 100)
    }
    val wellnessColor = when {
        wellnessScore >= 65 -> EmotionJoy
        wellnessScore >= 35 -> AccentAmber
        else -> EmotionAnger
    }
    val wellnessLabel = when {
        wellnessScore >= 65 -> "Positive Mood"
        wellnessScore >= 35 -> "Moderate"
        else -> "Consider a Break"
    }

    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Wellness Score", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(120.dp)) {
                            drawArc(
                                color = DarkSurfaceVariant,
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = 12f, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = wellnessColor,
                                startAngle = 135f,
                                sweepAngle = 270f * (wellnessScore / 100f),
                                useCenter = false,
                                style = Stroke(width = 12f, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                wellnessScore.toString(),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = wellnessColor
                            )
                        }
                    }
                    Text(wellnessLabel, style = MaterialTheme.typography.titleSmall, color = wellnessColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Screen Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    SummaryRow("Total Captures", filteredResults.size.toString())
                    SummaryRow("Time Range", selectedRange.label)
                    val duration = calculateDurationMinutes(filteredResults)
                    if (duration != null) {
                        val durationText = if (duration >= 60) "${duration / 60}h ${duration % 60}m" else "${duration}m"
                        SummaryRow("Active Duration", durationText)
                    }
                }
            }
        }

        if (suggestion != null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                        .border(1.dp, AccentPurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Suggestion", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AccentPurple)
                        Text(suggestion, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }
            }
        }

        if (filteredResults.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Emotion Breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        val emotionCounts = filteredResults.groupingBy { it.emotion }.eachCount().toList().sortedByDescending { it.second }
                        val total = filteredResults.size.toFloat()
                        emotionCounts.forEach { (emotion, count) ->
                            val pct = (count / total * 100).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(emotionColor(emotion)))
                                Text(
                                    emotion.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.width(80.dp)
                                )
                                Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(DarkSurfaceVariant)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = (count / total).coerceIn(0f, 1f))
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(emotionColor(emotion))
                                    )
                                }
                                Text("$pct%", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }

        if (filteredResults.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Top Content", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        val contentCounts = filteredResults.groupingBy { it.contentType }.eachCount().toList().sortedByDescending { it.second }
                        val total = filteredResults.size.toFloat()
                        contentCounts.take(3).forEachIndexed { index, (ct, count) ->
                            val pct = (count / total * 100).toInt()
                            val barColor = ContentPalette[index % ContentPalette.size]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(barColor))
                                Text(ct, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.width(80.dp))
                                Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(DarkSurfaceVariant)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = (count / total).coerceIn(0f, 1f))
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(barColor)
                                    )
                                }
                                Text("$pct%", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }

        if (filteredResults.isNotEmpty()) {
            item {
                val hourCounts = remember(filteredResults) {
                    val cal = java.util.Calendar.getInstance()
                    val counts = IntArray(24)
                    filteredResults.forEach { r ->
                        cal.timeInMillis = r.timestampMs
                        counts[cal.get(java.util.Calendar.HOUR_OF_DAY)]++
                    }
                    counts
                }
                val peakHour = remember(hourCounts) { hourCounts.indices.maxByOrNull { hourCounts[it] } ?: 0 }
                val maxCount = hourCounts.max().coerceAtLeast(1)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Peak Usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        val peakAmPm = if (peakHour < 12) "${if (peakHour == 0) 12 else peakHour}:00 AM" else "${if (peakHour == 12) 12 else peakHour - 12}:00 PM"
                        Text("Most active at $peakAmPm", style = MaterialTheme.typography.bodySmall, color = AccentTeal, fontWeight = FontWeight.SemiBold)
                        Canvas(modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            val barW = size.width / 24f
                            for (h in 0 until 24) {
                                val barHeight = (hourCounts[h].toFloat() / maxCount) * size.height
                                val barColor = if (h == peakHour) AccentTeal else AccentTeal.copy(alpha = 0.3f)
                                drawRect(
                                    color = barColor,
                                    topLeft = Offset(h * barW + 1f, size.height - barHeight),
                                    size = androidx.compose.ui.geometry.Size(barW - 2f, barHeight)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (comparison.capturesDelta != null || comparison.sentimentDelta != null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Period Comparison", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("This ${selectedRange.label} vs previous", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        if (comparison.capturesDelta != null) {
                            SummaryRow("Captures change", comparison.capturesDelta)
                        }
                        if (comparison.sentimentDelta != null) {
                            SummaryRow("Sentiment change", comparison.sentimentDelta)
                        }
                        if (comparison.typesDelta != null) {
                            SummaryRow("Content types change", comparison.typesDelta)
                        }
                    }
                }
            }
        }

        item { SectionHeader("Recent Summaries") }

        items(summaries) { summary ->
            RecentSummaryCard(summary)
        }
    }
}

// ── SETTINGS PAGE ──

@Composable
private fun SettingsScreen(
    paddingValues: PaddingValues,
    maxDimension: Int,
    onMaxDimensionChange: (Int) -> Unit,
    dimensionError: String?,
    pauseWhenScreenOff: Boolean,
    onPauseWhenScreenOffChange: (Boolean) -> Unit,
    onExportPdf: (Int) -> Unit,
    onOpenSurvey: () -> Unit,
    onTestReminder: () -> Unit,
    onClearData: () -> Unit
) {
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentTeal,
        unfocusedBorderColor = DarkOutline,
        focusedLabelColor = AccentTeal,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentTeal,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        errorBorderColor = ErrorCoral,
        errorLabelColor = ErrorCoral
    )

    LazyColumn(
        modifier = Modifier.padding(paddingValues).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Capture Settings")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Capture Interval", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Surface(shape = RoundedCornerShape(8.dp), color = DarkSurfaceVariant) {
                            Text(
                                "15 seconds",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = AccentTeal,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    OutlinedTextField(
                        value = maxDimension.toString(),
                        onValueChange = { onMaxDimensionChange(it.toIntOrNull() ?: maxDimension) },
                        label = { Text("Max Dimension (px)") },
                        singleLine = true,
                        isError = dimensionError != null,
                        supportingText = { dimensionError?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Data Management")
                    Text(
                        "Export PDF Report",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onExportPdf(1) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal)
                        ) { Text("1 Hour", style = MaterialTheme.typography.labelSmall) }
                        OutlinedButton(
                            onClick = { onExportPdf(24) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal)
                        ) { Text("24 Hours", style = MaterialTheme.typography.labelSmall) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onExportPdf(168) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal)
                        ) { Text("7 Days", style = MaterialTheme.typography.labelSmall) }
                        OutlinedButton(
                            onClick = { onExportPdf(336) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal)
                        ) { Text("14 Days", style = MaterialTheme.typography.labelSmall) }
                    }
                    Button(
                        onClick = onOpenSurvey,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                    ) {
                        Text("Fill Out Survey")
                    }
                    OutlinedButton(
                        onClick = onTestReminder,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal)
                    ) {
                        Text("Send Test Survey Reminder")
                    }
                    OutlinedButton(
                        onClick = onClearData,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorCoral)
                    ) {
                        Text("Clear Data")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Behavior")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pause when screen off", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Switch(
                            checked = pauseWhenScreenOff,
                            onCheckedChange = onPauseWhenScreenOffChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentTeal,
                                checkedTrackColor = AccentTeal.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = DarkSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("About")
                    Text("ScreenTrack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AccentTeal)
                    Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(
                        "AI-powered screen content tracker for digital wellbeing. Captures your screen activity, analyzes content and emotions, and provides actionable wellness insights.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

// ── Helper Data & Functions ──

private data class RecentSummary(
    val label: String,
    val totalCaptures: Int,
    val topEmotion: String?,
    val topEmotionShare: Double,
    val topContent: String?,
    val topContentShare: Double,
    val secondContent: String?,
    val secondContentShare: Double,
    val avgSentiment: Double?,
    val durationMinutes: Int?
)

@Composable
private fun RecentSummaryCard(summary: RecentSummary) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            val accentColor = when {
                summary.topEmotion != null -> emotionColor(summary.topEmotion)
                else -> DarkOutline
            }
            Box(
                modifier = Modifier.width(4.dp).height(90.dp)
                    .background(accentColor, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
            )
            Column(modifier = Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(summary.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                if (summary.totalCaptures == 0) {
                    Text("No captures in this window.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    return@Column
                }
                val contentLine = buildString {
                    append("Mostly ${summary.topContent ?: "unknown"}")
                    if (summary.topContentShare > 0.0) append(" (${formatPercent(summary.topContentShare)})")
                    if (summary.secondContent != null && summary.secondContentShare > 0.0) {
                        append(", ${summary.secondContent} (${formatPercent(summary.secondContentShare)})")
                    }
                }
                Text(contentLine, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                val emotionLine = buildString {
                    append("Top: ${summary.topEmotion ?: "unknown"}")
                    if (summary.topEmotionShare > 0.0) append(" (${formatPercent(summary.topEmotionShare)})")
                }
                Text(emotionLine, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                summary.avgSentiment?.let { Text("Sentiment: ${formatScore(it)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
            }
        }
    }
}

private fun buildAnalyticsStats(items: List<CaptureResultEntity>): AnalyticsStats {
    if (items.isEmpty()) return AnalyticsStats(0, "-", 0.0, 0)
    val dominantEmotion = items.groupingBy { it.emotion }.eachCount().maxByOrNull { it.value }?.key ?: "-"
    val avgSentiment = items.map { it.sentiment }.average()
    val contentTypes = items.map { it.contentType }.distinct().count()
    return AnalyticsStats(items.size, dominantEmotion, avgSentiment, contentTypes)
}

private fun buildRecentSummaries(items: List<CaptureResultEntity>): List<RecentSummary> {
    val now = System.currentTimeMillis()
    val windows = listOf(
        "Last 1 hour" to 1L * 60 * 60 * 1000,
        "Last 5 hours" to 5L * 60 * 60 * 1000,
        "Last 24 hours" to 24L * 60 * 60 * 1000,
        "Last 7 days" to 7L * 24 * 60 * 60 * 1000
    )
    return windows.map { (label, windowMs) ->
        val cutoff = now - windowMs
        val windowItems = items.filter { it.timestampMs >= cutoff }
        summarizeWindow(label, windowItems)
    }
}

private fun summarizeWindow(label: String, items: List<CaptureResultEntity>): RecentSummary {
    if (items.isEmpty()) return RecentSummary(label, 0, null, 0.0, null, 0.0, null, 0.0, null, null)
    val total = items.size.toDouble()
    val emotionCounts = items.groupingBy { it.emotion }.eachCount().toList().sortedByDescending { it.second }
    val contentCounts = items.groupingBy { it.contentType }.eachCount().toList().sortedByDescending { it.second }
    val topEmotion = emotionCounts.firstOrNull()
    val topContent = contentCounts.firstOrNull()
    val secondContent = contentCounts.getOrNull(1)
    return RecentSummary(
        label = label,
        totalCaptures = items.size,
        topEmotion = topEmotion?.first,
        topEmotionShare = (topEmotion?.second ?: 0) / total,
        topContent = topContent?.first,
        topContentShare = (topContent?.second ?: 0) / total,
        secondContent = secondContent?.first,
        secondContentShare = (secondContent?.second ?: 0) / total,
        avgSentiment = items.map { it.sentiment }.average(),
        durationMinutes = calculateDurationMinutes(items)
    )
}

private fun calculateDurationMinutes(items: List<CaptureResultEntity>): Int? {
    if (items.isEmpty()) return null
    val minTs = items.minOf { it.timestampMs }
    val maxTs = items.maxOf { it.timestampMs }
    return ((maxTs - minTs) / 60000.0).toInt().coerceAtLeast(0)
}

private fun validateRange(value: Int, min: Int, max: Int, label: String): String? {
    if (value < min || value > max) return "$label must be between $min and $max"
    return null
}

private fun formatScore(score: Double): String = String.format("%.2f", score)

private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val deltaSec = ((now - timestampMs) / 1000).coerceAtLeast(0)
    return when {
        deltaSec < 10 -> "just now"
        deltaSec < 60 -> "${deltaSec}s ago"
        deltaSec < 3600 -> "${deltaSec / 60}m ago"
        deltaSec < 86400 -> "${deltaSec / 3600}h ago"
        else -> "${deltaSec / 86400}d ago"
    }
}

private fun formatPercent(value: Double): String = "${(value * 100).toInt()}%"

