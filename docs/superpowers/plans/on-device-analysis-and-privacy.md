# On-Device Analysis & Privacy Hardening — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the keyword-only on-device analysis with a tested text-sentiment + facial-expression pipeline, harden privacy so nothing leaves the device, and add call/manual pause — on a base that is unit-testable and ready to scale.

**Architecture:** Separate **pure decision logic** (sentiment, content, face interpretation, signal combination, pause state) from **impure I/O** (ML Kit OCR/Face, MediaProjection, telephony, Room). The pure layer is fully unit-tested with JUnit on the JVM; the impure layer is thin, wired around the pure core, and verified on-device. This boundary is what lets the analysis logic evolve safely as the study scales.

**Tech Stack:** Kotlin, Jetpack Compose, Room, ML Kit (Text Recognition + Face Detection), Android MediaProjection/Telephony, JUnit 4.

## Global Constraints

- Package: `com.chiragdodia.screentrack` — all new files in this package.
- Min SDK 26, Target/Compile SDK 35.
- Version bump for this release: `versionName = "1.1.0"`, `versionCode = 3`.
- **No network.** The study build must not transmit any participant-derived data. `INTERNET` permission is removed; no Firebase Analytics/Crashlytics.
- **Nothing raw stored.** No screenshots, no OCR text retained (`textSnippet` removed). Only derived numeric/label fields persist.
- All analysis on-device; bitmaps recycled immediately after analysis.
- Honest output: when no text and no face signal exists, emotion = `"undetermined"` (never a fabricated `"neutral"`).
- Pure-logic files must have zero Android framework imports (so they unit-test on the JVM).
- Performance budget: per-frame analysis (OCR + face + combine) must complete well under the capture interval (default 5s). Face detection uses FAST mode.
- Database change is destructive (wipe on upgrade) — acceptable: no real participant data exists yet.

---

## File Structure

**New — pure logic (JVM-unit-tested):**
- `TextSentimentClassifier.kt` — OCR text → emotion + sentiment, with negation handling.
- `ContentClassifier.kt` — OCR text → content category (improved, weighted).
- `FaceSignal.kt` — smiling probability → positive/negative signal.
- `AnalysisCombiner.kt` — merge text + face signals → final `AnalysisResult`.
- `PauseState.kt` — three independent pause reasons → effective paused flag.

**New — impure I/O (device-verified):**
- `CallMonitor.kt` — phone + VoIP call detection.

**Modified:**
- `OnDeviceAnalyzer.kt` — orchestrator: calls ML Kit, delegates decisions to pure layer.
- `CaptureResultEntity.kt` — remove `textSnippet`.
- `AppDatabase.kt` — version 2 + destructive migration.
- `ScreenCaptureService.kt` — pause state machine, call monitor, manual pause actions, remove analytics/jpeg.
- `ServiceStatusStore.kt` — expose `isPaused`.
- `MainActivity.kt` — pause button, remove snippet display + JPEG setting, request `READ_PHONE_STATE`.
- `ChartComponents.kt` — color for `"undetermined"`.
- `app/build.gradle.kts` — add face-detection + JUnit; remove okhttp + Firebase.
- `app/src/main/AndroidManifest.xml` — add `READ_PHONE_STATE`; remove `INTERNET`, `ACCESS_NETWORK_STATE`.
- `build.gradle.kts` (project) — remove google-services / crashlytics plugins.
- `docs/IRB-data-handling.md` — disclose face analysis + removals.

**Test files (new):**
- `app/src/test/java/com/chiragdodia/screentrack/{TextSentimentClassifier,ContentClassifier,FaceSignal,AnalysisCombiner,PauseState}Test.kt`

---

## Engineering notes (VP lens)

- **Observability tradeoff (documented):** Crashlytics is removed for the study's privacy guarantee. At scale, reintroduce crash reporting against a project *we control*, behind a build flavor (`studyRelease` vs `publicRelease`), so the study build stays network-silent while a future public build keeps observability. Out of scope now; noted so it isn't lost.
- **Build flavors:** not introduced now (YAGNI), but the privacy removals are isolated so a flavor split is a clean future step.
- **Why pure/impure split:** the sentiment/category logic is the part most likely to be iterated as data comes in. Keeping it pure means every change is covered by fast JVM tests rather than requiring a device.

---

## Task 0: Establish unit-test harness

There are currently **no tests**. This task adds the JUnit harness and proves it runs.

**Files:**
- Modify: `app/build.gradle.kts` (dependencies block)
- Test: `app/src/test/java/com/chiragdodia/screentrack/SanityTest.kt`

- [ ] **Step 1: Add JUnit dependency.** In `app/build.gradle.kts` dependencies block, add:

```kotlin
    testImplementation("junit:junit:4.13.2")
```

- [ ] **Step 2: Create the failing sanity test** at `app/src/test/java/com/chiragdodia/screentrack/SanityTest.kt`:

```kotlin
package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Test

class SanityTest {
    @Test fun harness_runs() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 3: Run it.**

Run: `./gradlew testDebugUnitTest --tests "com.chiragdodia.screentrack.SanityTest"`
Expected: BUILD SUCCESSFUL, 1 test passes.

- [ ] **Step 4: Commit.**

```bash
git add app/build.gradle.kts app/src/test/java/com/chiragdodia/screentrack/SanityTest.kt
git commit -m "test: add JUnit unit-test harness"
```

---

## Task 1: TextSentimentClassifier (pure)

**Files:**
- Create: `app/src/main/java/com/chiragdodia/screentrack/TextSentimentClassifier.kt`
- Test: `app/src/test/java/com/chiragdodia/screentrack/TextSentimentClassifierTest.kt`

**Interfaces:**
- Produces:
  - `data class TextEmotion(val emotion: String, val confidence: Double, val sentiment: Double, val hadSignal: Boolean)`
  - `object TextSentimentClassifier { fun classify(text: String): TextEmotion }`
  - Emotion labels: `"joy" | "sadness" | "anger" | "fear" | "surprise" | "neutral"`. `hadSignal=false` ⇒ `emotion="neutral"`, `sentiment=0.0`.

- [ ] **Step 1: Write failing tests** at `TextSentimentClassifierTest.kt`:

```kotlin
package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSentimentClassifierTest {
    @Test fun positive_text_is_joyful_and_positive() {
        val r = TextSentimentClassifier.classify("I am so happy and grateful, this is wonderful")
        assertEquals("joy", r.emotion)
        assertTrue("sentiment should be positive", r.sentiment > 0.0)
        assertTrue(r.hadSignal)
    }

    @Test fun negative_text_is_negative() {
        val r = TextSentimentClassifier.classify("this is terrible, I feel so sad and alone")
        assertTrue("sentiment should be negative", r.sentiment < 0.0)
        assertTrue(r.hadSignal)
    }

    @Test fun negation_flips_polarity() {
        val r = TextSentimentClassifier.classify("I am not happy at all")
        assertTrue("negated positive should not be positive", r.sentiment <= 0.0)
    }

    @Test fun no_emotion_words_has_no_signal() {
        val r = TextSentimentClassifier.classify("the train departs at 9 from platform two")
        assertEquals(false, r.hadSignal)
        assertEquals("neutral", r.emotion)
        assertEquals(0.0, r.sentiment, 0.0001)
    }

    @Test fun empty_text_has_no_signal() {
        val r = TextSentimentClassifier.classify("")
        assertEquals(false, r.hadSignal)
    }
}
```

- [ ] **Step 2: Run tests, verify they fail.**

Run: `./gradlew testDebugUnitTest --tests "com.chiragdodia.screentrack.TextSentimentClassifierTest"`
Expected: FAIL — unresolved reference `TextSentimentClassifier`.

- [ ] **Step 3: Implement** `TextSentimentClassifier.kt`:

```kotlin
package com.chiragdodia.screentrack

data class TextEmotion(
    val emotion: String,
    val confidence: Double,
    val sentiment: Double,
    val hadSignal: Boolean
)

/**
 * Pure, on-device lexicon sentiment with simple negation handling.
 * No Android imports — unit-tested on the JVM.
 */
object TextSentimentClassifier {

    private val NEGATORS = setOf("not", "no", "never", "n't", "cant", "cannot", "dont", "isnt", "wasnt")

    private val LEXICON: Map<String, List<String>> = mapOf(
        "joy" to listOf("happy", "joy", "love", "wonderful", "amazing", "great", "excited", "fun",
            "laugh", "smile", "celebrate", "congratulations", "awesome", "fantastic", "delighted",
            "pleased", "grateful", "thankful", "blessed", "enjoy", "lol", "haha", "glad", "win"),
        "sadness" to listOf("sad", "cry", "depressed", "unhappy", "alone", "lonely", "grief",
            "heartbreak", "disappoint", "regret", "sorrow", "tears", "suffer", "mourn", "hurt",
            "pain", "miserable", "hopeless", "miss"),
        "anger" to listOf("angry", "furious", "hate", "annoying", "frustrated", "outrage", "rage",
            "mad", "irritate", "disgusted", "offensive", "terrible", "awful", "worst", "unfair", "blame"),
        "fear" to listOf("scared", "afraid", "worried", "anxious", "nervous", "panic", "terror",
            "dread", "frighten", "horrified", "stress", "overwhelm", "danger", "threat", "crisis", "urgent"),
        "surprise" to listOf("wow", "surprising", "unexpected", "shocking", "unbelievable", "incredible",
            "astonishing", "remarkable", "sudden", "revealed", "exclusive")
    )

    private val SENTIMENT_OF: Map<String, Double> = mapOf(
        "joy" to 0.8, "surprise" to 0.3, "neutral" to 0.0,
        "sadness" to -0.5, "fear" to -0.6, "anger" to -0.7
    )

    fun classify(text: String): TextEmotion {
        if (text.isBlank()) return TextEmotion("neutral", 1.0, 0.0, hadSignal = false)
        val tokens = text.lowercase().split(Regex("[^a-z']+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return TextEmotion("neutral", 1.0, 0.0, hadSignal = false)

        val scores = LEXICON.keys.associateWith { 0.0 }.toMutableMap()
        var negativeFlips = 0.0
        tokens.forEachIndexed { i, tok ->
            val emotion = LEXICON.entries.firstOrNull { (_, words) -> words.any { tok.contains(it) } }?.key
            if (emotion != null) {
                val negated = (1..2).any { back -> tokens.getOrNull(i - back) in NEGATORS }
                if (negated) {
                    // a negated emotion word contributes the opposite polarity, counted as a flip
                    negativeFlips += 1.0
                } else {
                    scores[emotion] = scores.getValue(emotion) + 1.0
                }
            }
        }

        val totalHits = scores.values.sum() + negativeFlips
        if (totalHits == 0.0) return TextEmotion("neutral", 1.0, 0.0, hadSignal = false)

        val top = scores.maxByOrNull { it.value }!!
        // If flips dominate (e.g. "not happy"), fall to a negative read.
        if (top.value == 0.0 || negativeFlips > top.value) {
            return TextEmotion("sadness", (negativeFlips / totalHits).coerceIn(0.0, 1.0), -0.4, hadSignal = true)
        }
        val confidence = (top.value / totalHits).coerceIn(0.0, 1.0)
        val baseSentiment = SENTIMENT_OF.getValue(top.key)
        val sentiment = if (negativeFlips > 0) baseSentiment * 0.5 else baseSentiment
        return TextEmotion(top.key, confidence, sentiment, hadSignal = true)
    }
}
```

- [ ] **Step 4: Run tests, verify pass.**

Run: `./gradlew testDebugUnitTest --tests "com.chiragdodia.screentrack.TextSentimentClassifierTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/chiragdodia/screentrack/TextSentimentClassifier.kt app/src/test/java/com/chiragdodia/screentrack/TextSentimentClassifierTest.kt
git commit -m "feat: add tested text sentiment classifier with negation handling"
```

---

## Task 2: ContentClassifier (pure)

**Files:**
- Create: `app/src/main/java/com/chiragdodia/screentrack/ContentClassifier.kt`
- Test: `app/src/test/java/com/chiragdodia/screentrack/ContentClassifierTest.kt`

**Interfaces:**
- Produces:
  - `data class ContentResult(val contentType: String, val confidence: Double, val hadSignal: Boolean)`
  - `object ContentClassifier { fun classify(text: String): ContentResult }`
  - Categories: `social_media, messaging, news, shopping, work, gaming, entertainment, health, education, general`. No signal ⇒ `"general"`, `hadSignal=false`.

- [ ] **Step 1: Write failing tests** at `ContentClassifierTest.kt`:

```kotlin
package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentClassifierTest {
    @Test fun gaming_text_classified_gaming() {
        val r = ContentClassifier.classify("level 5 respawn score coins achievement among us imposter")
        assertEquals("gaming", r.contentType)
        assertTrue(r.hadSignal)
    }
    @Test fun education_text_classified_education() {
        val r = ContentClassifier.classify("lecture chapter homework tutorial study exam course")
        assertEquals("education", r.contentType)
    }
    @Test fun social_text_classified_social() {
        val r = ContentClassifier.classify("retweet like follow story reel comment")
        assertEquals("social_media", r.contentType)
    }
    @Test fun unclassifiable_is_general_without_signal() {
        val r = ContentClassifier.classify("xggh qqzz lorem 1234")
        assertEquals("general", r.contentType)
        assertEquals(false, r.hadSignal)
    }
}
```

- [ ] **Step 2: Run, verify fail.**

Run: `./gradlew testDebugUnitTest --tests "com.chiragdodia.screentrack.ContentClassifierTest"`
Expected: FAIL — unresolved reference.

- [ ] **Step 3: Implement** `ContentClassifier.kt`:

```kotlin
package com.chiragdodia.screentrack

data class ContentResult(
    val contentType: String,
    val confidence: Double,
    val hadSignal: Boolean
)

/** Pure content-category classifier over OCR text. No Android imports. */
object ContentClassifier {

    private val CATEGORIES: Map<String, List<String>> = mapOf(
        "social_media" to listOf("tweet", "retweet", "instagram", "tiktok", "facebook", "follow",
            "story", "reel", "comment", "feed", "hashtag", "viral", "dm"),
        "messaging" to listOf("message", "chat", "reply", "inbox", "typing", "delivered", "read receipt",
            "whatsapp", "imessage", "text message"),
        "news" to listOf("breaking", "headline", "report", "journalist", "published", "reuters", "bbc",
            "cnn", "article", "politics", "election"),
        "shopping" to listOf("checkout", "cart", "buy now", "add to cart", "order", "discount", "sale",
            "shipping", "amazon", "price"),
        "work" to listOf("meeting", "spreadsheet", "deadline", "email", "calendar", "project", "slack",
            "zoom", "document", "report due"),
        "gaming" to listOf("respawn", "level", "score", "coins", "achievement", "quest", "imposter",
            "among us", "fortnite", "minecraft", "game over", "xp", "loadout"),
        "entertainment" to listOf("episode", "season", "movie", "trailer", "netflix", "stream",
            "subscribe", "playlist", "music video", "watch now"),
        "health" to listOf("calories", "workout", "exercise", "steps", "heart rate", "meditation",
            "sleep", "nutrition", "wellness"),
        "education" to listOf("lecture", "chapter", "homework", "tutorial", "study", "exam", "course",
            "quiz", "assignment", "syllabus", "professor", "learn")
    )

    fun classify(text: String): ContentResult {
        if (text.isBlank()) return ContentResult("general", 1.0, hadSignal = false)
        val lower = text.lowercase()
        val scores = CATEGORIES.mapValues { (_, words) -> words.count { lower.contains(it) }.toDouble() }
        val top = scores.maxByOrNull { it.value }
        val total = scores.values.sum()
        if (top == null || top.value == 0.0) return ContentResult("general", 1.0, hadSignal = false)
        return ContentResult(top.key, (top.value / total).coerceIn(0.0, 1.0), hadSignal = true)
    }
}
```

- [ ] **Step 4: Run, verify pass.**

Run: `./gradlew testDebugUnitTest --tests "com.chiragdodia.screentrack.ContentClassifierTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/chiragdodia/screentrack/ContentClassifier.kt app/src/test/java/com/chiragdodia/screentrack/ContentClassifierTest.kt
git commit -m "feat: add tested content classifier with education category"
```

---

## Task 3: FaceSignal (pure)

**Files:**
- Create: `app/src/main/java/com/chiragdodia/screentrack/FaceSignal.kt`
- Test: `app/src/test/java/com/chiragdodia/screentrack/FaceSignalTest.kt`

**Interfaces:**
- Produces:
  - `data class FaceSignal(val sentiment: Double, val hadSignal: Boolean)`
  - `object FaceSignals { fun fromSmilingProbability(p: Float?): FaceSignal }`
  - `p == null` (no face) ⇒ `hadSignal=false, sentiment=0.0`.

- [ ] **Step 1: Write failing tests** at `FaceSignalTest.kt`:

```kotlin
package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceSignalTest {
    @Test fun no_face_no_signal() {
        val s = FaceSignals.fromSmilingProbability(null)
        assertEquals(false, s.hadSignal)
        assertEquals(0.0, s.sentiment, 0.0001)
    }
    @Test fun big_smile_is_positive() {
        val s = FaceSignals.fromSmilingProbability(0.9f)
        assertTrue(s.hadSignal)
        assertTrue(s.sentiment > 0.0)
    }
    @Test fun no_smile_is_negative_leaning() {
        val s = FaceSignals.fromSmilingProbability(0.05f)
        assertTrue(s.hadSignal)
        assertTrue(s.sentiment < 0.0)
    }
}
```

- [ ] **Step 2: Run, verify fail.**

Run: `./gradlew testDebugUnitTest --tests "com.chiragdodia.screentrack.FaceSignalTest"`
Expected: FAIL.

- [ ] **Step 3: Implement** `FaceSignal.kt`:

```kotlin
package com.chiragdodia.screentrack

data class FaceSignal(val sentiment: Double, val hadSignal: Boolean)

/** Pure mapping from a face's smiling probability to a sentiment contribution. */
object FaceSignals {
    fun fromSmilingProbability(p: Float?): FaceSignal {
        if (p == null) return FaceSignal(0.0, hadSignal = false)
        // Map [0,1] smiling probability to [-0.6, +0.8] sentiment.
        val sentiment = when {
            p >= 0.6f -> 0.8 * ((p - 0.6) / 0.4)
            p <= 0.3f -> -0.6 * ((0.3 - p) / 0.3)
            else -> 0.0
        }
        return FaceSignal(sentiment.coerceIn(-0.6, 0.8), hadSignal = true)
    }
}
```

- [ ] **Step 4: Run, verify pass.**

Run: `./gradlew testDebugUnitTest --tests "com.chiragdodia.screentrack.FaceSignalTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/chiragdodia/screentrack/FaceSignal.kt app/src/test/java/com/chiragdodia/screentrack/FaceSignalTest.kt
git commit -m "feat: add tested face smiling->sentiment mapping"
```

---

## Task 4: AnalysisCombiner (pure)

**Files:**
- Create: `app/src/main/java/com/chiragdodia/screentrack/AnalysisCombiner.kt`
- Test: `app/src/test/java/com/chiragdodia/screentrack/AnalysisCombinerTest.kt`

**Interfaces:**
- Consumes: `TextEmotion` (Task 1), `FaceSignal` (Task 3), `ContentResult` (Task 2).
- Produces:
  - `data class AnalysisResult(val emotion: String, val emotionConfidence: Double, val sentiment: Double, val contentType: String, val contentConfidence: Double)`
  - `object AnalysisCombiner { fun combine(text: TextEmotion, face: FaceSignal, content: ContentResult): AnalysisResult }`
  - Neither text nor face signal ⇒ `emotion="undetermined"`, `emotionConfidence=0.0`, `sentiment=0.0`.

- [ ] **Step 1: Write failing tests** at `AnalysisCombinerTest.kt`:

```kotlin
package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisCombinerTest {
    private val noContent = ContentResult("general", 1.0, hadSignal = false)

    @Test fun neither_signal_is_undetermined() {
        val r = AnalysisCombiner.combine(
            TextEmotion("neutral", 1.0, 0.0, hadSignal = false),
            FaceSignal(0.0, hadSignal = false),
            noContent
        )
        assertEquals("undetermined", r.emotion)
        assertEquals(0.0, r.sentiment, 0.0001)
        assertEquals(0.0, r.emotionConfidence, 0.0001)
    }

    @Test fun text_only_uses_text() {
        val r = AnalysisCombiner.combine(
            TextEmotion("joy", 0.7, 0.8, hadSignal = true),
            FaceSignal(0.0, hadSignal = false),
            noContent
        )
        assertEquals("joy", r.emotion)
        assertTrue(r.sentiment > 0.0)
    }

    @Test fun face_only_uses_face_sentiment() {
        val r = AnalysisCombiner.combine(
            TextEmotion("neutral", 1.0, 0.0, hadSignal = false),
            FaceSignal(0.7, hadSignal = true),
            noContent
        )
        assertTrue(r.sentiment > 0.0)
        assertEquals("joy", r.emotion) // positive face -> joy label
    }

    @Test fun both_present_blends_sentiment() {
        val r = AnalysisCombiner.combine(
            TextEmotion("sadness", 0.6, -0.5, hadSignal = true),
            FaceSignal(0.8, hadSignal = true),
            ContentResult("entertainment", 0.5, hadSignal = true)
        )
        // blend of -0.5 and +0.8 lands between them
        assertTrue(r.sentiment > -0.5 && r.sentiment < 0.8)
        assertEquals("entertainment", r.contentType)
    }
}
```

- [ ] **Step 2: Run, verify fail.**

Run: `./gradlew testDebugUnitTest --tests "com.chiragdodia.screentrack.AnalysisCombinerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement** `AnalysisCombiner.kt`:

```kotlin
package com.chiragdodia.screentrack

data class AnalysisResult(
    val emotion: String,
    val emotionConfidence: Double,
    val sentiment: Double,
    val contentType: String,
    val contentConfidence: Double
)

/** Pure merge of text + face signals into the persisted analysis result. */
object AnalysisCombiner {

    private const val TEXT_WEIGHT = 0.6
    private const val FACE_WEIGHT = 0.4

    fun combine(text: TextEmotion, face: FaceSignal, content: ContentResult): AnalysisResult {
        val emotion: String
        val emotionConfidence: Double
        val sentiment: Double

        when {
            !text.hadSignal && !face.hadSignal -> {
                emotion = "undetermined"; emotionConfidence = 0.0; sentiment = 0.0
            }
            text.hadSignal && face.hadSignal -> {
                sentiment = (text.sentiment * TEXT_WEIGHT + face.sentiment * FACE_WEIGHT)
                    .coerceIn(-1.0, 1.0)
                emotion = labelFor(text, sentiment)
                emotionConfidence = ((text.confidence + 1.0) / 2.0).coerceIn(0.0, 1.0)
            }
            text.hadSignal -> {
                sentiment = text.sentiment; emotion = text.emotion; emotionConfidence = text.confidence
            }
            else -> { // face only
                sentiment = face.sentiment
                emotion = if (face.sentiment >= 0) "joy" else "sadness"
                emotionConfidence = kotlin.math.abs(face.sentiment).coerceIn(0.0, 1.0)
            }
        }
        return AnalysisResult(emotion, emotionConfidence, sentiment, content.contentType, content.confidence)
    }

    private fun labelFor(text: TextEmotion, blended: Double): String {
        // Prefer the text's discrete emotion, unless the face flips overall polarity hard.
        return if (text.sentiment >= 0 && blended < -0.2) "sadness"
        else if (text.sentiment < 0 && blended > 0.2) "joy"
        else text.emotion
    }
}
```

- [ ] **Step 4: Run, verify pass.**

Run: `./gradlew testDebugUnitTest --tests "com.chiragdodia.screentrack.AnalysisCombinerTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/chiragdodia/screentrack/AnalysisCombiner.kt app/src/test/java/com/chiragdodia/screentrack/AnalysisCombinerTest.kt
git commit -m "feat: add tested text+face signal combiner"
```

---

## Task 5: PauseState (pure)

**Files:**
- Create: `app/src/main/java/com/chiragdodia/screentrack/PauseState.kt`
- Test: `app/src/test/java/com/chiragdodia/screentrack/PauseStateTest.kt`

**Interfaces:**
- Produces:
  - `data class PauseState(val screenOff: Boolean=false, val onCall: Boolean=false, val manual: Boolean=false) { val isPaused: Boolean }`
  - `isPaused == screenOff || onCall || manual`.

- [ ] **Step 1: Write failing tests** at `PauseStateTest.kt`:

```kotlin
package com.chiragdodia.screentrack

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseStateTest {
    @Test fun default_is_running() = assertEquals(false, PauseState().isPaused)
    @Test fun any_reason_pauses() {
        assertEquals(true, PauseState(screenOff = true).isPaused)
        assertEquals(true, PauseState(onCall = true).isPaused)
        assertEquals(true, PauseState(manual = true).isPaused)
    }
    @Test fun resumes_only_when_all_clear() {
        val s = PauseState(screenOff = true, onCall = true, manual = true)
        assertEquals(true, s.copy(screenOff = false, onCall = false).isPaused) // manual still on
        assertEquals(false, s.copy(screenOff = false, onCall = false, manual = false).isPaused)
    }
}
```

- [ ] **Step 2: Run, verify fail.**

Run: `./gradlew testDebugUnitTest --tests "com.chiragdodia.screentrack.PauseStateTest"`
Expected: FAIL.

- [ ] **Step 3: Implement** `PauseState.kt`:

```kotlin
package com.chiragdodia.screentrack

data class PauseState(
    val screenOff: Boolean = false,
    val onCall: Boolean = false,
    val manual: Boolean = false
) {
    val isPaused: Boolean get() = screenOff || onCall || manual
}
```

- [ ] **Step 4: Run, verify pass.**

Run: `./gradlew testDebugUnitTest --tests "com.chiragdodia.screentrack.PauseStateTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/chiragdodia/screentrack/PauseState.kt app/src/test/java/com/chiragdodia/screentrack/PauseStateTest.kt
git commit -m "feat: add tested multi-source pause state"
```

---

## Task 6: Rewrite OnDeviceAnalyzer as orchestrator (+ face detection dep)

This is device/ML code; verified by build (ML Kit can't run in JVM unit tests).

**Files:**
- Modify: `app/build.gradle.kts` (add face-detection)
- Modify: `app/src/main/java/com/chiragdodia/screentrack/OnDeviceAnalyzer.kt`

**Interfaces:**
- Produces: `class OnDeviceAnalyzer { suspend fun analyze(bitmap: Bitmap): AnalysisResult; fun close() }`
  (returns the `AnalysisResult` from Task 4 — `textSnippet` no longer exists.)

- [ ] **Step 1: Add face-detection dependency.** In `app/build.gradle.kts` dependencies, below the text-recognition line add:

```kotlin
    implementation("com.google.mlkit:face-detection:16.1.7")
```

- [ ] **Step 2: Replace** `OnDeviceAnalyzer.kt` entirely with:

```kotlin
package com.chiragdodia.screentrack

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates on-device ML (OCR + face) and delegates all decisions to the
 * pure classifiers. No screen image is ever stored or transmitted.
 */
class OnDeviceAnalyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // enables smiling probability
            .build()
    )

    suspend fun analyze(bitmap: Bitmap): AnalysisResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = runCatching { Tasks.await(textRecognizer.process(image)).text }.getOrDefault("")
        val smiling: Float? = runCatching {
            Tasks.await(faceDetector.process(image)).maxByOrNull { it.smilingProbability ?: -1f }?.smilingProbability
        }.getOrNull()

        val textEmotion = TextSentimentClassifier.classify(text)
        val content = ContentClassifier.classify(text)
        val face = FaceSignals.fromSmilingProbability(smiling)
        AnalysisCombiner.combine(textEmotion, face, content)
    }

    fun close() {
        textRecognizer.close()
        faceDetector.close()
    }
}
```

- [ ] **Step 3: Build to verify it compiles.**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Note: `ScreenCaptureService` still references the old `result.textSnippet`/`emotionConfidence` shape — it is fixed in Task 7; if compile fails only there, proceed to Task 7 and re-verify.)

- [ ] **Step 4: Commit.**

```bash
git add app/build.gradle.kts app/src/main/java/com/chiragdodia/screentrack/OnDeviceAnalyzer.kt
git commit -m "feat: orchestrate OCR + face detection via pure classifiers"
```

---

## Task 7: Data model — drop textSnippet + destructive migration

**Files:**
- Modify: `CaptureResultEntity.kt`, `AppDatabase.kt`, `ScreenCaptureService.kt` (entity construction only)

- [ ] **Step 1: Edit** `CaptureResultEntity.kt` — remove the `textSnippet` line so it reads:

```kotlin
@Entity(tableName = "capture_results")
data class CaptureResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val emotion: String,
    val emotionScore: Double,
    val sentiment: Double,
    val contentType: String,
    val contentConfidence: Double
)
```

- [ ] **Step 2: Edit** `AppDatabase.kt` — bump version and allow destructive migration:

```kotlin
@Database(entities = [CaptureResultEntity::class], version = 2, exportSchema = false)
```
and change the builder to:
```kotlin
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screentrack.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
```

- [ ] **Step 3: Edit** `ScreenCaptureService.kt` — the `repository.addResult(...)` block (currently ~lines 161-172). Replace the `CaptureResultEntity(...)` construction with (note `result` is now an `AnalysisResult`, and the Firebase bundle lines above it are removed in Task 8):

```kotlin
                    val repository = CaptureRepository.getInstance(applicationContext)
                    repository.addResult(
                        CaptureResultEntity(
                            timestampMs = System.currentTimeMillis(),
                            emotion = result.emotion,
                            emotionScore = result.emotionConfidence,
                            sentiment = result.sentiment,
                            contentType = result.contentType,
                            contentConfidence = result.contentConfidence
                        ),
                        retentionDays = retentionDays
                    )
```

- [ ] **Step 4: Build to verify.**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Firebase analytics lines still present compile fine; removed in Task 8).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/chiragdodia/screentrack/CaptureResultEntity.kt app/src/main/java/com/chiragdodia/screentrack/AppDatabase.kt app/src/main/java/com/chiragdodia/screentrack/ScreenCaptureService.kt
git commit -m "feat: drop stored text snippet; destructive DB migration to v2"
```

---

## Task 8: Remove Firebase, okhttp, INTERNET, and dead JPEG setting

**Files:**
- Modify: `ScreenCaptureService.kt`, `app/build.gradle.kts`, `build.gradle.kts` (project root), `app/src/main/AndroidManifest.xml`, `MainActivity.kt`

- [ ] **Step 1: In `ScreenCaptureService.kt`** remove the Firebase import (`import com.google.firebase.analytics.FirebaseAnalytics`), the two lifecycle `logEvent` calls (`capture_started`, `capture_stopped`), and the `analysis_received` bundle + logEvent block (the 4 lines building `bundle` and the `FirebaseAnalytics.getInstance(...).logEvent("analysis_received", bundle)` line).

- [ ] **Step 2: In `ScreenCaptureService.kt`** remove the dead JPEG plumbing: the `jpegQuality` field, the `EXTRA_JPEG_QUALITY` constant, its read in `onStartCommand`, the `jpegQuality` parameter in `start(...)`, and its `putExtra`. (OCR/face work on the bitmap directly; JPEG is unused.)

- [ ] **Step 3: In `app/build.gradle.kts`** remove these dependency lines:
```kotlin
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
```
and the plugin line `id("com.google.firebase.crashlytics")`, and (if present) `id("com.google.gms.google-services")`.

- [ ] **Step 4: In the project root `build.gradle.kts`** remove the `com.google.gms.google-services` and `com.google.firebase.crashlytics` plugin classpath/aliases if declared there.

- [ ] **Step 5: In `AndroidManifest.xml`** delete the `INTERNET` and `ACCESS_NETWORK_STATE` `<uses-permission>` lines.

- [ ] **Step 6: In `MainActivity.kt`** remove the JPEG-quality UI control (the settings field around line 1252), its state (`jpegQuality` remember around line 175), its validation (line 185), its `savePrefs` argument + the `putInt("jpeg_quality", ...)` (line 438), and the `jpegQuality` argument passed to `ScreenCaptureService.start(...)` (line ~199/351). Update `savePrefs` signature accordingly.

- [ ] **Step 7: Build to verify everything still compiles without Firebase.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. If the `google-services` Gradle plugin complains about a missing `google-services.json`, that confirms Step 3/4 removed it correctly; if it still runs, ensure the plugin is fully removed.

- [ ] **Step 8: Run the full unit suite (nothing pure broke).**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (all tasks 1–5 tests green).

- [ ] **Step 9: Commit.**

```bash
git add -A
git commit -m "refactor: remove Firebase, okhttp, INTERNET permission, dead JPEG setting"
```

---

## Task 9: CallMonitor (phone + VoIP)

Device code; verified on-device.

**Files:**
- Create: `app/src/main/java/com/chiragdodia/screentrack/CallMonitor.kt`
- Modify: `AndroidManifest.xml` (add permission)

**Interfaces:**
- Produces: `class CallMonitor(context, onCallActiveChanged: (Boolean) -> Unit) { fun start(); fun stop() }`

- [ ] **Step 1: Add permission** to `AndroidManifest.xml`:
```xml
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

- [ ] **Step 2: Create** `CallMonitor.kt`:

```kotlin
package com.chiragdodia.screentrack

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager

/**
 * Detects active phone or VoIP/video calls so capture can auto-pause.
 * Phone calls via TelephonyManager call state; VoIP via AudioManager mode.
 */
class CallMonitor(
    private val context: Context,
    private val onCallActiveChanged: (Boolean) -> Unit
) {
    private val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var listener: android.telephony.PhoneStateListener? = null
    private var lastActive = false

    private fun evaluate() {
        @Suppress("DEPRECATION")
        val callState = telephony.callState // CALL_STATE_IDLE / RINGING / OFFHOOK
        val mode = audio.mode // MODE_IN_CALL / MODE_IN_COMMUNICATION for VoIP
        val active = callState != TelephonyManager.CALL_STATE_IDLE ||
            mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION
        if (active != lastActive) {
            lastActive = active
            onCallActiveChanged(active)
        }
    }

    fun start() {
        @Suppress("DEPRECATION")
        listener = object : android.telephony.PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) { evaluate() }
        }
        @Suppress("DEPRECATION")
        telephony.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        evaluate() // initial state (also catches VoIP already in progress)
    }

    fun stop() {
        @Suppress("DEPRECATION")
        listener?.let { telephony.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE) }
        listener = null
    }
}
```
> Note: `PhoneStateListener` is deprecated in favor of `TelephonyCallback` (API 31+); it remains functional on min-SDK 26 and is the simplest cross-version path. A `TelephonyCallback` split by SDK is a future refinement, not required now.

- [ ] **Step 3: Build to verify.**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/chiragdodia/screentrack/CallMonitor.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add phone + VoIP call monitor for auto-pause"
```

---

## Task 10: Wire pause state machine into the service

**Files:**
- Modify: `ScreenCaptureService.kt`, `ServiceStatusStore.kt`

- [ ] **Step 1: In `ServiceStatusStore.kt`** add an `isPaused` StateFlow mirroring the existing `isRunning` pattern (a `MutableStateFlow<Boolean>(false)` exposed as `StateFlow<Boolean>` with a `setPaused(value: Boolean)` setter). Follow the file's existing flow/setter convention exactly.

- [ ] **Step 2: In `ScreenCaptureService.kt`** replace `private var isPaused: Boolean = false` with:
```kotlin
    private var pauseState = PauseState()
    private var callMonitor: CallMonitor? = null

    private fun updatePause(transform: (PauseState) -> PauseState) {
        pauseState = transform(pauseState)
        ServiceStatusStore.getInstance(applicationContext).setPaused(pauseState.isPaused)
    }
```

- [ ] **Step 3:** In the image listener, change `if (isPaused) {` to `if (pauseState.isPaused) {`.

- [ ] **Step 4:** In `registerScreenReceiver()`, set screen-off via the new helper:
```kotlin
                    Intent.ACTION_SCREEN_OFF -> updatePause { it.copy(screenOff = true) }
                    Intent.ACTION_USER_PRESENT -> updatePause { it.copy(screenOff = false) }
```
and in `unregisterScreenReceiver()` replace `isPaused = false` with `updatePause { PauseState() }`.

- [ ] **Step 5:** Start/stop the call monitor. In `startProjection(...)` end (or `onStartCommand` after `registerScreenReceiver()`):
```kotlin
        callMonitor = CallMonitor(applicationContext) { active ->
            updatePause { it.copy(onCall = active) }
        }.also { it.start() }
```
and in `onDestroy()` add `callMonitor?.stop()` before `stopProjection()`.

- [ ] **Step 6:** Add manual pause/resume actions. Add constants:
```kotlin
        const val ACTION_PAUSE = "com.chiragdodia.screentrack.action.PAUSE"
        const val ACTION_RESUME = "com.chiragdodia.screentrack.action.RESUME"
```
In `onStartCommand`, before the projection-data handling, handle them:
```kotlin
        when (intent?.action) {
            ACTION_PAUSE -> { updatePause { it.copy(manual = true) }; return START_STICKY }
            ACTION_RESUME -> { updatePause { it.copy(manual = false) }; return START_STICKY }
        }
```
Add static helpers mirroring `stop(context)`:
```kotlin
        fun pause(context: Context) = context.startService(
            Intent(context, ScreenCaptureService::class.java).apply { action = ACTION_PAUSE })
        fun resume(context: Context) = context.startService(
            Intent(context, ScreenCaptureService::class.java).apply { action = ACTION_RESUME })
```

- [ ] **Step 7: Build to verify.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit.**

```bash
git add app/src/main/java/com/chiragdodia/screentrack/ScreenCaptureService.kt app/src/main/java/com/chiragdodia/screentrack/ServiceStatusStore.kt
git commit -m "feat: multi-source pause (screen/call/manual) wired into service"
```

---

## Task 11: UI — pause button, permission, remove snippet display

**Files:**
- Modify: `MainActivity.kt`

- [ ] **Step 1: Remove the stored-text display.** Delete the block around `MainActivity.kt:684-686` that renders `latestResult.textSnippet` (it no longer exists on the entity).

- [ ] **Step 2: Request `READ_PHONE_STATE`.** Where the app already requests `POST_NOTIFICATIONS` / sets up permission launchers, add `Manifest.permission.READ_PHONE_STATE` to the requested set (follow the existing `registerForActivityResult` pattern). It is optional-degrade: if denied, VoIP detection via AudioManager still works; phone-call detection won't.

- [ ] **Step 3: Add a Pause/Resume control.** Observe `viewModel`-exposed paused state (add `val isPaused` to `CaptureViewModel` mirroring `isRunning`, sourced from `statusStore.isPaused`). On the home/control screen, when `isRunning` is true show a button: label "Pause" when not paused (calls `ScreenCaptureService.pause(context)`), "Resume" when paused (calls `ScreenCaptureService.resume(context)`). Reuse the existing button styling used by Start/Stop.

- [ ] **Step 4: Build to verify.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/chiragdodia/screentrack/MainActivity.kt app/src/main/java/com/chiragdodia/screentrack/CaptureViewModel.kt
git commit -m "feat: pause/resume button, phone-state permission, remove snippet display"
```

---

## Task 12: Handle "undetermined" in consumers

**Files:**
- Modify: `ChartComponents.kt`

- [ ] **Step 1:** Find the `emotionColor(...)` function in `ChartComponents.kt` and add a branch for `"undetermined"` (a muted gray, e.g. `Color(0xFF9AA0A6)`) and ensure `"neutral"` and any unknown label also have a sensible default. Pie/among charts group by emotion string, so `"undetermined"` will appear as its own slice — confirm the legend/labels render it.

- [ ] **Step 2: Build to verify.**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/chiragdodia/screentrack/ChartComponents.kt
git commit -m "feat: render 'undetermined' emotion in charts"
```

---

## Task 13: Version bump, release build, on-device verification

**Files:**
- Modify: `app/build.gradle.kts` (versionName/versionCode)

- [ ] **Step 1:** In `app/build.gradle.kts` `defaultConfig`, set `versionCode = 3` and `versionName = "1.1.0"`.

- [ ] **Step 2: Full unit suite.**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (all pure-logic tests).

- [ ] **Step 3: Release build.**

Run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/release/app-release.apk`, version 1.1.0 (3).

- [ ] **Step 4: On-device manual verification checklist** (install on a physical phone). Confirm each:
  - App starts capture; status shows running.
  - Open a text-heavy app (e.g., a news article) → an analysis row appears with a sensible emotion/sentiment (not all "undetermined").
  - Open a video with a visible face → results appear; sentiment reflects expression.
  - Open a blank/no-text screen → emotion shows "undetermined" (not fake "neutral").
  - Receive/place a phone call → capture pauses (status shows paused); end call → resumes.
  - Start a WhatsApp/Zoom call → capture pauses; end → resumes.
  - Tap Pause → stays paused even after locking/unlocking; tap Resume → resumes.
  - Airplane-mode/offline: app fully functions (no network needed).
  - Charts/Insights render including any "undetermined" slice.

- [ ] **Step 5: Commit.**

```bash
git add app/build.gradle.kts
git commit -m "release: bump to 1.1.0 (versionCode 3)"
```

---

## Task 14: Update IRB data-handling doc

**Files:**
- Modify: `docs/IRB-data-handling.md`

- [ ] **Step 1:** Add a subsection under data collection stating the app also performs **on-device facial-expression detection** (smiling probability) when a face is visible in content; faces are **never stored or transmitted**; capture **auto-pauses during phone and VoIP/video calls** plus a manual pause. Note the removals: no stored text snippet, no analytics, no crash reporting, no network/`INTERNET` permission.

- [ ] **Step 2:** Update the §10 summary table: "What leaves the device?" → **Nothing**.

- [ ] **Step 3: Commit.**

```bash
git add docs/IRB-data-handling.md
git commit -m "docs: disclose on-device face analysis and privacy removals in IRB doc"
```

---

## Self-Review (completed by author)

- **Spec coverage:** text+face sentiment (T1,3,4,6) ✓; improved content/no app detection (T2) ✓; honest "undetermined" (T4,12) ✓; remove textSnippet (T7) ✓; remove Analytics/Crashlytics (T8) ✓; remove okhttp/INTERNET/JPEG (T8) ✓; destructive migration (T7) ✓; manual pause (T10,11) ✓; call auto-pause (T9,10) ✓; multi-source pause (T5,10) ✓; tests (T0–5) ✓; archive HF (already committed) ✓; IRB doc update (T14) ✓.
- **Placeholder scan:** none — every code step contains full code; wiring steps cite exact files/lines.
- **Type consistency:** `AnalysisResult` (T4) is what `OnDeviceAnalyzer.analyze` returns (T6) and what `ScreenCaptureService` persists (T7); `TextEmotion`/`ContentResult`/`FaceSignal` names match across T1–4,6; `PauseState` fields match T5↔T10.

## Out of scope (tracked for later)
- Daily mental-health survey (separate spec, pending Krista's data-delivery decision).
- Build flavors (`studyRelease` vs `publicRelease`) + reintroducing crash reporting to a controlled project at scale.
- `TelephonyCallback` migration for API 31+.
- TFLite/transformer sentiment upgrade.
- Battery/performance profiling pass on real devices.
