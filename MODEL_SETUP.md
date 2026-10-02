# VisionEye — YOLOv8n Model Setup & LiteRT Export Guide

This guide documents the official acquisition, LiteRT export, tensor validation, and deployment workflow for the **YOLOv8n object detector** used in VisionEye's `NAVIGATE` mode.

---

## 1. Overview & Export Terminology

- **Runtime**: **Google LiteRT** (formerly known as TensorFlow Lite / TFLite).
- **Export Format**: Ultralytics exports via `format="tflite"`, producing a standard LiteRT flatbuffer with the **`.tflite`** file extension.
- **Target Filename**: `yolov8n.tflite`
- **Target Android Location**: `app/src/main/assets/models/yolov8n.tflite`
- **Associated Labels**: `app/src/main/assets/models/coco_labels.txt` (80 COCO classes)

---

## 2. Platform Compatibility & Why Google Colab

> [!IMPORTANT]
> **Do not attempt local export on Windows 11.**
> The current Ultralytics LiteRT export pipeline requires dependencies (such as Linux-native `tensorflow` / `ai-edge-litert` packages) that are officially supported only on **Linux x86_64** and **macOS**. Attempting local export on Windows often fails due to toolchain or ABI conversion errors.
>
> **Recommended Workflow**: Use a free **Google Colab** notebook (Ubuntu Linux x86_64) to perform the export and download the resulting `.tflite` file in under two minutes.

---

## 3. Model Contract: Confirmed Actual Contract vs. Deprecated NHWC Assumption

### Confirmed Actual Model Contract (From Colab Export Inspection)
The official YOLOv8n LiteRT export produces the following confirmed contract:

| Property | Confirmed Actual Contract |
| :--- | :--- |
| **Source Model** | `yolov8n.pt` (official Ultralytics pre-trained detection weights) |
| **Task** | `detect` (Object detection — bounding boxes + 80 COCO classes) |
| **Input Shape** | **`[1, 3, 640, 640]`** (Batch 1, Channels 3, Height 640, Width 640) |
| **Input Layout** | **`NCHW` (Planar)**: All Red floats, followed by all Green floats, followed by all Blue floats |
| **Color Order** | **RGB** (normalized to `[0.0, 1.0]`) |
| **Input Datatype** | `Float32` |
| **Output Tensor Count** | Exactly `1` raw prediction tensor |
| **Output Shape** | **`[1, 84, 8400]`** (84 features × 8400 candidate detection anchors) |
| **Output Datatype** | `Float32` |
| **NMS Mode** | **External / Raw** (`nms=False`). VisionEye runs internal class-aware NMS |

### Deprecated Previous Assumption (Now Rejected)
> [!WARNING]
> **Previous NHWC Assumption Deprecated**: Early development assumed an interleaved NHWC input shape `[1, 640, 640, 3]`.
> The actual exported LiteRT model requires planar NCHW `[1, 3, 640, 640]`. VisionEye's `ModelValidator` will explicitly reject models with `[1, 640, 640, 3]` with `MODEL_SCHEMA_MISMATCH`. Preprocessing has been updated to pack pixels in strict planar NCHW order.

---

## 4. Google Colab Export Workflow

Open a new notebook on [Google Colab](https://colab.research.google.com/) and run the following cells sequentially:

### Cell 1: Install Official Ultralytics Package
```python
!pip install --upgrade ultralytics
```

### Cell 2: Export Official YOLOv8n to LiteRT (.tflite)
```python
from ultralytics import YOLO

# 1. Load official YOLOv8n detection model
model = YOLO("yolov8n.pt")

# 2. Export to LiteRT format with FP32 precision and raw external NMS
export_path = model.export(
    format="tflite",
    imgsz=640,
    batch=1,
    half=False,      # FP32 precision
    int8=False,      # Do not quantize to INT8
    nms=False        # Raw output; do NOT use embedded NMS
)

print(f"Export successful! File created at: {export_path}")
```

### Cell 3: Inspect & Validate Actual Model Tensors
Run this inspection script in Colab before downloading to verify the actual contract:

```python
import glob
import tensorflow as tf

# Locate exported .tflite file
tflite_candidates = glob.glob("**/*float32*.tflite", recursive=True) or glob.glob("**/*.tflite", recursive=True)
if not tflite_candidates:
    raise FileNotFoundError("No .tflite file found after export!")

model_path = tflite_candidates[0]
print(f"Inspecting: {model_path}")

interpreter = tf.lite.Interpreter(model_path=model_path)
interpreter.allocate_tensors()

inputs = interpreter.get_input_details()
outputs = interpreter.get_output_details()

print("\n" + "=" * 50)
print("ACTUAL MODEL CONTRACT INSPECTION")
print("=" * 50)
print(f"Input Tensors ({len(inputs)}):")
for i, tensor in enumerate(inputs):
    print(f"  [{i}] name='{tensor['name']}', shape={tensor['shape']}, dtype={tensor['dtype'].__name__}")

print(f"\nOutput Tensors ({len(outputs)}):")
for i, tensor in enumerate(outputs):
    print(f"  [{i}] name='{tensor['name']}', shape={tensor['shape']}, dtype={tensor['dtype'].__name__}")

# Contract assertions
assert len(inputs) == 1, f"Expected 1 input tensor, got {len(inputs)}"
assert list(inputs[0]['shape']) == [1, 640, 640, 3], f"Expected [1, 640, 640, 3], got {inputs[0]['shape']}"
assert inputs[0]['dtype'] == tf.float32, f"Expected float32 input, got {inputs[0]['dtype']}"

assert len(outputs) == 1, f"Expected 1 output tensor, got {len(outputs)} (Check: did you pass nms=True?)"
actual_out_shape = list(outputs[0]['shape'])
assert (actual_out_shape == [1, 84, 8400] or actual_out_shape == [1, 8400, 84]), \
    f"Expected [1, 84, 8400] or [1, 8400, 84], got {actual_out_shape}"
assert outputs[0]['dtype'] == tf.float32, f"Expected float32 output, got {outputs[0]['dtype']}"

print("=" * 50)
print("VALIDATION STATUS: PASS (Model conforms to VisionEye requirements)")
print("=" * 50)
```

### Cell 4: Download the File
```python
from google.colab import files
files.download(model_path)
```

---

## 5. Deployment into VisionEye

1. Rename the downloaded file to:
   ```
   yolov8n.tflite
   ```

2. Place the file into the Android project assets directory:
   ```
   app/src/main/assets/models/yolov8n.tflite
   ```

3. Verify file locations in your workspace:
   - `app/src/main/assets/models/yolov8n.tflite` (Model binary)
   - `app/src/main/assets/models/coco_labels.txt` (80 COCO class labels)

4. In **Android Studio**:
   - Rebuild the app (`Build` → `Make Project`).
   - Run on the physical POCO F5 (`Shift + F10`).
   - In `NAVIGATE` mode, the developer HUD should display:
     ```
     DEV: YOLOv8n STATUS          READY (<latency> ms)
     Objects (N): person 92%, chair 85%
     ```

---

## 6. Troubleshooting Guide

| Issue / Diagnostic State | Cause | Solution |
| :--- | :--- | :--- |
| **Export Dependency Failures** (on local Windows machine) | Windows 11 lacks supported build tools/ABI for Ultralytics LiteRT export. | Export on **Google Colab** (Linux x86_64) using the provided script. |
| **`MODEL_MISSING`** | `yolov8n.tflite` is not in `app/src/main/assets/models/` or was misspelled. | Confirm file is placed at `app/src/main/assets/models/yolov8n.tflite`. Ensure Gradle asset compression is disabled (`noCompress += "tflite"`). |
| **`MODEL_SCHEMA_MISMATCH`** (Embedded NMS model detected) | Export command included `nms=True`, generating 4 output tensors (`[1, 300, 4]`, etc.). | Re-export with `nms=False`. VisionEye requires the single raw output tensor (`[1, 84, 8400]` or `[1, 8400, 84]`). |
| **`MODEL_SCHEMA_MISMATCH`** (Wrong input dimensions) | Model was exported with a resolution other than 640 (e.g. `imgsz=320`). | Re-export with `imgsz=640`. VisionEye's preprocessor formats frames to 640×640. |
| **`MODEL_SCHEMA_MISMATCH`** (Wrong input/output datatype) | Model was quantized to INT8 (`int8=True`) without quantization scale/zero-point support. | Re-export as FP32 (`int8=False`, `half=False`). VisionEye preprocessor passes normalized Float32 values. |
| **`MODEL_LOAD_ERROR`** | Corrupted `.tflite` file or unsupported TensorFlow Lite operator version. | Check file size (~12 MB). Re-export cleanly from official `yolov8n.pt`. |
| **`INFERENCE_ERROR`** | Native runtime error during `interpreter.run(...)`. | Inspect Logcat tagged with `YoloV8Detector` for underlying runtime details. |
