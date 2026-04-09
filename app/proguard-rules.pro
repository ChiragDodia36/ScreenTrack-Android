# ScreenTrack ProGuard Rules

# OkHttp + Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Room Database
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ScreenTrack app classes
-keep class com.screentrack.mobile.CaptureResultEntity { *; }
-keep class com.screentrack.mobile.AnalysisResult { *; }
-keep class com.screentrack.mobile.UploadResult { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# JSON parsing (org.json used in FrameUploader)
-keep class org.json.** { *; }

# Firebase — Crashlytics & Analytics (consumer rules are auto-included,
# but these guards prevent obfuscation from stripping crash metadata)
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Suppress warnings for optional Firebase features
-dontwarn com.google.firebase.**
