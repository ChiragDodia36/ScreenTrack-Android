# Code-Slimming Plan (ponytail / minimal-code review)

**Goal:** Remove redundant / misleading lines and duplication **without changing any business logic.** Plan only — no implementation.

**Verdict up front:** The codebase is already lean (the recent on-device refactor removed the Hugging Face uploader, `okhttp`, `jpegQuality`, Analytics, Crashlytics). There is **no commented-out dead code, no TODOs, no unused HF remnants**. The items below are the genuine, low-risk cleanups — mostly clarity, not volume.

---

## Item 1 — Rename the "upload" leftovers (clarity, zero logic change) — RECOMMENDED

Nothing is uploaded anymore; the timestamp is really "last analysis/capture." The old name is now misleading.

**Occurrences (~9):**
- `ServiceStatusStore.kt`: `_lastUploadAt`, `lastUploadAt`, `setLastUploadAt`, `KEY_LAST_UPLOAD` (keep the stored pref key string to avoid a data reset, just rename the Kotlin symbols)
- `CaptureViewModel.kt:41`: `lastUploadAt`
- `ScreenCaptureService.kt:177`: `setLastUploadAt(...)`
- `MainActivity.kt:171,321,588,634-635`: `lastUploadAt` + `"Last upload: ..."` UI text
- `MainActivity.kt:469`: `"Upload Error"` UI text

**Action:** rename symbols `lastUploadAt → lastCaptureAt`, `setLastUploadAt → setLastCaptureAt`; change UI strings to `"Last capture:"` / `"Capture Error"`. Keep the SharedPreferences key literal unchanged.
**Risk:** none (pure rename). **Lines saved:** ~0 (clarity, not volume). **Effort:** ~15 min.

## Item 2 — Extract repeated color literals in ChartComponents (dedupe) — RECOMMENDED

`0xFF9CA3AF` appears 5×, and 6 emotion colors appear 2× each as raw hex.

**Action:** hoist to named `private val`s (e.g., `NeutralGrey = Color(0xFF9CA3AF)`) at the top of `ChartComponents.kt` and reference them.
**Risk:** none (same values). **Lines saved:** a few; removes magic-number duplication. **Effort:** ~15 min.

## Item 3 — Duplicate emotion→color mapping across files — OPTIONAL (weigh it)

`ChartComponents.kt:421` (`emotionColor` → Compose `Color`) and `PdfExporter.kt:297` (emotion → Android `Int`) are two separate emotion→color maps.

**Action:** define one canonical `emotion → 0xAARRGGBB Long` map; derive `Color(...)` and the Android `Int` from it in each place.
**Risk:** low, but the two consumers need different types — forcing a shared abstraction could be mild over-engineering. **Recommendation:** only do it if a third consumer appears; otherwise a `// keep in sync with PdfExporter` comment is the YAGNI-correct choice.

## Item 4 — MainActivity.kt is 1506 lines / 27 composables — OPTIONAL, LOW PRIORITY

**Not a line-count problem** — it's organizational. Splitting per-screen files is reorganization, not removal, and it touches core UI (higher risk).
**Recommendation:** defer. Only split if a screen changes substantially. Does not fit "remove extra lines without touching logic."

## Item 5 — Compiler/lint sweep for unused imports & warnings — CHEAP WIN — RECOMMENDED

The release build showed a real warning: `CaptureViewModel.kt:27 ExperimentalCoroutinesApi opt-in` (from `flatMapLatest`).

**Action:** run `./gradlew lint` (or read compile warnings) and remove any unused imports it flags; add the `@OptIn(ExperimentalCoroutinesApi::class)` annotation to silence the known warning.
**Risk:** none. **Effort:** ~15 min.

---

## Suggested order (all low-risk, logic-preserving)
1. Item 5 (lint sweep) — free, surfaces anything else
2. Item 1 (upload → capture rename)
3. Item 2 (color constants)
4. Item 3 only if justified; Item 4 deferred

**Honest bottom line:** this is a *tidy*, not a *teardown*. Expect clarity gains and a handful of lines, not a big reduction — the fat was already removed in the on-device refactor.
