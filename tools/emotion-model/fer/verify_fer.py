"""Phase 3.1: verify FER+ loads, check I/O, quantize to int8."""
import os
import numpy as np
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType

HERE = os.path.dirname(__file__)
SRC = os.path.join(HERE, "emotion-ferplus-8.onnx")
INT8 = os.path.join(HERE, "emotion-ferplus-8.int8.onnx")

LABELS = ["neutral", "happiness", "surprise", "sadness", "anger", "disgust", "fear", "contempt"]

sess = ort.InferenceSession(SRC)
inp = sess.get_inputs()[0]
out = sess.get_outputs()[0]
print("input :", inp.name, inp.shape)
print("output:", out.name, out.shape)

def softmax(x):
    e = np.exp(x - np.max(x))
    return e / e.sum()

# dummy 64x64 grayscale input just to confirm it runs and shapes line up
x = np.random.rand(1, 1, 64, 64).astype(np.float32) * 255.0
logits = sess.run(None, {inp.name: x})[0][0]
print("8-emotion softmax on random input:", [(l, round(float(p), 2)) for l, p in zip(LABELS, softmax(logits))])

quantize_dynamic(SRC, INT8, weight_type=QuantType.QInt8)
print("quantized size:", round(os.path.getsize(INT8) / 1_000_000, 1), "MB")
