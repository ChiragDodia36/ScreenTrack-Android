# 27-Emotion On-Device Model + Hourly CSV — Design Spec

**Date:** 2026-08-03
**Status:** Draft for review (no code yet)
**Context:** Professor wants (a) a CSV export (replacing the PDF) with **one row per hour** and **a column per emotion / sentiment / content category**, and (b) far more emotional detail than the current keyword classifier can give. Decision made: move to a real on-device ML emotion model (GoEmotions-27) plus a facial-emotion model, accepting the size/battery cost.

---

## 1. Goals
- Replace keyword emotion classifier with a **real on-device ML model** classifying **GoEmotions 27 emotions (+ neutral)**, outputting a **probability score per emotion**.
- Add a **facial-emotion (FER) model** as a second signal when a face is visible.
- Replace the PDF export with an **hourly CSV**: one row per hour, a column per emotion (score), per sentiment bucket, and per content category.
- Keep everything **on-device** (no network) — preserves the IRB/privacy posture.

## 2. Models

### 2.1 Text emotion (primary)
- **Model:** pre-trained GoEmotions classifier — start with `SamLowe/roberta-base-go_emotions` (28 labels, per-label probability). ModernBERT-GoEmotions as an upgrade option.
- **We do NOT train from scratch** — use the pre-trained model. Optional future fine-tuning needs labeled screen-text data we don't have.
- **On-device:** convert to ONNX (ONNX Runtime Mobile) or TFLite; bundle the tokenizer + vocab; quantize (int8/fp16) to control size.
- **Output:** map of `emotion -> score (0..1)` for the 28 labels.

### 2.2 Facial emotion (secondary)
- Small on-device FER model (7 labels: happy, sad, angry, fear, surprise, disgust, neutral), run only when ML Kit detects a face.
- Its 7 labels **map into** the GoEmotions set (happy→joy, etc.); finer GoEmotions emotions come from text only.

### 2.3 Combine
- Per capture: text scores (27) + optional face scores (mapped into the overlapping basic emotions), blended on the overlapping labels (weighted, e.g. text 0.6 / face 0.4). Non-overlapping emotions = text only.
- If neither text nor face gives signal → all scores 0 / "undetermined".

## 3. Data model change
- Per capture, store the **per-emotion score map** (serialized JSON string in one Room column) rather than 28 separate columns — keeps the schema clean. Keep existing `sentiment`, `contentType`, `contentConfidence`.
- **Destructive DB migration** (wipe on update) — already the agreed strategy; no real participant data yet.

## 4. Hourly CSV format
One row per hour that has captures. Columns:
```
Date, Hour,
  <27 GoEmotions columns>,          ← avg model score for that emotion over the hour
  positive, negative, neutral,      ← % of hour in each sentiment bucket
  <content category columns>, general   ← % of hour in each content category
```
- **Emotion cell = average model score** for that emotion across the hour's captures (per the "score in each column" decision).
- **Sentiment/content cells = % of the hour** (sum to 100 within each group).
- No daily total row. RFC-4180 quoting.

## 5. Privacy / IRB
- Still fully on-device, no network. Faces analyzed on-device, never stored; call auto-pause already excludes private video calls.
- IRB note: emotion analysis now uses an ML model + facial-emotion reading — disclose to Krista/IRB.

## 6. Realistic accuracy (must communicate to Krista)
- GoEmotions fine-grained detection: reliable on common emotions (joy/anger), weak on rare ones (grief/pride/nostalgia). Benchmarks vary widely (~0.45–0.85 F1 depending on emotion and eval).
- Input is OCR'd **screen fragments** (out of domain vs the Reddit training text) → real accuracy lower than benchmarks.
- Face model only fires when a face is on screen (a minority of screen time).
- **Net:** far better than keywords, genuinely useful, but not research-perfect on rare emotions. Krista should confirm this bar is acceptable.

## 7. Costs
- App size ~150–280 MB (two models). Distributed via Firebase (no Play size limit issue).
- More battery/CPU/RAM; slower per capture. Accepted.
- Build effort: model conversion + tokenizer + inference wiring + DB change + CSV + tests. Multi-day, multi-task.

## 8. Open decisions before the implementation plan
1. **Exact taxonomy in the CSV:** all 28 GoEmotions columns, or a chosen subset (~16)? (28 recommended for completeness.)
2. **FER model choice** + confirm the 7→27 mapping table.
3. **Text/face blend weights.**
4. **Deployment runtime:** ONNX Runtime Mobile vs TFLite (affects conversion work).
5. **Krista sign-off** on the accuracy caveat (§6) and the emotion-model change.

## 9. Risks
- Model size may hit device limits on low-end phones (RAM → app killed). Mitigate with a smaller/distilled model.
- Tokenizer/conversion is the fiddliest part (BERT/RoBERTa WordPiece/BPE on-device).
- Out-of-domain accuracy may disappoint; keep a confidence threshold + "undetermined".
