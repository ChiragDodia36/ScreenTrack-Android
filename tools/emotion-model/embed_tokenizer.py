"""Task 0.4: try to build an ONNX tokenizer (raw-text input on device).

If this succeeds we can feed a raw string to the model on Android via
onnxruntime-extensions, avoiding a hand-written BPE tokenizer in Kotlin.
"""
import os
from transformers import AutoTokenizer
from onnxruntime_extensions import gen_processing_models

HERE = os.path.dirname(__file__)
ONNX = os.path.join(HERE, "onnx")

# onnxruntime-extensions needs the "slow" (Python) tokenizer to build the graph.
tok = AutoTokenizer.from_pretrained(ONNX, use_fast=False)
pre, _ = gen_processing_models(tok, pre_kwargs={})
out = os.path.join(ONNX, "tokenizer.onnx")
with open(out, "wb") as f:
    f.write(pre.SerializeToString())
print(f"tokenizer.onnx written -> {out}")
