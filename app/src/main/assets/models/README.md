# VisionEye — YOLOv8n Model Specifications & Deployment Guide

This directory holds on-device machine learning models for VisionEye object detection.

For the complete, step-by-step export, validation, and troubleshooting guide, see [MODEL_SETUP.md](../../../../MODEL_SETUP.md).

---

## 1. Expected Model File
- **Filename**: `yolov8n.tflite`
- **Location**: `app/src/main/assets/models/yolov8n.tflite`
- **Architecture**: Ultralytics YOLOv8 Nano (Detection)
- **Runtime**: Google LiteRT / TensorFlow Lite on-device mobile interpreter (Float32)

---

## 2. Confirmed Model Contract (NCHW) vs. Deprecated NHWC

### Confirmed Actual Contract (Official LiteRT Export)
- **Input Tensor (1)**: Shape **`[1, 3, 640, 640]`**, Float32, RGB, **NCHW planar layout**, values normalized in `[0.0, 1.0]`.
- **Output Tensor (1)**: Shape **`[1, 84, 8400]`**, Float32, raw output without embedded NMS (`nms=False`).
  - Features (84): 4 bounding box coordinates + 80 COCO classes across 8400 detection anchors.
- **Previous NHWC Assumption (`[1, 640, 640, 3]`) is Rejected**: `ModelValidator` will flag NHWC models with `MODEL_SCHEMA_MISMATCH`. Preprocessing formats data into planar NCHW.

---

## 3. Class Labels Specification

- **File**: `coco_labels.txt` (located in this directory)
- **Source**: Official Ultralytics YOLOv8 COCO dataset configuration (`coco.yaml`)
- **Format**: Newline-delimited text file with 80 class names
- **Indexing**: 0-based index corresponding to output tensor slices `[4 + class_id]`
  - Index 0: `person`
  - Index 1: `bicycle`
  - Index 2: `car`
  - ...
  - Index 56: `chair`
  - Index 57: `couch`
  - ...
  - Index 79: `toothbrush`

Common navigation obstacle classes include: `person` (0), `bicycle` (1), `car` (2), `motorcycle` (3), `bus` (5), `truck` (7), `traffic light` (9), `fire hydrant` (10), `stop sign` (11), `bench` (13), `chair` (56), `couch` (57), `potted plant` (58), `dining table` (60).

---

## 4. Model Export Procedure (Google Colab Recommended)

The current Ultralytics LiteRT export workflow requires Linux x86_64 or macOS. Exporting locally on Windows 11 often fails with ABI/toolchain issues. **Use Google Colab**:

```python
# In Google Colab:
!pip install --upgrade ultralytics
from ultralytics import YOLO

model = YOLO("yolov8n.pt")
model.export(format="tflite", imgsz=640, batch=1, half=False, int8=False, nms=False)
```

Download the exported `.tflite` file, rename it to `yolov8n.tflite`, and place it in this directory:
`app/src/main/assets/models/yolov8n.tflite`

See [MODEL_SETUP.md](../../../../MODEL_SETUP.md) for the full Python validation script and troubleshooting matrix.
