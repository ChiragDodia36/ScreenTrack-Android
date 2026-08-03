# 27-Emotion On-Device Model + Hourly CSV — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the keyword emotion classifier with a real on-device GoEmotions-27 ML model (plus a facial-emotion model), and replace the PDF export with an hourly CSV that has a column per emotion (model score), per sentiment bucket (%), and per content category (%).

**Architecture:** Text is classified by a quantized RoBERTa-GoEmotions model run via ONNX Runtime Mobile, producing 28 per-emotion scores. When a face is present, a small FER model contributes 7 emotions mapped into the 28 and blended (text 0.6 / face 0.4). Per-capture 28-score maps are stored locally; the CSV aggregates them by hour. Everything stays on-device (no network).

**Tech Stack:** Kotlin, ONNX Runtime Mobile (`com.microsoft.onnxruntime:onnxruntime-android`), ONNX Runtime Extensions (embedded tokenizer), ML Kit Face Detection (existing), Room, Jetpack Compose. Model conversion in Python (`optimum`, `onnxruntime`, `transformers`).

## Global Constraints
- **On-device only** — no `INTERNET`/`ACCESS_NETWORK_STATE` permission; the app must not gain network capability. Model + tokenizer are bundled assets.
- **Package:** `com.chiragdodia.screentrack`; minSdk 26; targetSdk 35.
- **DB migration is destructive** (wipe on update) — agreed; no real participant data yet.
- **Commits:** author `chiragdodia36 <chiragdodia36@gmail.com>`, **no** Claude co-author line.
- **Distribution:** Firebase App Distribution (no Play Store size limit concern).
- **TDD:** pure logic (mapping, blending, hourly aggregation, CSV) gets JVM unit tests; model/IO glue is verified on-device.

---

# PHASE 0 — Feasibility Spike (GATE)

**Purpose:** Prove the model runs on a real phone with acceptable size/latency/output BEFORE building anything on it. This phase is throwaway/experimental — its only deliverable is a go/no-go decision plus the converted model artifacts.

**Gate criteria (all must pass to proceed to Phase 1):**
- Model + tokenizer produce sensible 28-emotion scores for test sentences on-device.
- Final bundled model size ≤ ~150 MB (prefer int8 quantized).
- Per-inference latency ≤ ~300 ms on the test phone (Samsung, from earlier logs).
- No crash / OOM on the device.

### Task 0.1: Python conversion environment

**Files:**
- Create: `tools/emotion-model/requirements.txt`
- Create: `tools/emotion-model/README.md`

- [ ] **Step 1: Write requirements.txt**
```
optimum[exporters]==1.23.3
onnx==1.17.0
onnxruntime==1.20.1
onnxruntime-extensions==0.13.0
transformers==4.46.0
torch==2.5.1
```

- [ ] **Step 2: Create + install venv**
```bash
cd tools/emotion-model
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```
Expected: installs without error. (This is a local dev-only env; not part of the app.)

### Task 0.2: Export RoBERTa-GoEmotions to ONNX

**Files:**
- Create: `tools/emotion-model/export.sh`

- [ ] **Step 1: Export to ONNX via optimum**
```bash
optimum-cli export onnx \
  --model SamLowe/roberta-base-go_emotions \
  --task text-classification \
  tools/emotion-model/onnx/
```
Expected: produces `model.onnx`, `config.json`, `tokenizer.json`, `vocab.json`, `merges.txt` in `onnx/`.

- [ ] **Step 2: Dynamic int8 quantization**
```bash
python - <<'PY'
from onnxruntime.quantization import quantize_dynamic, QuantType
quantize_dynamic("tools/emotion-model/onnx/model.onnx",
                 "tools/emotion-model/onnx/model.int8.onnx",
                 weight_type=QuantType.QInt8)
PY
ls -lh tools/emotion-model/onnx/model.int8.onnx
```
Expected: `model.int8.onnx` ~120–130 MB (roberta-base is 125M params).

### Task 0.3: Verify ONNX output in Python

**Files:**
- Create: `tools/emotion-model/verify.py`

- [ ] **Step 1: Write verify.py**
```python
import onnxruntime as ort, numpy as np
from transformers import AutoTokenizer

tok = AutoTokenizer.from_pretrained("tools/emotion-model/onnx")
sess = ort.InferenceSession("tools/emotion-model/onnx/model.int8.onnx")
labels = [  # GoEmotions order from config.json id2label
  "admiration","amusement","anger","annoyance","approval","caring","confusion",
  "curiosity","desire","disappointment","disapproval","disgust","embarrassment",
  "excitement","fear","gratitude","grief","joy","love","nervousness","optimism",
  "pride","realization","relief","remorse","sadness","surprise","neutral"]

for text in ["I love this so much!", "this is terrifying", "meh, whatever"]:
    enc = tok(text, return_tensors="np", truncation=True, max_length=64)
    logits = sess.run(None, {k: v for k, v in enc.items()
                             if k in [i.name for i in sess.get_inputs()]})[0][0]
    probs = 1/(1+np.exp(-logits))  # sigmoid (multi-label)
    top = sorted(zip(labels, probs), key=lambda x: -x[1])[:3]
    print(text, "->", [(l, round(float(p),2)) for l,p in top])
```

- [ ] **Step 2: Run and sanity-check**
```bash
python tools/emotion-model/verify.py
```
Expected: "I love this" → love/admiration/joy high; "terrifying" → fear high. If labels are nonsense, STOP and re-check id2label order in `onnx/config.json`.

### Task 0.4: Decide on-device tokenizer strategy

RoBERTa uses byte-level BPE — hard to reimplement in Kotlin. Preferred: embed the tokenizer into the ONNX graph via `onnxruntime-extensions` so the model takes **raw text** as input.

**Files:**
- Create: `tools/emotion-model/embed_tokenizer.py`

- [ ] **Step 1: Attempt to build a combined text-in model**
```python
# Uses onnxruntime-extensions to prepend a HuggingFace tokenizer op.
from onnxruntime_extensions import gen_processing_models
from transformers import AutoTokenizer
tok = AutoTokenizer.from_pretrained("tools/emotion-model/onnx")
pre, _ = gen_processing_models(tok, pre_kwargs={})
open("tools/emotion-model/onnx/tokenizer.onnx","wb").write(pre.SerializeToString())
print("tokenizer.onnx written")
```

- [ ] **Step 2: Record the outcome**
Document in `tools/emotion-model/README.md` whether the tokenizer embeds cleanly.
- If **yes** → Android input is a raw string; Phase 1 uses ORT-Extensions on device.
- If **no** → fallback: bundle `vocab.json`+`merges.txt`, implement byte-level BPE in Kotlin (adds a Phase 1 task). **This outcome is a key spike finding.**

### Task 0.5: Minimal Android harness on the real phone

**Files:**
- Create: `app/src/main/assets/emotion_model.int8.onnx` (copy from tools output)
- Create: `app/src/main/assets/tokenizer.onnx` (if Task 0.4 succeeded)
- Modify: `app/build.gradle.kts` (add ORT deps, `aaptOptions.noCompress`)
- Create: `app/src/androidTest/java/com/chiragdodia/screentrack/EmotionSpikeTest.kt`

- [ ] **Step 1: Add dependencies + keep .onnx uncompressed**
```kotlin
// app/build.gradle.kts dependencies
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
implementation("com.microsoft.onnxruntime:onnxruntime-extensions-android:0.13.0")
// android { androidResources { noCompress += listOf("onnx") } }
```

- [ ] **Step 2: Write an instrumented test that runs one inference on-device**
```kotlin
@RunWith(AndroidJUnit4::class)
class EmotionSpikeTest {
    @Test fun runsAndTimes() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions()
        OrtxPackage.getLibraryPath()?.let { opts.registerCustomOpLibrary(it) }
        val bytes = ctx.assets.open("emotion_model.int8.onnx").readBytes()
        val session = env.createSession(bytes, opts)
        // input shape depends on Task 0.4 outcome; log latency + top scores
        val t0 = SystemClock.elapsedRealtime()
        // ... run inference on a fixed sample string ...
        Log.i("SPIKE", "latency=${SystemClock.elapsedRealtime()-t0}ms")
    }
}
```

- [ ] **Step 3: Run on the connected phone and capture metrics**
```bash
./gradlew connectedDebugAndroidTest
adb logcat -d -s SPIKE
```
Record: latency, that scores are sensible, APK size delta (`ls -lh app/build/outputs/apk/`).

### Task 0.6: Go / No-Go decision

- [ ] **Step 1: Evaluate against the gate criteria** (size ≤150 MB, latency ≤300 ms, sensible output, no OOM).
- [ ] **Step 2: Record the decision in the spec doc.**
  - **Go** → proceed to Phase 1 with the confirmed model + tokenizer strategy.
  - **No-Go (too big/slow)** → switch to a distilled model (e.g. a DistilRoBERTa-GoEmotions) and repeat 0.2–0.6. **Do not start Phase 1 until a model passes.**

---

# PHASE 1 — Text Emotion Engine

Depends on Phase 0 outcome (model file + tokenizer strategy + input/output tensor names).

### Task 1.1: `EmotionScores` type + label constants

**Files:**
- Create: `app/src/main/java/com/chiragdodia/screentrack/EmotionLabels.kt`
- Test: `app/src/test/java/com/chiragdodia/screentrack/EmotionLabelsTest.kt`

- [ ] **Step 1: Failing test** — assert the label list has 28 entries and includes "neutral".
- [ ] **Step 2: Implement** the `val GO_EMOTIONS: List<String>` (28, in model `id2label` order) and a `typealias EmotionScores = Map<String, Double>`.
- [ ] **Step 3: Run test / commit.**

### Task 1.2: `EmotionModel` ONNX wrapper (impure I/O)

**Files:**
- Create: `app/src/main/java/com/chiragdodia/screentrack/EmotionModel.kt`

**Interfaces:**
- Produces: `class EmotionModel(context) { suspend fun scores(text: String): EmotionScores; fun close() }` — returns sigmoid scores per GoEmotions label.

- [ ] **Step 1:** Load the ONNX session from assets (once), register the ORT-Extensions op library (or Kotlin BPE fallback per 0.4), run inference, sigmoid the logits, zip with `GO_EMOTIONS`.
- [ ] **Step 2:** On-device smoke via the Phase 0 harness (no JVM unit test — needs the native lib). Commit.

### Task 1.3: Replace keyword classifier in the analyzer

**Files:**
- Modify: `app/src/main/java/com/chiragdodia/screentrack/OnDeviceAnalyzer.kt`
- Delete: `app/src/main/java/com/chiragdodia/screentrack/TextSentimentClassifier.kt` (and its test)

**Interfaces:**
- `TextEmotion` gains `val scores: EmotionScores`. Dominant emotion = `scores.maxByOrNull{it.value}`; `sentiment` derived from a **valence table over 28 labels** (see Task 1.4).

- [ ] **Step 1:** Wire `EmotionModel.scores()` into `OnDeviceAnalyzer.analyze()`; keep image recycling unchanged.
- [ ] **Step 2:** Update `AnalysisCombiner` to carry the 28-score map through. Commit.

### Task 1.4: 28-label valence table (for the sentiment buckets)

**Files:**
- Create: `app/src/main/java/com/chiragdodia/screentrack/EmotionValence.kt`
- Test: `EmotionValenceTest.kt`

- [ ] **Step 1: Failing test** — e.g. `valence("joy") > 0`, `valence("grief") < 0`, `valence("neutral") == 0.0`, and every one of the 28 labels has an entry.
- [ ] **Step 2: Implement** a `Map<String,Double>` assigning each GoEmotions label a valence in [-1,1] (positive: joy, love, admiration, gratitude…; negative: anger, grief, fear, disgust…; neutral: realization, curiosity, neutral). Sentiment score = weighted avg of valences by emotion score.
- [ ] **Step 3: Run / commit.**

---

# PHASE 2 — Data Model

### Task 2.1: Store the 28-score map + bump DB version

**Files:**
- Modify: `app/src/main/java/com/chiragdodia/screentrack/CaptureResultEntity.kt` (add `val emotionScoresJson: String`)
- Modify: `app/src/main/java/com/chiragdodia/screentrack/AppDatabase.kt` (`version = 2`, `.fallbackToDestructiveMigration()`)
- Modify: `ScreenCaptureService.kt` (serialize scores to JSON when inserting)
- Test: `EmotionScoresJsonTest.kt`

- [ ] **Step 1: Failing test** — round-trip a `EmotionScores` map through the JSON encode/decode helper.
- [ ] **Step 2: Implement** a tiny `org.json`-based encode/decode (no new dependency), add the column, bump version, enable destructive migration.
- [ ] **Step 3:** Update the insert path in `ScreenCaptureService`. Run / commit.

---

# PHASE 3 — Facial Emotion Model

### Task 3.1: Bundle + wrap the FER model

**Files:**
- Create: `app/src/main/assets/fer_model.onnx` (chosen 7-emotion FER, from Phase 0-style conversion)
- Create: `app/src/main/java/com/chiragdodia/screentrack/FaceEmotionModel.kt`

**Interfaces:**
- Produces: `class FaceEmotionModel(context){ fun scores(faceBitmap: Bitmap): Map<String,Double> }` → 7 labels.

- [ ] **Step 1:** Crop the ML-Kit-detected face bounding box, preprocess (grayscale/resize per model), run ORT, softmax. On-device smoke. Commit.

### Task 3.2: Map 7→28 and blend

**Files:**
- Create: `app/src/main/java/com/chiragdodia/screentrack/FaceEmotionMap.kt`
- Modify: `AnalysisCombiner.kt`
- Test: `FaceEmotionMapTest.kt`, `AnalysisCombinerTest.kt`

- [ ] **Step 1: Failing tests** — mapping table (happy→joy, sad→sadness, angry→anger, fear→fear, surprise→surprise, disgust→disgust, neutral→neutral); blend = text*0.6 + face*0.4 on overlapping labels, text-only elsewhere; no face → text unchanged.
- [ ] **Step 2: Implement** the mapping + blend in `AnalysisCombiner.combine`. Run / commit.

---

# PHASE 4 — Hourly CSV Export

### Task 4.1: `CsvExporter` (pure, wide layout)

**Files:**
- Create: `app/src/main/java/com/chiragdodia/screentrack/CsvExporter.kt`
- Test: `CsvExporterTest.kt`

**Interfaces:**
- Produces: `object CsvExporter { fun buildHourlyCsv(items: List<CaptureResultEntity>): String }`

- [ ] **Step 1: Failing test** — two captures in the same hour with known scores → assert: header has `Date,Hour,` + 28 emotion labels + `positive,negative,neutral` + content categories; one data row; emotion cell = **average model score** for that emotion; sentiment/content cells = **% of the hour**; RFC-4180 quoting for cells with commas.
- [ ] **Step 2: Implement** — group by `yyyyMMddHH`; per hour: average each emotion's score, bucket sentiment via `EmotionValence` into positive/negative/neutral %, content category %. Skip empty hours.
- [ ] **Step 3: Run / commit.**

### Task 4.2: Swap export wiring PDF→CSV; delete PdfExporter

**Files:**
- Modify: `MainActivity.kt` (mime `text/csv`, filename `screentrack-report.csv`, call `CsvExporter`, relabel "Export CSV Report")
- Delete: `app/src/main/java/com/chiragdodia/screentrack/PdfExporter.kt`

- [ ] **Step 1:** Change `CreateDocument("text/csv")`, `exportLauncher.launch("screentrack-report.csv")`, replace `PdfExporter.buildSummaryPdf(...)` with `CsvExporter.buildHourlyCsv(data).toByteArray()`.
- [ ] **Step 2:** Delete `PdfExporter.kt`. Build to confirm no references remain. Commit.

### Task 4.3: Update Krista's form + participant instructions

- [ ] **Step 1:** The Google Form weekly upload switches from PDF-only → CSV (Krista changes the form). Note in `docs/participant-instructions` (Export "7 Days" → attach `.csv`).

---

# PHASE 5 — Ship

### Task 5.1: Version bump, on-device verification, distribute

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode = 6`, `versionName = "1.2.0"` — major feature)

- [ ] **Step 1:** Full release build; confirm no `INTERNET` permission crept in (`aapt dump permissions`).
- [ ] **Step 2:** Install on the physical phone; capture a few minutes; export a CSV; open it and verify hourly rows + 28 emotion score columns look sane.
- [ ] **Step 3:** `firebase appdistribution:distribute` v1.2.0 to `study-participants` with release notes. Commit + push branch.

---

## Self-review notes
- **Spec coverage:** 28-emotion model (P0–P1), face model + mapping (P3), per-emotion score columns + sentiment/content % (P4), DB change (P2), on-device/no-network (global), ship (P5). ✓
- **Biggest risk:** Phase 0 tokenizer embedding (Task 0.4). If it fails, Phase 1 gains a Kotlin BPE task. The plan gates on this deliberately.
- **Accuracy caveat** (spec §6) is a product decision for Krista, tracked separately from this build.
