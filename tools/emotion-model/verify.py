"""Task 0.3: sanity-check the ONNX 28-emotion scores in Python."""
import os
import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

HERE = os.path.dirname(__file__)
ONNX = os.path.join(HERE, "onnx")

# GoEmotions label order (matches SamLowe/roberta-base-go_emotions config id2label)
LABELS = [
    "admiration", "amusement", "anger", "annoyance", "approval", "caring",
    "confusion", "curiosity", "desire", "disappointment", "disapproval",
    "disgust", "embarrassment", "excitement", "fear", "gratitude", "grief",
    "joy", "love", "nervousness", "optimism", "pride", "realization", "relief",
    "remorse", "sadness", "surprise", "neutral",
]

tok = AutoTokenizer.from_pretrained(ONNX)
sess = ort.InferenceSession(os.path.join(ONNX, "model.int8.onnx"))
input_names = {i.name for i in sess.get_inputs()}

def sigmoid(x):
    return 1.0 / (1.0 + np.exp(-x))

for text in ["I love this so much!", "this is terrifying", "meh, whatever", "wow I did not expect that"]:
    enc = tok(text, return_tensors="np", truncation=True, max_length=64)
    feeds = {k: v for k, v in enc.items() if k in input_names}
    logits = sess.run(None, feeds)[0][0]
    probs = sigmoid(logits)
    top = sorted(zip(LABELS, probs), key=lambda x: -x[1])[:3]
    print(f"{text!r:40} -> {[(l, round(float(p), 2)) for l, p in top]}")
