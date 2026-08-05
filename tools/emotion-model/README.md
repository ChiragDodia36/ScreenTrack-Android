# Emotion model conversion (dev tooling)

Converts `SamLowe/roberta-base-go_emotions` to a quantized ONNX model for
on-device inference in the ScreenTrack app. **Dev-only** — not part of the app
build; the app ships only the resulting `.onnx` asset.

## Setup
```bash
cd tools/emotion-model
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## Steps
1. `bash export.sh` — export to ONNX (`onnx/model.onnx`) + int8 quantize (`onnx/model.int8.onnx`).
2. `python verify.py` — sanity-check 28-emotion scores in Python.
3. `python embed_tokenizer.py` — try to embed the tokenizer (raw-text input on device).
4. Copy `onnx/model.int8.onnx` -> `app/src/main/assets/emotion_model.onnx` (and `tokenizer.onnx`).

**Note:** `app/src/main/assets/emotion_model.onnx` is **gitignored** (120 MB > GitHub's
100 MB limit). A fresh clone must run steps 1–4 before building. `tokenizer.onnx`
(1.3 MB) is committed.

## Face model (FER) — Phase 3
`emotion-ferplus-8` (ONNX Model Zoo, 8 emotions, 1x1x64x64 grayscale input).
```bash
curl -sL -o fer/emotion-ferplus-8.onnx \
  https://huggingface.co/onnxmodelzoo/emotion-ferplus-8/resolve/main/emotion-ferplus-8.onnx
python fer/verify_fer.py   # verifies + writes emotion-ferplus-8.int8.onnx (~20MB)
cp fer/emotion-ferplus-8.int8.onnx ../../app/src/main/assets/fer_model.onnx
```
`fer_model.onnx` is **gitignored** — re-download + quantize on a fresh clone.

## Spike findings
- int8 model size: **120 MB** (fp32 was 476 MB) — under the 150 MB gate. ✓
- Python accuracy: excellent — love 0.96, fear 0.91, surprise 0.86, pride/admiration correct. ✓
- tokenizer embeds cleanly (Task 0.4): **YES**, via `onnxruntime-extensions` with `use_fast=False`. Android feeds **raw text** — no Kotlin BPE needed. `tokenizer.onnx` = 1.3 MB. ✓
- Interface: tokenizer input `input_text`; outputs `input_ids`, `attention_mask`; model takes those two.
- on-device latency (Task 0.5): **88–180 ms** per inference on Samsung SM-A176U1 (arm64). Output identical to Python. No OOM. ✓
- Go / No-Go (Task 0.6): **GO.** All gate criteria pass (size 120 MB, latency <200 ms, accurate, no crash).
- Phase 5 note: restrict release ABI to `arm64-v8a` to keep the APK ~145 MB (debug bundles all 4 ABIs = 305 MB).
