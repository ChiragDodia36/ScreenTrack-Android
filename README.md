## ScreenTrack Android (Kotlin)

This is the Android client for real-time screen capture and AI-powered content analysis. It captures the screen, streams frames to the backend for emotion/sentiment/content analysis, and provides comprehensive local analytics with a modern Material 3 UI.

### Features

#### 📱 5-Page Navigation
- **Home**: Live capture status, latest emotion, quick sentiment summary with mini sparkline
- **Trends**: Time-filtered analytics with interactive charts, period comparisons, and granular data views
- **History**: Chronological capture list with emotion/content filters, expandable cards, and date grouping
- **Insights**: Wellness score, screen time stats, peak usage hours, suggestions, and trend comparisons
- **Settings**: Backend configuration, capture settings, data management, and app info

#### 📊 Rich Analytics
- **Interactive Sentiment Chart**: Dynamic granularity (All, 15m, 30m, 1h, 3h, 6h) with trend indicators
- **Emotion & Content Distribution**: Bar charts and donut charts with custom color palettes
- **Period Comparisons**: Track changes vs previous equal-length period with delta indicators
- **Peak Usage Analysis**: 24-hour activity heatmap showing most active times
- **Mini Sparkline**: Compact sentiment trend visualization (last 20 captures)

#### 🎨 Modern UI/UX
- Material 3 design with custom dark theme
- Animated mood ring glow effect on emotions
- Expandable history cards with detailed metrics
- Horizontally scrollable filter chips with haptic feedback
- Empty states with icons and helpful messages
- Gradient-bordered status cards
- Pulsing status indicators

#### ⚙️ Robust Capture Engine
- Foreground service using `MediaProjection` API
- Configurable capture interval (1-60 seconds)
- JPEG quality control (10-100)
- Max dimension settings (240-2160px)
- Pause when screen off option
- Automatic retry with exponential backoff
- Connection pooling and keep-alive

#### 💾 Local Storage
- Room database for capture results
- Configurable retention (1-90 days)
- Time-range filtering (1h, 6h, 24h, 7 days, 30 days)
- PDF export with charts and summaries
- Clear data functionality

#### 🔔 Smart Notifications
- Wellness suggestions based on sentiment trends
- 15-minute cooldown between notifications
- Rule-based triggers for breaks and positive reinforcement

### Setup

#### Open in Android Studio
1. Open `mobile-android/` as a project
2. Let Gradle sync
3. Run on an emulator or device (API 26+)

#### Backend Configuration
- **Emulator**: `http://10.0.2.2:8000`
- **Physical Device**: `http://<your-lan-ip>:8000`
- Test connection via Settings page before starting capture

### Architecture

```
MainActivity (Compose UI)
    ├── Home Screen
    ├── Trends Screen
    ├── History Screen
    ├── Insights Screen
    └── Settings Screen

ScreenCaptureService (Foreground)
    └── MediaProjection + ImageReader
        └── FrameUploader (OkHttp)
            └── Backend API

CaptureViewModel
    └── CaptureRepository
        └── Room Database (CaptureResultEntity)

ChartComponents
    ├── SentimentLineChart (with granularity)
    ├── EmotionBarChart
    ├── ContentBarChart
    ├── MiniSparkline
    └── DonutChart

SuggestionEngine
    └── NotificationHelper
```

### Key Files
- `MainActivity.kt` - Main UI with 5-page navigation and all screens
- `ScreenCaptureService.kt` - Capture service with MediaProjection
- `FrameUploader.kt` - Network client with retry logic and keep-alive
- `ChartComponents.kt` - All chart visualizations with animations
- `CaptureViewModel.kt` - State management and data flow
- `SuggestionEngine.kt` - Wellness suggestion rules
- `PdfExporter.kt` - PDF report generation
- `TimeRangeOption.kt` - Time range filter options

### Dependencies
- Jetpack Compose + Material 3
- Room Database
- OkHttp for networking
- Kotlin Coroutines + Flow
- iText for PDF generation

### Recent Updates (2026-02-16)
- Complete UI redesign with 5-page bottom navigation
- Interactive sentiment chart with dynamic granularity
- Period-over-period comparison analytics
- Peak usage hour visualization
- Expandable history cards with filters
- Haptic feedback throughout the app
- Enhanced empty states and animations
- Wellness score with radial arc visualization

### Notes
- Requires `FOREGROUND_SERVICE` and `POST_NOTIFICATIONS` permissions
- MediaProjection requires user consent on first use
- Backend must be running for capture to work
- Local data persists across app restarts
- PDF exports saved to Downloads folder
