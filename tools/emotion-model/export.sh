#!/usr/bin/env bash
# Task 0.2: export SamLowe/roberta-base-go_emotions to ONNX and int8-quantize.
set -e
cd "$(dirname "$0")"
source .venv/bin/activate

echo "=== Exporting to ONNX (downloads model on first run) ==="
optimum-cli export onnx \
  --model SamLowe/roberta-base-go_emotions \
  --task text-classification \
  onnx/

echo "=== Dynamic int8 quantization ==="
python - <<'PY'
from onnxruntime.quantization import quantize_dynamic, QuantType
quantize_dynamic("onnx/model.onnx", "onnx/model.int8.onnx", weight_type=QuantType.QInt8)
print("quantized -> onnx/model.int8.onnx")
PY

echo "=== Sizes ==="
ls -lh onnx/model.onnx onnx/model.int8.onnx
