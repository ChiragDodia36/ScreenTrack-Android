"""Task 0.4 (cont): end-to-end check — raw text -> tokenizer.onnx -> model -> emotions.
Mirrors exactly what the Android harness will do (ORT + ORT-Extensions)."""
import os
import numpy as np
import onnxruntime as ort
from onnxruntime_extensions import get_library_path

HERE = os.path.dirname(__file__)
ONNX = os.path.join(HERE, "onnx")
LABELS = [
    "admiration", "amusement", "anger", "annoyance", "approval", "caring",
    "confusion", "curiosity", "desire", "disappointment", "disapproval",
    "disgust", "embarrassment", "excitement", "fear", "gratitude", "grief",
    "joy", "love", "nervousness", "optimism", "pride", "realization", "relief",
    "remorse", "sadness", "surprise", "neutral",
]

so = ort.SessionOptions()
so.register_custom_ops_library(get_library_path())
tok = ort.InferenceSession(os.path.join(ONNX, "tokenizer.onnx"), so)
mdl = ort.InferenceSession(os.path.join(ONNX, "model.int8.onnx"))

print("tokenizer inputs :", [i.name for i in tok.get_inputs()])
print("tokenizer outputs:", [o.name for o in tok.get_outputs()])
print("model inputs     :", [i.name for i in mdl.get_inputs()])

def sigmoid(x):
    return 1.0 / (1.0 + np.exp(-x))

for text in ["I love this so much!", "this is terrifying", "so proud of you"]:
    tout = tok.run(None, {tok.get_inputs()[0].name: np.array([text])})
    names = [o.name for o in tok.get_outputs()]
    ids = tout[names.index("input_ids")].astype(np.int64).reshape(1, -1)
    mask = tout[names.index("attention_mask")].astype(np.int64).reshape(1, -1)
    feeds = {"input_ids": ids, "attention_mask": mask}
    feeds = {k: v for k, v in feeds.items() if k in [i.name for i in mdl.get_inputs()]}
    logits = mdl.run(None, feeds)[0][0]
    top = sorted(zip(LABELS, sigmoid(logits)), key=lambda x: -x[1])[:3]
    print(f"{text!r:28} -> {[(l, round(float(p), 2)) for l, p in top]}")
