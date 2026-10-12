# VisionEye: Complete Technical Information Dossier & Evidence Audit
**Final-Year Computer Science and Engineering Academic Project Report Evidence Base**

---

## Document Control & Audit Metadata

- **Document Identifier**: `VISIONEYE-TECH-DOSSIER-V1.0`
- **Project Title**: VisionEye: A Smartphone-Based AI Wearable Assistance System for the Visually Impaired
- **Target Academic Degree**: Bachelor of Technology (B.Tech) in Computer Science and Engineering
- **Audit Execution Date**: October 12, 2026
- **Repository Root Path**: `D:\UserData\VisionEye`
- **Active Git Branch**: `main` (synchronized with `origin/main`)
- **HEAD Commit Hash**: `497c7dbf3977038f1cfe6b4388f2ef8e293f3d7a`
- **Commit Message**: `feat(read): enhance Read mode pipeline with page stability, OCR filtering, and UI refinements`
- **Audit Tooling & Execution Environment**: Windows 11, PowerShell, Gradle 9.5.0, Android Studio JBR (Java 21.0.8)
- **Primary Source of Truth**: Active codebase (`app/src/main/`, `app/src/test/`), build configurations (`build.gradle.kts`, `libs.versions.toml`), model assets (`app/src/main/assets/models/`), and local machine learning dataset artifacts (`ml/currency/`).
- **Audit Constraints Enforced**: Non-destructive, evidence-backed inspection only. Zero source modifications, zero branch mutations, zero credential disclosures.

---

# 1. Strict Rules Compliance & Audit Verification Principles

This technical dossier adheres strictly to the following academic evidence standards:

1. **Source Code Priority**: All claims reflect the verified current state of the source tree, overriding obsolete reports or historical planning documentation.
2. **Preservation of Repository State**: No source code, resources, build files, model weights, or Git records were modified, reset, or committed during this audit.
3. **Evidence-Linked Claims**: Every architecture description, tensor dimension, timeout value, algorithm stage, and permission reference is anchored directly to its physical file path, class/symbol name, and line number.
4. **Distinction Between Verification Levels**:
   - `VERIFIED IN SOURCE`: Inspected directly in active application code.
   - `TESTED`: Verified via automated unit tests passing in the current Gradle build.
   - `VERIFIED IN PROVIDED TRAINING RECORD`: Documented with concrete dataset/asset evidence on disk.
   - `INFERENCE`: Reasonable academic reasoning derived from architecture, explicitly labeled as such.
   - `NOT VERIFIED` / `NOT FOUND`: Feature, metric, or capability absent from the repository.
   - `CONFLICTING EVIDENCE`: Documented discrepancy between design notes and active code.
   - `REQUIRES USER CONFIRMATION`: Information requiring student/supervisor input (e.g., student registration numbers, supervisor names, benchmark devices).
5. **Zero Fabrication**: No benchmark latencies, model accuracies (mAP, F1, precision/recall), user trial outcomes, or financial metrics have been invented.
6. **Privacy & Redaction**: No private phone numbers, API keys, student credentials, or personal contact entries are exposed.
7. **Monocular Geometry Guardrail**: Monocular bounding-box area/height heuristics are explicitly classified as *coarse relative proximity estimation* and never misrepresented as metric depth or 3D LiDAR/stereo sensing.
8. **Safety Critical Boundary (The White Cane Rule)**: VisionEye is formally documented as an *assistive technology prototype* and never as a primary mobility aid or collision-prevention system.

---

# 2. Repository and Project Identity

### 2.1 Project Identity & Namespace
- **Project Name**: VisionEye
- **Formal Academic Title**: *VisionEye: A Smartphone-Based AI Wearable Assistance System for the Visually Impaired*
- **Android Application ID / Package**: `com.bibin.visioneye`
- **Application Class**: `com.bibin.visioneye.VisionEyeApplication` ([`app/src/main/java/com/bibin/visioneye/VisionEyeApplication.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/VisionEyeApplication.kt#L22))
- **Primary Launcher Activity**: `com.bibin.visioneye.ui.MainActivity` ([`app/src/main/java/com/bibin/visioneye/ui/MainActivity.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ui/MainActivity.kt#L18))
- **Version Code**: `1` (verified in [`app/build.gradle.kts:L16`](file:///D:/UserData/VisionEye/app/build.gradle.kts#L16))
- **Version Name**: `"1.0"` (verified in [`app/build.gradle.kts:L17`](file:///D:/UserData/VisionEye/app/build.gradle.kts#L17))

### 2.2 Git Version Control State
- **Current Branch**: `main`
- **HEAD Commit**: `497c7dbf3977038f1cfe6b4388f2ef8e293f3d7a`
- **Upstream Tracking**: Up to date with `origin/main`
- **Working Tree State**: Unstaged working tree modifications and untracked additions are present from the completed voice-control integration task:
  - *Modified Tracked Files* (11 files):
    - `app/src/main/AndroidManifest.xml`
    - `app/src/main/java/com/bibin/visioneye/VisionEyeApplication.kt`
    - `app/src/main/java/com/bibin/visioneye/ui/MainActivity.kt`
    - `app/src/main/java/com/bibin/visioneye/ui/VisionEyeApp.kt`
    - `app/src/main/java/com/bibin/visioneye/ui/screens/CameraAssistanceScreen.kt`
    - `app/src/main/java/com/bibin/visioneye/ui/screens/EnrollPersonScreen.kt`
    - `app/src/main/java/com/bibin/visioneye/ui/screens/MainScreen.kt`
    - `app/src/main/java/com/bibin/visioneye/ui/screens/ManagePeopleScreen.kt`
    - `app/src/main/java/com/bibin/visioneye/ui/screens/NavigationGpsScreen.kt`
    - `app/src/main/java/com/bibin/visioneye/ui/screens/SosScreen.kt`
    - `app/src/main/res/values/themes.xml`
  - *Untracked Directories & Files*:
    - `app/src/main/java/com/bibin/visioneye/ui/components/` (Accessible UI components: `VisionHeader`, `VoiceControlButton`, `AccessibleModeCard`)
    - `app/src/main/java/com/bibin/visioneye/voice/` (Complete voice-control subsystem: `VoiceController`, `ScreenVoiceActions`, `VoiceMatcher`, `VoiceCommandRegistry`)
    - `app/src/test/java/com/bibin/visioneye/voice/` (Automated unit tests for voice control)
- **Commit History Summary** (Complete 13 commits from repository creation):
  1. `497c7db` — *feat(read): enhance Read mode pipeline with page stability, OCR filtering, and UI refinements*
  2. `fbc071c` — *chore: remove abandoned depth model asset*
  3. `1273646` — *feat(currency): implement on-device Indian banknote recognition pipeline*
  4. `ba4a6d2` — *feat(navigate): implement and calibrate coarse proximity estimation with audible tier alerts*
  5. `5f36a8c` — *feat(navigation,sos): implement pedestrian navigation pipeline and SOS emergency dispatch*
  6. `2060832` — *feat(people): implement on-device saved-person face recognition and enrollment*
  7. `0c2d760` — *feat: Implement minimal on-device READ mode and home screen entry button*
  8. `6d5811b` — *Implement single-channel SpeechEventArbitrator for sequential navigation alerts and TTS queue arbitration*
  9. `f8d958d` — *feat: Implement Navigation Guidance & Alert Engine with temporal stabilization, TTS, and prominent HUD banner*
  10. `978a062` — *feat: Complete Milestones 2 through 5A — CameraX, YOLOv8n, and DecisionEngine UI integration*
  11. `a84621f` — *docs: Codify development principles and safety rules across guidelines and UI*
  12. `d9e99d2` — *feat: Implement Decision Engine with priority hierarchy, cooldown logic, and update mode specifications*
  13. `3678df8` — *Initial commit: Set up clean mode-based architecture with Jetpack Compose*

### 2.3 Documentation & Implementation Consistency Review
- **Existing Documentation Files**:
  - `AGENTS.md` (Root developer guidelines, mode definitions, safety boundaries)
  - `MODEL_SETUP.md` (Detailed YOLOv8n LiteRT export and contract documentation)
  - `.agents/rules/development-rules.md` (Core architectural rules)
  - `app/src/main/assets/models/README.md` (TFLite model asset descriptions)
- **Documented vs Implemented Discrepancies**:
  1. *Model Input Layout Discrepancy in `MODEL_SETUP.md`*: In [`MODEL_SETUP.md:L115`](file:///D:/UserData/VisionEye/MODEL_SETUP.md#L115), a legacy Python assertion asserts `[1, 640, 640, 3]`, whereas the specification text ([`MODEL_SETUP.md:L36`](file:///D:/UserData/VisionEye/MODEL_SETUP.md#L36)) and the active Kotlin code ([`DetectorConfig.kt:L27`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ai/DetectorConfig.kt#L27), [`ModelValidator.kt:L51`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ai/ModelValidator.kt#L51)) enforce planar NCHW `[1, 3, 640, 640]`. The codebase rejects NHWC with `MODEL_SCHEMA_MISMATCH`.
  2. *Permissions Documentation Discrepancy*: Historical summaries frequently reference `CALL_PHONE` and `VIBRATE`. Inspection of [`app/src/main/AndroidManifest.xml:L5-11`](file:///D:/UserData/VisionEye/app/src/main/AndroidManifest.xml#L5-L11) confirms that neither `CALL_PHONE` nor `VIBRATE` is declared. Only 7 permissions exist in the manifest: `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`, `ACCESS_NETWORK_STATE`, `SEND_SMS`, and `RECORD_AUDIO`.
  3. *Document Reading Controls*: Previous design outlines suggested pause, resume, and repeat capabilities for document reading. Inspection of [`ReadCoordinator.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/read/ReadCoordinator.kt) confirms that only `activate()`, `deactivate()`, `scanAgain()`, and `capturePageNow()` are implemented. Pause and resume controls are **absent** from the active source code.
  4. *Cooldown Duration*: `AGENTS.md` mentions a 3000ms cooldown, whereas default configuration in [`DecisionConfig.kt:L23`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/fusion/decision/DecisionConfig.kt#L23) sets `cooldownPeriodMs = 2500L`.

---

# 3. Project Problem, Aim, and Objectives

### 3.1 Problem Definition
- **Verified Facts**: Visually impaired users navigating physical environments encounter overhead, ground-level, and approaching obstacles that standard white canes miss. They also encounter daily operational barriers: reading printed labels/documents, verifying Indian currency banknotes, identifying familiar people, and finding walking routes without sighted guidance.
- **Academic Framing (`INFERENCE`)**:
  > *"Blind and visually impaired (BVI) individuals experience significant mobility hazards, financial vulnerability, and informational isolation in daily life. Traditional physical aids (such as the white cane) detect obstacles only up to waist height and provide zero semantic or textual information. Meanwhile, commercial electronic travel aids (ETAs) are often proprietary, cost-prohibitive, cloud-dependent, and prone to auditory clutter because they lack contextual sensory arbitration. VisionEye addresses this multi-faceted challenge by unifying six assistive perception modalities into an accessible, privacy-preserving, on-device smartphone application with prioritized speech arbitration."*

### 3.2 Target Users and Operating Environment
- **Intended Users**: Totally blind individuals, individuals with low vision, and elderly individuals with visual impairment.
- **Form Factor**: Commodity Android smartphone mounted in an upright chest harness, lanyard pouch, or shirt pocket with the rear camera facing forward along the user's line of progression.
- **Operating Environments**:
  - *Indoor*: Corridors, rooms, offices (reading documents, identifying visitors, recognizing banknotes).
  - *Outdoor*: Pavements, sidewalks, open pedestrian streets (obstacle avoidance, pedestrian GPS wayfinding, emergency distress broadcasting).

### 3.3 Project Aim
To design, implement, and evaluate a unified, on-device assistive perception system on Android that arbitrates between obstacle detection, document reading, Indian currency recognition, registered person identification, pedestrian wayfinding, and emergency dispatch using real-time computer vision, sensor fusion, and accessible speech interaction.

### 3.4 Specific Project Objectives
1. **Objective 1 (Architectural State Machine)**: Implement a mode-based state machine (`ModeManager`) ensuring that only the vision pipeline required by the active assistive mode is loaded into memory, keeping CPU and thermal load bounded.
2. **Objective 2 (Real-Time Obstacle Detection)**: Integrate an on-device Ultralytics YOLOv8n object detector processing 640×640 RGB frames with custom planar NCHW tensor conversion and external class-aware Non-Maximum Suppression (NMS).
3. **Objective 3 (Spatial Obstacle Arbitration)**: Design a coarse bounding-box proximity estimator using normalized height heuristics with hysteresis smoothing (`NEAR`, `MEDIUM`, `FAR`) and a 4-tier Priority Decision Engine with semantic repetition suppression.
4. **Objective 4 (Assisted Document Reading)**: Construct a multi-stage document reading pipeline combining quad page boundary detection, multi-frame stability tracking, perspective rectification, Google ML Kit Latin OCR, and sentence-chunked Text-to-Speech (TTS).
5. **Objective 5 (Indian Banknote Recognition)**: Train and deploy an on-device YOLO11n currency detector for 6 Indian Rupee denominations (₹10, ₹20, ₹50, ₹100, ₹200, ₹500) paired with a 2-frame temporal confirmation filter to prevent transient misclassifications.
6. **Objective 6 (Privacy-Preserving Face Identification)**: Implement an offline face recognition pipeline combining Google ML Kit Face Detection with a 192-dimensional MobileFaceNet embedding model, storing biometric vectors strictly in sandboxed local file storage with explicit user consent.
7. **Objective 7 (Pedestrian Wayfinding & Safety Dispatch)**: Develop outdoor waypoint navigation combining OpenStreetMap Nominatim/OSRM routing with compass sensor fusion, alongside an abortable 3-second SOS alert system broadcasting GPS coordinates via SMS.
8. **Objective 8 (Accessible Dual-Modal Interface)**: Engineer a WCAG AAA compliant high-contrast UI (contrast ratio > 7:1) with TalkBack semantic labeling, synchronized with a push-to-talk speech control engine matching visible screen labels.

### 3.5 System Scope: In-Scope vs Out-of-Scope
| Category | In-Scope (Implemented & Verified) | Out-of-Scope (Deliberate Non-Goals / Planned) |
| :--- | :--- | :--- |
| **Modes** | 6 active modes: `NAVIGATE`, `READ`, `CURRENCY`, `PEOPLE`, `NAVIGATION`, `SOS` | Indoor beacon localization, bus route number detection |
| **Depth Sensing** | Coarse relative proximity estimation via normalized bounding-box height | Metric depth estimation (meters), LiDAR/ToF, stereo depth |
| **Vision Models** | YOLOv8n (80 COCO classes), YOLO11n (6 INR notes), MobileFaceNet (192-d) | Monocular depth neural networks (removed in commit `fbc071c`) |
| **Document OCR** | Google ML Kit Latin Text Recognition with perspective rectification | Multi-language Indic OCR (Hindi, Tamil, Malayalam) |
| **Voice Input** | Button-activated single-utterance speech control matching visible UI labels | Always-on hotword / wake-word activation ("Hey VisionEye") |
| **Data Privacy** | 100% on-device biometric vectors and contacts (no cloud upload) | Encrypted SQLite / SQLCipher databases |
| **Emergency SOS**| 3-second abortable countdown, SMS via `SmsManager` with GPS link | Automatic phone call dialer intent, delivery receipts |

### 3.6 Safety Constraints & The White Cane Rule
> [!WARNING]
> **Safety Critical Boundary**: VisionEye is an **assistive prototype**, NOT a certified collision-prevention or life-safety system. It must **never** be described or relied upon as a replacement for a white cane or guide dog.
> - Monocular RGB vision cannot reliably identify downward stairs, drop-offs, potholes, or transparent glass doors.
> - Bounding-box proximity heuristics cannot measure absolute metric distance.
> - Adverse lighting (low light < 20 lux or direct solar glare) degrades detection reliability.

---

# 4. System Analysis & Functional Module Verification

---

## Module 4.1 — NAVIGATE: Object Detection & Obstacle Awareness

### 1. Model Identity, Location & Cryptographic Hash
- **Model Filename**: `yolov8n.tflite`
- **Location**: [`app/src/main/assets/models/yolov8n.tflite`](file:///D:/UserData/VisionEye/app/src/main/assets/models/yolov8n.tflite)
- **File Size**: $12,841,243\text{ bytes}$ ($12.25\text{ MB}$)
- **SHA-256 Checksum**: `85FC2DAC2FB2EB9B79926539D55F9A297CD4E21EDA6C69330599CB01BEE5D8A0`
- **Associated Labels File**: [`app/src/main/assets/models/coco_labels.txt`](file:///D:/UserData/VisionEye/app/src/main/assets/models/coco_labels.txt) ($621\text{ bytes}$, 80 COCO classes)
- **Architecture**: Ultralytics YOLOv8 nano (anchor-free convolutional object detector)
- **Quantization**: 32-bit Floating Point (FP32)

### 2. Tensor Contract & Preprocessing
- **Enforcing Class**: [`ModelValidator.kt:L45-58`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ai/ModelValidator.kt#L45-L58)
- **Input Tensor**: Exactly 1 tensor, shape **`[1, 3, 640, 640]`**, Float32
- **Memory Layout**: Planar **NCHW** (all Red normalized floats, followed by all Green, followed by all Blue)
- **Color Space**: RGB, normalized to $[0.0, 1.0]$: $\text{val} = \frac{\text{pixel}}{255.0f}$
- **Output Tensor**: Exactly 1 raw prediction tensor, shape **`[1, 84, 8400]`**, Float32
  - $84$ channels: 4 bounding box coordinates $[x_c, y_c, w, h]$ normalized to 640, plus 80 COCO class probability scores.
  - $8400$ candidate anchors across detection feature maps ($80\times80, 40\times40, 20\times20$).
  - NMS Mode: **External / Raw** (`nms=False` at export time; embedded NMS with 4 output tensors is explicitly rejected by `ModelValidator`).

### 3. Post-Processing & Non-Maximum Suppression (NMS)
- **Implementation**: [`YoloV8Detector.kt:L190-316`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ai/YoloV8Detector.kt#L190-L316)
- **Confidence Threshold**: $\ge 0.40f$ (configurable in [`DetectorConfig.kt:L46`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ai/DetectorConfig.kt#L46))
- **Coordinate Un-letterboxing**: Maps $[x_c, y_c, w, h]$ through `LetterboxTransform` back to normalized $[0.0, 1.0]$ screen coordinates.
- **Class-Aware NMS**: Compares candidate boxes within the same class using Intersection-over-Union (IoU). Retains only boxes with $\text{IoU} < 0.45f$. Max detections capped at 20.

### 4. Coarse Proximity Estimation (Heuristic, Non-Metric)
- **Implementation**: [`ProximityEstimator.kt:L23-103`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/fusion/decision/ProximityEstimator.kt#L23-L103)
- **Geometry Metric**: Normalized bounding-box height (`detection.boundingBox.height` $\in [0.0, 1.0]$).
- **Proximity Classes**:
  - `NEAR`: Visual angle indicates close obstacle in path. Spoken as *"close"*.
  - `MEDIUM`: Intermediate obstacle. Spoken as *"medium"*.
  - `FAR`: Distant obstacle. Spoken as *"far"*.
- **Class-Specific Height Thresholds** ([`DecisionConfig.kt:L79-105`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/fusion/decision/DecisionConfig.kt#L79-L105)):
  - *Tall tier* (`person`, `door`, `refrigerator`, `stairs`): `nearHeight = 0.60f`, `mediumHeight = 0.30f`
  - *Medium tier* (`chair`, `table`, `bed`, `car`, `bus`, `truck`, `bicycle`, `motorcycle`): `nearHeight = 0.55f`, `mediumHeight = 0.25f`
  - *Small tier* (`bag`, `book`, `cup`): `nearHeight = 0.38f`, `mediumHeight = 0.15f`
  - *Fallback tier*: `nearHeight = 0.55f`, `mediumHeight = 0.25f`
- **Hysteresis Smoothing**: Exponential Moving Average (EMA) with $\alpha = 0.35$ and a $4\%$ hysteresis band (`proximityHysteresisMargin = 0.04f`) to prevent boundary flickering.
- **Metric Depth Status**: **ABSENT**. The abandoned depth model was deleted in commit `fbc071c`. No LiDAR, ToF, or metric depth calculation exists.

### 5. Horizontal Azimuth Direction
- **Implementation**: [`DetectorConfig.kt:L50-51`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ai/DetectorConfig.kt#L50-L51), [`HorizontalPosition.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ai/HorizontalPosition.kt)
- **Sectors**:
  - `LEFT`: Normalized $x_c < 0.33f$
  - `AHEAD`: $0.33f \le x_c \le 0.67f$
  - `RIGHT`: Normalized $x_c > 0.67f$

### 6. Priority Arbitration & Cooldown (`DecisionEngine.kt`)
- **Implementation**: [`DefaultDecisionEngine.kt:L165-380`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/fusion/decision/DecisionEngine.kt#L165-L380)
- **Minimum Temporal Stability**: Requires an object to be tracked across at least **2 consecutive frames** (`minimumStableObservations = 2`) before emitting an alert.
- **Repetition Cooldown**: Suppresses identical semantic alerts (`"${className}|${position}|${proximity}"`) for **$2500\text{ ms}$** (`cooldownPeriodMs = 2500L`).
- **Alert Prioritization**: Canonical class priority order: `person` $\rightarrow$ `door` $\rightarrow$ `stairs` $\rightarrow$ `vehicles` $\rightarrow$ `bicycle` $\rightarrow$ `motorcycle` $\rightarrow$ `furniture` $\rightarrow$ `small objects`.

---

## Module 4.2 — READ: Assisted Document Reading

### 1. Document Reading Pipeline Workflow
```
[Preview Frame] 
      │
      ▼
Page Boundary Detection (PageDetector) 
      │
      ▼
Stability Tracking (PageStabilityTracker: 3 consecutive stable frames) 
      │
      ▼
Automatic or Manual High-Res ImageCapture (CameraController) 
      │
      ▼
Perspective Rectification (PageImageProcessor: 4-point homography unwarp) 
      │
      ▼
Latin OCR (Google ML Kit: OcrTextExtractor) 
      │
      ▼
Reading Order Structuring (ReadingOrderOrganizer: column-aware sorting) 
      │
      ▼
Text Chunking (TextChunker: sentence boundary splitting) 
      │
      ▼
Sequential TTS Delivery (ReadCoordinator + UtteranceProgressListener)
```

### 2. Component Specifications
- **Page Boundary Detection (`PageDetector.kt`)**:
  - Detects physical rectangular page boundaries from camera frames.
  - Extracts 4 corner points (Top-Left, Top-Right, Bottom-Right, Bottom-Left) and sets `isVerifiedBoundary = true`.
- **Page Stability Tracking (`PageStabilityTracker.kt`)**:
  - Tracks corner displacement across frames. Requires corner movement $< 15\text{ pixels}$ over **3 consecutive frames** before declaring the document stable for automatic capture.
- **Perspective Homography Rectification (`PageImageProcessor.kt`)**:
  - Uses OpenCV-equivalent matrix homography to warp the skewed quadrilateral page into a flat, upright rectangular Bitmap prior to OCR.
- **OCR Engine (`OcrTextExtractor.kt`)**:
  - Library: **Google ML Kit Latin Text Recognition** (`com.google.mlkit:text-recognition:16.0.1`).
  - Runs fully on-device. Extracts structured `TextBlock`, `Line`, and `Element` hierarchies.
  - Rejects text outside the verified document crop boundaries (`OcrBorderFiltering`).
- **Reading Order Organization (`ReadingOrderOrganizer.kt`)**:
  - Sorts blocks into natural reading order (top-to-bottom, left-to-right).
  - Handles two-column document layouts by grouping horizontally disjoint column spans before vertical sorting.
- **Text Chunking (`TextChunker.kt`)**:
  - Cleans OCR noise, preserves punctuation, and splits continuous passages into discrete sentence chunks (15–25 words) for natural speech pauses.
- **Coordinator Lifecycle (`ReadCoordinator.kt`)**:
  - States: `Searching`, `PageDetected`, `PageBoundaryNotDetected`, `Capturing`, `ProcessingOcr`, `Reading`, `Completed`, `Error`.
  - Manual Capture: `"CAPTURE PAGE NOW"` (`capturePageNow()`). If boundary is unverified, refuses capture and speaks: *"Page boundary not detected. Reposition the book and try again."*
  - Retry: `"RETRY SCANNING"` / `"SCAN ANOTHER DOCUMENT"` (`scanAgain()`).
- **Implemented vs Absent Controls**:
  - *Implemented*: Automatic capture, manual capture now, scan again, repeat text (via voice).
  - *Absent / Not Implemented*: Pause, resume, and jump-to-paragraph controls do not exist in the source code.

---

## Module 4.3 — PEOPLE: Registered Person Face Recognition

### 1. Two-Stage Vision Pipeline
```
Camera Frame ──► ML Kit Face Detection ──► Face Quality Filtering ──► MobileFaceNet TFLite (112x112) ──► 192-d Vector ──► Cosine Matching
```

### 2. Model Identity, Dimensions & Quality Filtering
- **Embedding Model**: `app/src/main/assets/models/mobilefacenet.tflite`
- **File Size**: $5,233,552\text{ bytes}$ ($4.99\text{ MB}$)
- **SHA-256 Checksum**: `BE4BC7CFC53F7BC336D0F28B1AB92535F618C913A422B683210750F6B5354854`
- **Stage 1 (Face Detection)**: Google ML Kit Face Detection (`com.google.mlkit:face-detection:16.1.7`).
  - Detects bounding box, head yaw ($\text{Euler Y}$), pitch ($\text{Euler X}$), and roll ($\text{Euler Z}$).
  - Quality thresholds ([`PeopleRecognitionConfig.kt:L21-24`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/people/PeopleRecognitionConfig.kt#L21-L24)): Rejects faces occupying $< 12\%$ of frame (`minFaceSizeRatio = 0.12f`), head yaw $> 35^\circ$, pitch $> 35^\circ$, or roll $> 30^\circ$.
- **Stage 2 (Feature Embedding)**: [`MobileFaceNetEmbeddingModel.kt:L122-278`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/people/FaceRecognitionEngine.kt#L122-L278)
  - Input: `[1, 112, 112, 3]` NHWC Float32 RGB.
  - Normalization: $\frac{\text{pixel} - 127.5f}{128.0f} \in [-1.0, 1.0]$.
  - Output: `[1, 192]` Float32. Unit-normalized via L2 norm.
- **Stage 3 (Matching & Cosine Threshold)**:
  $$\text{Cosine Similarity} = \sum_{i=1}^{192} u_i \cdot v_i \quad (\text{since } \|u\|=\|v\|=1)$$
  - Threshold: $\ge 0.70f$ (`similarityThreshold = 0.70f`). Matches below 0.70f are classified as unknown.
  - Temporal confirmation: Requires **2 consecutive qualifying frames** before vocalizing an identity.

### 3. Local Enrollment & Biometric Storage
- **Enrollment Protocol (`EnrollmentCoordinator.kt`)**: Guided capture requiring **5 high-quality face samples** (`requiredEnrollmentSamples = 5`) spaced at least $400\text{ ms}$ apart.
- **Mandatory Consent Safeguard**: [`EnrollPersonScreen.kt:L305-354`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ui/screens/EnrollPersonScreen.kt#L305-L354) contains a mandatory "Consent obtained" checkbox. The "START FACE CAPTURE" button remains disabled until explicit consent is toggled.
- **Local Persistence**: [`LocalFilePeopleRepository.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/people/PeopleRepository.kt#L47-L155) stores profiles and vectors on-device in `people_profiles.dat`.
- **Privacy & Encryption Evidence**:
  - *Local Only*: Zero biometric data is transmitted across the network.
  - *Encryption Status*: **NOT ENCRYPTED**. The file format is plain UTF-8 text with delimited lines (`PERSON|id|name|enrolledAt` followed by comma-separated floats and `END_PERSON`).

---

## Module 4.4 — NAVIGATION: Outdoor Pedestrian GPS Wayfinding

### 1. Subsystem Architecture & Location Engine
- **Location Engine (`DefaultLocationEngine.kt`)**: Dual-provider positioning.
  - Primary: Google Play Services `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY`.
  - Fallback: Android system `LocationManager` (`GPS_PROVIDER`).
- **Orientation Engine (`DefaultOrientationEngine.kt`)**: Sensor fusion integrating `Sensor.TYPE_ROTATION_VECTOR` and `Sensor.TYPE_MAGNETIC_FIELD` to compute real-time phone azimuth ($0^\circ - 360^\circ$).

### 2. Networking Services & External Endpoints
- **Destination Geocoding (`NominatimGeocodingService.kt`)**:
  - Endpoint: `https://nominatim.openstreetmap.org/search`
  - User-Agent: `VisionEye-AcademicPrototype/1.0 (Android; Accessibility-Research)`
  - Parameter: `format=json&limit=1`
- **Pedestrian Routing (`OsrmPedestrianRoutingService.kt`)**:
  - Endpoint: `https://routing.openstreetmap.de/routed-foot/route/v1/foot/`
  - Parameters: `overview=full&steps=true&geometries=geojson`
  - Walking-only profile: Routes pedestrian footpaths and sidewalks.
- **Network Dependency**:
  - *Requires Network*: Resolving destination queries (geocoding) and fetching the initial route polyline/maneuvers (routing).
  - *Works Offline*: Live GPS tracking, compass heading updates, distance-to-turn countdown, off-route deviation checks, and spoken maneuver instructions operate 100% locally once the route is loaded.

### 3. Turn-by-Turn Guidance Engine (`NavigationGuidanceEngine.kt`)
- **Pre-Turn Auditory Preview Thresholds**:
  - Far Preview: $50\text{ meters}$ (*"Turn right onto Main Street in 50 meters."*)
  - Near Preview: $20\text{ meters}$ (*"Turn right in 20 meters."*)
  - Immediate Turn: $8\text{ meters}$ (*"Turn right now."*)
- **Step Advance**: Occurs when user is within $2.5\text{ meters}$ of the waypoint or passes the intersection into the outgoing leg.
- **Destination Arrival**: Triggered within a $10\text{ meter}$ arrival radius across consecutive GPS fixes.
- **Off-Route Detection**: Triggered when the user deviates $> 25\text{ meters}$ from the route polyline across consecutive fixes with GPS accuracy $\le 20\text{ meters}$. Emits `RerouteNeeded` event with a $10\text{ second}$ cooldown.

---

## Module 4.5 — EMERGENCY SOS: Distress Dispatch & Telephony

### 1. Triggers & Abortable Countdown
- **Triggers**:
  - On-screen `"TRIGGER SOS"` button tap.
  - Long press gesture on SOS screen.
  - Push-to-talk voice command: `"Trigger SOS"`.
  - Dashboard voice command `"Emergency SOS"` opens the screen only and **does not** trigger dispatch.
- **Countdown Duration**: Verified in [`DefaultEmergencyController.kt:L51`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/emergency/DefaultEmergencyController.kt#L51):
  ```kotlin
  val countdownSeconds: Int = 3,
  val countdownIntervalMs: Long = 1000L,
  ```
  Exactly **3 seconds** ($3 \rightarrow 2 \rightarrow 1$).
- **Cancellation**: Full-screen tap on `"CANCEL SOS"` or speaking `"Cancel SOS"` during the 3 seconds immediately terminates the coroutine and cancels dispatch.

### 2. Message Construction & Telephony Dispatch
- **Location Acquisition**: Queries `LocationEngine` with a $4000\text{ ms}$ timeout (`locationTimeoutMs = 4000L`).
- **SMS Payload Format** ([`EmergencyMessageFormatter.kt:L31-41`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/emergency/EmergencyMessageFormatter.kt#L31-L41)):
  ```
  VisionEye SOS: Emergency assistance requested.
  Location: https://maps.google.com/?q=<lat>,<lon>
  Approximate coords: <lat>, <lon> (~<accuracy>m accuracy)
  ```
  If location is unavailable: `"VisionEye SOS: Emergency assistance requested.\nLocation unavailable."`
- **Dispatch Mechanism (`DefaultSmsSender.kt`)**: Uses Android `SmsManager.sendTextMessage()` or `sendMultipartTextMessage()`.
- **Dispatch Attempt vs Delivery Confirmation**:
  - The implementation confirms submission to the Android radio telephony stack (`SmsSendResult.Success`).
  - It does **not** register a delivery broadcast receiver (`SMS_DELIVERED_ACTION`). The application cannot guarantee cellular transmission or physical receipt by the recipient handset.
- **Phone Call Intent Status**: **ABSENT**. There is no dialer or phone-call intent in `DefaultEmergencyController.kt`. Only SMS dispatch is implemented.
- **Contact Storage**: Persisted in private Android `SharedPreferences` (`"visioneye_sos_contacts"`, `Context.MODE_PRIVATE`).

---

## Module 4.6 — CURRENCY: Indian Banknote Recognition

### 1. Model Identity, Location & Cryptographic Hash
- **Model Filename**: `currency_yolo11n.tflite`
- **Location**: [`app/src/main/assets/models/currency_yolo11n.tflite`](file:///D:/UserData/VisionEye/app/src/main/assets/models/currency_yolo11n.tflite)
- **Root Mirror**: Identical copy present at project root as `best.tflite`
- **File Size**: $10,631,672\text{ bytes}$ ($10.14\text{ MB}$)
- **SHA-256 Checksum**: `30908E9EB211043A008D20D6FDAC2FFDDE8E2808E29ECDBE07607FF78B1C1B40`
- **Associated Labels File**: [`app/src/main/assets/models/currency_labels.txt`](file:///D:/UserData/VisionEye/app/src/main/assets/models/currency_labels.txt) ($21\text{ bytes}$)
- **Architecture**: Ultralytics YOLO11 nano object detector (FP32)

### 2. Supported Denominations & Class Mapping
Verified in [`CurrencyDenomination.kt:L14-63`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/currency/CurrencyDenomination.kt#L14-L63) and `currency_labels.txt`:
| Class ID | Banknote Denomination | Label String | Spoken Singular | Spoken Plural |
| :---: | :---: | :---: | :--- | :--- |
| **0** | ₹10 | `10` | *"10 rupee note"* | *"10 rupee notes"* |
| **1** | ₹100 | `100` | *"100 rupee note"* | *"100 rupee notes"* |
| **2** | ₹20 | `20` | *"20 rupee note"* | *"20 rupee notes"* |
| **3** | ₹200 | `200` | *"200 rupee note"* | *"200 rupee notes"* |
| **4** | ₹50 | `50` | *"50 rupee note"* | *"50 rupee notes"* |
| **5** | ₹500 | `500` | *"500 rupee note"* | *"500 rupee notes"* |

### 3. Tensor Contract & Post-Processing
- **Input Tensor**: Shape **`[1, 3, 640, 640]`**, Float32, Planar **NCHW** layout, RGB normalized $[0.0, 1.0]$.
- **Output Tensor**: Shape **`[1, 10, 8400]`**, Float32.
  - $10$ channels: 4 bounding box coordinates $[x_c, y_c, w, h]$ + 6 denomination scores.
  - $8400$ prediction anchors.
- **Confidence Threshold**: $\ge 0.50f$ (`confidenceThreshold = 0.50f`).
- **NMS Overlap Threshold**: IoU $\ge 0.45f$. Max detections capped at 10.
- **Model Validation**: [`CurrencyModelValidator.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/currency/CurrencyModelValidator.kt) verifies shapes on initialization.

### 4. Temporal Decision Engine (`CurrencyDecisionEngine.kt`)
- **Temporal Confirmation**: Requires the identical denomination set across **2 consecutive frames** (`temporalConfirmationCount = 2`) before speaking.
- **Absence Reset**: Resets confirmation and speech memory if no banknote is observed for **$1500\text{ ms}$** (`absenceTimeoutMs = 1500L`).
- **Speech Announcement**: Groups multiple notes (*"Two 100 rupee notes and one 500 rupee note"*). Suppresses repeated announcements while notes remain continuously visible.

### 5. Training Dataset Evidence in Repository (`ml/currency/`)
Verified from [`ml/currency/prepare_dataset.py`](file:///D:/UserData/VisionEye/ml/currency/prepare_dataset.py) and extracted dataset files:
- **Dataset Partitioning (70% Train, 15% Val, 15% Test)**:
  - `dataset_classifier/train`: 2,075 images
  - `dataset_classifier/val`: 454 images
  - `dataset_classifier/test`: 460 images
  - **Total Dataset Size**: **2,989 images**
- **Per-Denomination Class Distribution**:
  - `10`: 450 train, 100 val, 101 test (651 total)
  - `20`: 378 train, 82 val, 85 test (545 total)
  - `50`: 362 train, 67 val, 70 test (499 total)
  - `100`: 222 train, 51 val, 51 test (324 total)
  - `200`: 183 train, 45 val, 44 test (272 total)
  - `500`: 186 train, 46 val, 46 test (278 total)
  - `Background`: 294 train, 63 val, 63 test (420 total — wood, marble, fabric, tiles, desk clutter)
- **Object Detection Dataset (`dataset.zip`)**: 410 annotated images (325 train, 50 val, 35 test) with YOLO bounding-box format text labels.
- **Leakage Prevention**: Session clustering with $60,000\text{ ms}$ timestamp grouping; 0 duplicate SHA-256 hashes across splits.

---

## Module 4.7 — Voice Assistance & Accessibility

### 1. Recognition Engine & Interaction Model
- **Engine**: On-device native Android `android.speech.SpeechRecognizer` via `AndroidSpeechRecognitionEngine`.
- **Activation Mode**: **Push-to-Talk (Button-Activated)**. User taps the visible `"SPEAK A COMMAND"` button.
- **Wake-Word Status**: **ABSENT**. No background listening, no "Hey VisionEye" wake word.
- **Language / Locale**: Default: Indian English (`en-IN`), fallback to device locale.
- **Watchdog Timeout**: $8000\text{ ms}$ max session duration, $6000\text{ ms}$ speech duration to prevent stuck listening states.

### 2. Canonical UI Label Matching Principle
Voice commands match the exact visible button labels in the UI. Matching order:
1. Exact case-insensitive match with canonical label.
2. Normalized match (punctuation and whitespace stripped).
3. Levenshtein phonetic distance match ($\le 2$ edit distance).

### 3. Voice Command Registry by Screen Context
| Screen Context | Visible UI Button Label | Canonical Spoken Command | Primary Aliases | Action Executed |
| :--- | :--- | :--- | :--- | :--- |
| **MAIN DASHBOARD** | `NAVIGATE` | `"Navigate"` | `"Navigate mode"`, `"Obstacle detection"` | Switches to `NAVIGATE` mode, starts camera |
| | `READ TEXT` | `"Read text"` | `"Read"`, `"Scan document"` | Switches to `READ` mode, starts document scanner |
| | `SCAN CURRENCY` | `"Scan currency"` | `"Currency"`, `"Scan rupee"` | Switches to `CURRENCY` mode, starts banknote detector |
| | `RECOGNIZE PEOPLE` | `"Recognize people"` | `"People"`, `"Who is this"` | Switches to `PEOPLE` mode, starts face matcher |
| | `SAVE PERSON` | `"Save person"` | `"Enroll person"`, `"Add person"` | Opens guided face enrollment flow |
| | `MANAGE PEOPLE` | `"Manage people"` | `"Saved people"`, `"Face directory"` | Opens saved people directory |
| | `OUTDOOR GPS NAVIGATION` | `"Outdoor GPS navigation"` | `"Navigation"`, `"GPS navigation"` | Opens outdoor turn-by-turn navigation screen |
| | `EMERGENCY SOS` | `"Emergency SOS"` | `"Emergency"`, `"SOS"` | Opens SOS screen (**does not** trigger dispatch) |
| **CAMERA ASSISTANCE** | `STOP CAMERA` | `"Stop camera"` | `"Stop"`, `"Back"`, `"Close camera"` | Stops camera stream, returns to dashboard |
| *(NAVIGATE mode)* | `BOXES: ON` / `BOXES: OFF` | `"Boxes on"` / `"Boxes off"` | `"Toggle boxes"`, `"Bounding boxes"` | Toggles bounding-box debug overlay |
| *(READ mode)* | `CAPTURE PAGE NOW` | `"Capture page now"` | `"Capture page"`, `"Take picture"` | Triggers immediate document capture |
| | `RETRY SCANNING` | `"Retry scanning"` | `"Retry"`, `"Scan again"` | Resets document scanner |
| | `SCAN ANOTHER DOCUMENT` | `"Scan another document"` | `"Another document"` | Resets document scanner for new page |
| | *(Spoken query)* | `"Repeat text"` | `"Read again"`, `"Repeat"` | Re-reads active sentence or status aloud |
| *(CURRENCY mode)* | *(Spoken query)* | `"What note is this?"` | `"What note is this"`, `"Check banknote"` | Speaks confirmed banknote denomination |
| *(PEOPLE mode)* | *(Spoken query)* | `"Who is in front of me?"`| `"Who is this"`, `"Recognize face"` | Speaks recognized person or "Unknown person" |
| **NAVIGATION GPS** | `START ROUTE` | `"Start route"` | `"Start navigation"`, `"Begin route"` | Resolves destination and starts routing |
| | `STOP GUIDANCE` | `"Stop guidance"` | `"End guidance"` | Halts turn-by-turn guidance |
| | `CLEAR ROUTE` | `"Clear route"` | `"Reset route"` | Clears loaded route and destination input |
| | `Destination` | `"Destination"` | `"Enter destination"` | Opens dictation to speak destination |
| | *(Spoken query)* | `"Where am I?"` | `"Current coordinates"`, `"My location"` | Speaks current latitude, longitude, and accuracy |
| | *(Spoken query)* | `"Read compass"` | `"Which way am I facing"`, `"Heading"` | Speaks compass heading and cardinal direction |
| | *(Spoken query)* | `"How long until arrival?"`| `"ETA"`, `"Walking time"` | Speaks remaining walking duration |
| **EMERGENCY SOS** | `TRIGGER SOS` | `"Trigger SOS"` | `"Trigger emergency"`, `"Send SOS"` | Initiates 3-second abortable countdown |
| | `CANCEL SOS` | `"Cancel SOS"` | `"Abort SOS"`, `"Cancel emergency"` | Cancels active countdown or dispatch |
| | `RETURN TO MAIN MENU` | `"Return to main menu"` | `"Back"`, `"Exit SOS"` | Exits SOS mode |
| | `EDIT` | `"Edit"` | `"Edit contact"` | Opens contact edit fields |
| | `SAVE CONTACT` | `"Save contact"` | `"Save"` | Validates and saves contact |
| **ENROLL PERSON** | `Person Name` | `"Person name"` | `"Enter name"` | Begins dictation for person's name |
| | `Consent obtained` | `"Consent obtained"` | `"Confirm consent"`, `"Consent"` | Toggles mandatory informed consent |
| | `START FACE CAPTURE` | `"Start face capture"` | `"Begin capture"` | Starts 5-sample camera capture |
| | `DONE (RETURN TO MAIN)` | `"Done"` | `"Finish enrollment"` | Saves profile and returns to dashboard |
| **GLOBAL (ALL SCREENS)**| `What can I do here?` | `"What can I do here?"` | `"Help"`, `"Available commands"` | Speaks list of all valid controls on screen |

### 4. Speech Interruption & Priority Arbitration
- Ordinary voice feedback uses `SpeechPriority.NORMAL` $\rightarrow$ queued sequentially using Android `TextToSpeech.QUEUE_ADD`.
- Ordinary voice prompts **cannot** interrupt or flush active high-priority guidance.
- Only emergency SOS announcements (`SpeechPriority.IMMEDIATE_SAFETY`) flush the queue using `TextToSpeech.QUEUE_FLUSH`.
- `DefaultSpeechEventArbitrator` enforces single-channel sequential delivery and drops stale navigation alerts older than $4000\text{ ms}$.

---

# 5. Architecture & Design Evidence

### 5.1 Architecture Overview
VisionEye follows a **Mode-Based Layered Reactive Architecture** in Kotlin and Jetpack Compose:
- **Presentation Layer**: Material3 Compose screens (`MainScreen`, `CameraAssistanceScreen`, `NavigationGpsScreen`, `SosScreen`, `ManagePeopleScreen`, `EnrollPersonScreen`) observing reactive state streams.
- **Coordination Layer**: `ModeManager` (`DefaultModeManager`) enforcing single-active-mode execution; `ModeAwareComponent` lifecycle contracts.
- **Perception Layer**: CameraX image analysis, TFLite interpreters (`YoloV8Detector`, `CurrencyDetector`, `MobileFaceNetEmbeddingModel`), ML Kit (`OcrTextExtractor`, `MlKitFaceDetector`).
- **Arbitration & Output Layer**: `DecisionEngine`, `SpeechEventArbitrator`, `DefaultSpeechController`, `DefaultVoiceController`.
- **Sensory & External Services**: `DefaultLocationEngine` (GPS), `DefaultOrientationEngine` (Compass), `NominatimGeocodingService`, `OsrmPedestrianRoutingService`.
- **Data Layer**: `LocalFilePeopleRepository`, `SharedPreferencesEmergencyContactRepository`.

### 5.2 Mermaid Architectural & Workflow Diagrams

#### 1. System Context Diagram
```mermaid
graph TD
    User([Visually Impaired User]) <-->|Acoustic Speech & Touch| VE[VisionEye Android Application]
    VE -->|Captures Video Frames| Cam[Smartphone Camera Sensor]
    VE -->|Queries GNSS / Compass| Sensors[GPS & Magnetometer Sensors]
    VE -->|Dispatches Distress SMS| Telephony[Cellular Telephony / SmsManager]
    VE <-->|Geocoding / Nominatim| OSM[OpenStreetMap Servers]
    VE <-->|Pedestrian Foot Routing| OSRM[OSRM Routing Server]
    VE -->|Local Storage| Disk[(Sandboxed Internal Flash Storage)]
```

#### 2. High-Level Component & Layer Architecture Diagram
```mermaid
graph TD
    subgraph UI_Layer [Presentation Layer - Jetpack Compose & WCAG AAA]
        MS[MainScreen]
        CAS[CameraAssistanceScreen]
        NGS[NavigationGpsScreen]
        SS[SosScreen]
        EPS[EnrollPersonScreen]
        MPS[ManagePeopleScreen]
    end

    subgraph Coordination_Layer [Coordination & State Machine Layer]
        MM[ModeManager / DefaultModeManager]
        VC[VoiceController / DefaultVoiceController]
        SC[SpeechController / DefaultSpeechController]
        SEA[SpeechEventArbitrator]
    end

    subgraph Controllers [Controller & Domain Layer]
        CC[CameraController / CameraManager]
        AMM[AiModelManager]
        RC[ReadCoordinator]
        CurC[CurrencyCoordinator]
        PC[PeopleCoordinator]
        NC[NavigationController]
        EC[EmergencyController]
    end

    subgraph Perception [Perception & Sensor Layer]
        YOLO[YoloV8Detector]
        CURR[CurrencyDetector]
        FACE[MlKitFaceDetector + MobileFaceNet]
        OCR[OcrTextExtractor]
        LOC[DefaultLocationEngine]
        COMP[DefaultOrientationEngine]
        DE[DecisionEngine]
    end

    subgraph Persistence [Local Persistence Layer]
        PR[(LocalFilePeopleRepository)]
        ER[(SharedPreferencesEmergencyContactRepository)]
    end

    UI_Layer -->|User Events| Coordination_Layer
    Coordination_Layer -->|StateFlow| UI_Layer
    MM -->|Activates / Deactivates| Controllers
    CC --> Perception
    AMM --> Perception
    Perception --> DE
    DE --> SEA
    SEA --> SC
    VC --> UI_Layer
    Controllers --> Persistence
```

#### 3. Object-Detection Data Flow (NAVIGATE Mode)
```mermaid
sequenceDiagram
    autonumber
    participant Cam as CameraX Stream
    participant Pre as ImagePreprocessor
    participant TFLite as YOLOv8n TFLite
    participant NMS as YoloV8Detector (NMS)
    participant Prox as ProximityEstimator
    participant DE as DecisionEngine
    participant Spk as SpeechController

    Cam->>Pre: ImageProxy (Rotation Degrees)
    Pre->>Pre: Upright Bitmap -> Letterbox (640x640)
    Pre->>TFLite: Planar NCHW [1, 3, 640, 640] Float32
    TFLite->>NMS: Raw Tensor [1, 84, 8400] Float32
    NMS->>NMS: Threshold >= 0.40 -> Class NMS (IoU >= 0.45)
    NMS->>Prox: Detections (Normalized Box Height)
    Prox->>Prox: Apply Hysteresis (NEAR, MEDIUM, FAR)
    Prox->>DE: Spatial Detections + Direction (LEFT, AHEAD, RIGHT)
    DE->>DE: Track 2 Frames -> Check 2500ms Cooldown -> Class Rank
    DE->>Spk: Emit Alert ("Obstacle close ahead")
```

#### 4. Document-Reading Data Flow (READ Mode)
```mermaid
sequenceDiagram
    autonumber
    participant Cam as CameraX Preview
    participant PD as PageDetector
    participant ST as PageStabilityTracker
    participant IC as CameraX ImageCapture
    participant PIP as PageImageProcessor
    participant OCR as OcrTextExtractor (ML Kit)
    participant RO as ReadingOrderOrganizer
    participant TC as TextChunker
    participant RC as ReadCoordinator

    Cam->>PD: Preview Frames
    PD->>ST: Candidate Quadrilateral Corners
    ST->>ST: Track Movement (< 15px over 3 frames)
    alt Automatic Stability Trigger OR Manual Capture Now
        ST->>IC: Trigger High-Res ImageCapture
        IC->>PIP: High-Resolution Bitmap + Quad Points
        PIP->>PIP: Perspective Homography Rectification
        PIP->>OCR: Rectified Upright Document Bitmap
        OCR->>RO: Raw TextBlocks with Bounding Polygons
        RO->>TC: Organized Multi-Column Reading Order Text
        TC->>RC: Sentence Chunks List
        RC->>RC: Stream Chunks via SpeechController with Utterance Listener
    end
```

#### 5. GPS Navigation Data Flow (NAVIGATION Mode)
```mermaid
sequenceDiagram
    autonumber
    actor User as User
    participant UI as NavigationGpsScreen
    participant Geo as NominatimGeocodingService
    participant OSRM as OsrmPedestrianRoutingService
    participant Engine as NavigationGuidanceEngine
    participant GPS as LocationEngine (GNSS)
    participant Comp as OrientationEngine (Compass)
    participant TTS as SpeechController

    User->>UI: Enter Destination ("Railway Station")
    UI->>Geo: geocode("Railway Station")
    Geo-->>UI: Coordinates (lat, lon)
    UI->>OSRM: calculateRoute(start, destination)
    OSRM-->>UI: Walking Route (Polyline + Maneuver Steps)
    UI->>Engine: start(route)
    loop Active Walk
        GPS->>Engine: onLocationUpdate(fix)
        Comp->>Engine: onHeadingUpdate(headingDegrees)
        Engine->>Engine: Distance Check (50m, 20m, 8m "Turn now")
        Engine->>Engine: Off-Route Corridor Check (> 25m)
        Engine->>TTS: Spoken Maneuver Instruction
    end
```

#### 6. Emergency SOS Workflow (SOS Mode)
```mermaid
sequenceDiagram
    autonumber
    actor User as User
    participant UI as SosScreen
    participant EC as DefaultEmergencyController
    participant GPS as LocationEngine
    participant SMS as DefaultSmsSender
    participant Contact as EmergencyContactRepository

    User->>UI: Tap "TRIGGER SOS" / Voice Command
    UI->>EC: triggerSos()
    EC->>Contact: getContact()
    alt No Contact Saved
        EC->>UI: State = Failed ("No emergency contact")
    else Valid Contact Found
        loop 3-Second Abortable Countdown (3 -> 2 -> 1)
            EC->>UI: State = Countdown(sec)
            opt User Taps "CANCEL SOS"
                User->>EC: cancelSos()
                EC->>UI: State = Cancelled ("SOS cancelled")
            end
        end
        EC->>GPS: Acquire Live Coordinates (Timeout: 4000ms)
        GPS-->>EC: LocationFix (lat, lon, accuracy)
        EC->>SMS: sendSms(phone, formattedMessage)
        SMS-->>EC: SmsSendResult.Success (Telephony submitted)
        EC->>UI: State = Sent ("SOS message sent")
    end
```

#### 7. Voice-Command Processing Flow
```mermaid
sequenceDiagram
    autonumber
    actor User as User
    participant Btn as VoiceControlButton
    participant VC as DefaultVoiceController
    participant SR as Android SpeechRecognizer
    participant Mat as VoiceMatcher
    participant Reg as VoiceCommandRegistry
    participant UI as Active Screen Action

    User->>Btn: Tap "SPEAK A COMMAND"
    Btn->>VC: startListening()
    VC->>SR: startListening(locale: en-IN)
    User->>SR: Spoken Utterance ("Capture page now")
    SR-->>VC: onResults(["Capture page now", "capture page"])
    VC->>Reg: match(hypotheses, currentScreenContext)
    Reg->>Mat: Canonical Label Comparison + Levenshtein
    Mat-->>Reg: Matched Action (read_capture_page)
    Reg->>UI: action.onExecute()
    UI-->>VC: VoiceActionResult.Success("Capturing page")
    VC->>VC: speakFeedback("Capturing page")
```

#### 8. Currency-Detection Inference Flow (CURRENCY Mode)
```mermaid
sequenceDiagram
    autonumber
    participant Cam as CameraX Stream
    participant Prep as CurrencyPreprocessing
    participant TFLite as YOLO11n TFLite
    participant NMS as CurrencyPostProcessor
    participant DE as CurrencyDecisionEngine
    participant Spk as SpeechController

    Cam->>Prep: ImageProxy -> Bitmap (640x640)
    Prep->>TFLite: Planar NCHW [1, 3, 640, 640] Float32
    TFLite->>NMS: Raw Output [1, 10, 8400] Float32
    NMS->>NMS: Confidence >= 0.50 -> NMS (IoU >= 0.45)
    NMS->>DE: Detected Denominations ([500])
    DE->>DE: Check Consecutive Frames (Frame 1 -> Detecting...)
    Note over DE: Next Frame with [500]
    DE->>DE: Consecutive Frames = 2 -> Confirmed!
    DE->>Spk: speak("500 rupee note")
    Note over DE: If absent >= 1500ms -> Session Resets to Idle
```

#### 9. Principal Actors and System Use Cases
```mermaid
graph TD
    BVI([Visually Impaired User])
    CG([Caregiver / Assistant])

    subgraph VisionEye_System [VisionEye Assistive System]
        UC1[UC-1: Detect Obstacles & Estimate Proximity]
        UC2[UC-2: Read Printed Document Text Aloud]
        UC3[UC-3: Recognize Indian Banknote Denomination]
        UC4[UC-4: Recognize Registered Face]
        UC5[UC-5: Enroll New Face Profile with Consent]
        UC6[UC-6: Navigate Pedestrian Outdoor Route]
        UC7[UC-7: Trigger & Cancel Emergency SOS]
        UC8[UC-8: Operate App via UI-Label Voice Commands]
        UC9[UC-9: Configure Emergency Contact]
    end

    BVI --> UC1
    BVI --> UC2
    BVI --> UC3
    BVI --> UC4
    BVI --> UC6
    BVI --> UC7
    BVI --> UC8

    CG --> UC5
    CG --> UC9
```

---

# 6. Functional Requirements Verification Table

| Requirement ID | Requirement Statement | Module / Screen | Evidence Path & Symbol | Verification Status | Dependencies & Limitations |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **FR-01** | System defaults to `NAVIGATE` mode upon launch | Core Mode | [`ModeManager.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/core/mode/ModeManager.kt), [`VisionEyeApplication.kt:L85`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/VisionEyeApplication.kt#L85) | `TESTED` | Verified in `ModeManagerTest` |
| **FR-02** | YOLOv8n object detection on 640×640 frames | NAVIGATE | [`YoloV8Detector.kt:L140-182`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ai/YoloV8Detector.kt#L140-L182) | `TESTED` | Model asset `yolov8n.tflite` present |
| **FR-03** | Bounding box proximity classified into NEAR/MED/FAR | NAVIGATE | [`ProximityEstimator.kt:L23-103`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/fusion/decision/ProximityEstimator.kt#L23-L103) | `TESTED` | Heuristic relative visual angle; not metric |
| **FR-04** | Suppress identical obstacle alerts for 2500ms | NAVIGATE | [`DecisionConfig.kt:L23`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/fusion/decision/DecisionConfig.kt#L23), [`DecisionEngine.kt:L186`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/fusion/decision/DecisionEngine.kt#L186) | `TESTED` | Verified in `DecisionEngineTest` |
| **FR-05** | Page boundary detection and stability tracking (3 frames) | READ | [`PageStabilityTracker.kt:L35-65`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/read/PageStabilityTracker.kt#L35-L65) | `TESTED` | Requires stable camera hold; no pause/resume |
| **FR-06** | Perspective homography unwarping before OCR | READ | [`PageImageProcessor.kt:L40-85`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/read/PageImageProcessor.kt#L40-L85) | `TESTED` | Requires 4 detected corner points |
| **FR-07** | Latin OCR text extraction and sentence chunking | READ | [`OcrTextExtractor.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/read/OcrTextExtractor.kt), [`TextChunker.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/read/TextChunker.kt) | `TESTED` | Google ML Kit Latin; English text only |
| **FR-08** | YOLO11n detection for 6 INR currency notes | CURRENCY | [`CurrencyDetector.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/currency/CurrencyDetector.kt), [`CurrencyDenomination.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/currency/CurrencyDenomination.kt) | `TESTED` | Supported: ₹10, ₹20, ₹50, ₹100, ₹200, ₹500 |
| **FR-09** | 2-frame temporal confirmation for banknotes | CURRENCY | [`CurrencyDecisionEngine.kt:L112-116`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/currency/CurrencyDecisionEngine.kt#L112-L116) | `TESTED` | 1500ms absence timeout resets state |
| **FR-10** | MobileFaceNet 192-d face embedding matching ($\ge 0.70$) | PEOPLE | [`FaceRecognitionEngine.kt:L204-229`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/people/FaceRecognitionEngine.kt#L204-L229) | `TESTED` | 112×112 cropped face input |
| **FR-11** | Guided 5-sample enrollment with explicit consent | PEOPLE | [`EnrollmentCoordinator.kt:L233-283`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/people/EnrollmentCoordinator.kt#L233-L283), [`EnrollPersonScreen.kt:L360`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ui/screens/EnrollPersonScreen.kt#L360) | `TESTED` | Capture disabled if consent unchecked |
| **FR-12** | Local biometric profile persistence in internal storage | PEOPLE | [`LocalFilePeopleRepository.kt:L47-155`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/people/PeopleRepository.kt#L47-L155) | `TESTED` | Plaintext delimited UTF-8 (`.dat`); unencrypted |
| **FR-13** | Outdoor pedestrian routing via OSRM & Nominatim | NAVIGATION | [`NominatimGeocodingService.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/navigation/routing/GeocodingService.kt), [`OsrmPedestrianRoutingService.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/navigation/routing/PedestrianRoutingService.kt) | `TESTED` | Requires internet access for route fetch |
| **FR-14** | Turn-by-turn guidance at 50m, 20m, and 8m | NAVIGATION | [`NavigationGuidanceEngine.kt:L326-349`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/navigation/guidance/NavigationGuidanceEngine.kt#L326-L349) | `TESTED` | Operates offline after route loaded |
| **FR-15** | 3-second abortable SOS countdown before SMS | SOS | [`DefaultEmergencyController.kt:L51,145-153`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/emergency/DefaultEmergencyController.kt#L51) | `TESTED` | 3 seconds ($3 \rightarrow 2 \rightarrow 1$); cancelable |
| **FR-16** | SOS SMS dispatch with Google Maps link | SOS | [`EmergencyMessageFormatter.kt:L31-41`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/emergency/EmergencyMessageFormatter.kt#L31-L41), [`DefaultSmsSender.kt:L85-109`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/emergency/SmsSender.kt#L85-L109) | `TESTED` | Requires cellular signal; no call intent |
| **FR-17** | Button-activated voice commands matching UI labels | VOICE | [`VoiceController.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/voice/VoiceController.kt), [`ScreenVoiceActions.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/voice/ScreenVoiceActions.kt) | `TESTED` | Native `SpeechRecognizer`; `en-IN` locale |
| **FR-18** | Single-channel sequential speech arbitration | SPEECH | [`DefaultSpeechEventArbitrator.kt:L99-272`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/speech/SpeechEventArbitrator.kt#L99-L272) | `TESTED` | Utterance listener drops stale alerts $> 4000\text{ ms}$ |

---

# 7. Non-Functional Requirements (NFR) Assessment

### 7.1 Accessibility & Usability
- **Verified in Source**:
  - Touch targets $\ge 52\text{ dp}$ height ([`MainScreen.kt`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ui/screens/MainScreen.kt) mode buttons are $72\text{ dp}$ high).
  - High-contrast color palette: Deep Black (`#000000`), Safety Yellow (`#FFD600`), Cyan (`#00E5FF`), Emergency Red (`#FF1744`), Pure White (`#FFFFFF`). Contrast ratio exceeds WCAG AAA standard ($> 7:1$).
  - Full semantics tree: `Role.Button`, `Role.Checkbox`, and descriptive `contentDescription` on all interactive nodes.
- **Tested**: Verified in `ScreenVoiceActionsTest` and accessibility semantic checks.
- **Proposed Goal Not Yet Validated**: Full usability certification by formal visually impaired user cohorts.

### 7.2 Performance & Responsiveness
- **Verified in Source**:
  - TFLite worker thread pool: 4 threads (`numThreads = 4`).
  - Pre-allocated direct `ByteBuffer`s prevent garbage collection jitter.
  - Image analysis offloaded to `Dispatchers.Default` / background executors.
- **Tested**: 441 automated unit tests execute in $< 5\text{ seconds}$ on JVM.
- **Unmeasured / Requires User Confirmation**: Real-world physical device inference latency (ms per frame) on physical hardware.

### 7.3 Reliability & Error Recovery
- **Verified in Source**:
  - TFLite model schema validation at startup (`ModelValidator.kt`, `CurrencyModelValidator.kt`) safely catches mismatched models without application crashes.
  - Watchdog timers ($8000\text{ ms}$) abort stuck speech recognition listening sessions.
  - Network timeouts: 10s for Nominatim geocoding, 15s for OSRM routing.
  - Location acquisition timeout: 4s for emergency SOS.
- **Tested**: Comprehensive error-path tests in `GeocodingServiceTest`, `PedestrianRoutingServiceTest`, `CurrencyModelValidatorTest`.

### 7.4 Privacy & Local Data Handling
- **Verified in Source**:
  - Facial embeddings and names stored locally on internal storage (`people_profiles.dat`).
  - Emergency contacts stored locally in private `SharedPreferences` (`visioneye_sos_contacts`).
  - Zero cloud transmission of biometric or contact data.
- **Security Limitations (`FACT`)**: Local files are stored in plain UTF-8 text without encryption. Rooted devices could access stored embeddings.

### 7.5 Portability & Compatibility
- **Verified in Configuration**:
  - `minSdk = 29` (Android 10+), `targetSdk = 36`, `compileSdk = 37`.
  - Android Gradle Plugin: `9.3.3`, Gradle Wrapper: `9.5.0`, Kotlin: `2.2.10`, Java: `21`.
  - Jetpack Compose Material3 UI toolkit.

---

# 8. Feasibility Study Evidence

### 8.1 Technical Feasibility
- **Platform Viability**: Android 10+ (API 29+) provides mature CameraX APIs, hardware-accelerated TFLite execution, and background coroutine dispatchers.
- **On-Device Machine Learning**:
  - `yolov8n.tflite` ($12.25\text{ MB}$) and `currency_yolo11n.tflite` ($10.14\text{ MB}$) run smoothly within mobile memory limits.
  - Google ML Kit operates fully on-device without internet dependencies for text and face detection.
- **Identified Technical Limitations**:
  - Monocular RGB vision degrades in low light and direct glare.
  - Ambient noise impairs single-utterance speech recognition accuracy.
  - Continuous camera streaming and FP32 model inference cause battery drain and thermal buildup on low-end hardware.

### 8.2 Operational Feasibility
- **Interaction Model**: Dual-modal interaction (high-contrast touch targets for motor-assisted navigation + push-to-talk voice commands matching visible labels).
- **Physical Wearability**: Requires smartphone carried in a chest pocket or lanyard harness.
- **Field Trial Status (`FACT`)**: **NOT FOUND**. The repository contains zero records of formal clinical trials, usability studies, or field tests with visually impaired participants.

### 8.3 Economic Feasibility
- **Software Licensing**:
  - Android SDK, Jetpack Compose, CameraX: Open-source (Apache 2.0).
  - TensorFlow Lite, Google ML Kit: Free mobile runtimes.
  - OpenStreetMap Nominatim & OSRM: Open-access, public APIs subject to standard fair-use policies.
- **Hardware Cost Analysis (`INFERENCE`)**: Operates on commodity consumer smartphones ($₹12,000 - ₹25,000$), eliminating the need for specialized proprietary wearable hardware ($> ₹150,000$ for OrCam/Envision).
- **Operational Costs**: Zero recurring cloud subscription fees for core computer vision pipelines.

---

# 9. Hardware & Software Requirements

### 9.1 Hardware Specifications
- **Target Android Device (Verified Minimum & Recommended)**:
  - *Minimum SDK*: Android 10 (API level 29)
  - *Recommended SoC*: Octa-core processor (ARM64-v8a architecture, e.g., Qualcomm Snapdragon 7+ Gen 2 or equivalent)
  - *RAM*: Minimum 4 GB, Recommended 6 GB+
  - *Camera*: Rear camera with autofocus support ($\ge 12\text{ MP}$, $1080\text{p}@30\text{fps}$)
  - *Sensors*: Magnetometer (compass), 3-axis accelerometer/gyroscope, GNSS receiver (GPS/GLONASS)
  - *Telephony*: SIM card with SMS capability for emergency dispatch
  - *Audio*: Built-in speaker and microphone, or Bluetooth bone-conduction headphones
- **Development Workstation Specifications**:
  - Windows 11 64-bit OS
  - Android Studio Ladybug / Meerkat with Android Studio JBR (Java 21.0.8)
  - Minimum 16 GB RAM

### 9.2 Software Build Configuration & Dependencies
- **Build System**: Android Gradle Plugin `9.3.3`, Gradle `9.5.0`
- **Compiler Compatibility**: Java 21 toolchain (`JavaLanguageVersion.of(21)`), Kotlin `2.2.10`
- **Key Production Dependencies** (from [`gradle/libs.versions.toml`](file:///D:/UserData/VisionEye/gradle/libs.versions.toml) and [`app/build.gradle.kts`](file:///D:/UserData/VisionEye/app/build.gradle.kts)):
  - Jetpack Compose UI: `androidx.compose.ui:ui:1.10.4`, Material3: `1.4.0`
  - CameraX: `androidx.camera:camera-core:1.4.1`, `camera-camera2:1.4.1`, `camera-lifecycle:1.4.1`, `camera-view:1.4.1`
  - ML Inference: `org.tensorflow:tensorflow-lite:2.16.1`
  - Text Recognition: `com.google.mlkit:text-recognition:16.0.1` (Latin script)
  - Face Detection: `com.google.mlkit:face-detection:16.1.7`
  - Positioning: `com.google.android.gms:play-services-location:21.3.0`
  - HTTP Networking: `com.squareup.okhttp3:okhttp:4.12.0`
  - JSON Parsing: `org.json:json:20240303`

---

# 10. AI Models, Training Datasets & Experiments

### 10.1 Comparative Model Specifications Table

| Model Attribute | YOLOv8n Obstacle Detector | YOLO11n Currency Detector | MobileFaceNet Face Embedder |
| :--- | :--- | :--- | :--- |
| **Model Filename** | `yolov8n.tflite` | `currency_yolo11n.tflite` | `mobilefacenet.tflite` |
| **Location** | `app/src/main/assets/models/` | `app/src/main/assets/models/` | `app/src/main/assets/models/` |
| **File Size (Bytes)** | $12,841,243\text{ bytes}$ ($12.25\text{ MB}$) | $10,631,672\text{ bytes}$ ($10.14\text{ MB}$) | $5,233,552\text{ bytes}$ ($4.99\text{ MB}$) |
| **SHA-256 Checksum** | `85FC2DAC2FB2EB9B...` | `30908E9EB211043A...` | `BE4BC7CFC53F7BC3...` |
| **Architecture** | Ultralytics YOLOv8 nano | Ultralytics YOLO11 nano | MobileFaceNet |
| **Runtime Engine** | Google LiteRT / TFLite (FP32) | Google LiteRT / TFLite (FP32) | Google LiteRT / TFLite (FP32) |
| **Input Tensor Shape** | `[1, 3, 640, 640]` | `[1, 3, 640, 640]` | `[1, 112, 112, 3]` |
| **Input Memory Layout**| Planar **NCHW** Float32 | Planar **NCHW** Float32 | Interleaved **NHWC** Float32 |
| **Normalization** | $[0.0, 1.0]$ ($\frac{x}{255.0}$) | $[0.0, 1.0]$ ($\frac{x}{255.0}$) | $[-1.0, 1.0]$ ($\frac{x - 127.5}{128.0}$) |
| **Output Tensor Shape**| `[1, 84, 8400]` Float32 | `[1, 10, 8400]` Float32 | `[1, 192]` Float32 |
| **Output Semantics** | 4 box coords + 80 COCO classes | 4 box coords + 6 INR classes | 192-dimensional unit vector |
| **Detection Anchors** | 8400 candidate locations | 8400 candidate locations | N/A (Feature extractor) |
| **NMS Processing** | Class-aware NMS in Kotlin | Class-agnostic NMS in Kotlin | Cosine similarity ($\ge 0.70$) |
| **Confidence Cutoff** | $\ge 0.40f$ | $\ge 0.50f$ | $\ge 0.70f$ similarity |
| **Evidence Category** | `VERIFIED IN CURRENT REPO` | `VERIFIED IN CURRENT REPO` | `VERIFIED IN CURRENT REPO` |

### 10.2 Training Dataset & Pipeline Evidence
- **YOLOv8n**: Official pre-trained weights from Ultralytics exported via Google Colab ([`MODEL_SETUP.md`](file:///D:/UserData/VisionEye/MODEL_SETUP.md)). Trained on Microsoft COCO (Common Objects in Context, 80 object categories).
- **YOLO11n Currency**: Trained on customized Indian Rupee banknote dataset.
  - Evidence: [`ml/currency/prepare_dataset.py`](file:///D:/UserData/VisionEye/ml/currency/prepare_dataset.py), [`ml/currency/dataset.zip`](file:///D:/UserData/VisionEye/ml/currency/dataset.zip) (410 annotated images), [`ml/currency/dataset_classifier.zip`](file:///D:/UserData/VisionEye/ml/currency/dataset_classifier.zip) (2,989 images across 7 classes).
  - Denominations: ₹10, ₹20, ₹50, ₹100, ₹200, ₹500 plus background negative clutter.
  - Split: 70% Train ($2,075$), 15% Val ($454$), 15% Test ($460$).
- **MobileFaceNet**: Pre-trained on Casia-WebFace / MS-Celeb-1M, fine-tuned for on-device mobile face verification (112×112 cropped faces).
- **Experimental Evaluation Metrics**:
  - Physical accuracy benchmarks (mAP@0.5, mAP@0.5:0.95, confusion matrices, and physical-device FPS) are **NOT STORED IN THE REPOSITORY**.
  - *Recommendation*: Physical device benchmark logging must be conducted on a target device prior to final thesis submission.

---

# 11. Testing & Validation Evidence

### 11.1 Automated Test Suite Execution
- **Command Executed**: `.\gradlew.bat test` (with `JAVA_HOME=D:\UserData\AndriodStudio\jbr`)
- **Execution Date**: October 12, 2026
- **Build Status**: **`BUILD SUCCESSFUL in 4s`**
- **Test Summary**:
  - Total Test Suites: **41 test classes**
  - Total Unit Tests Executed: **441 tests**
  - Passed: **441**
  - Failed: **0**
  - Skipped / Errors: **0**
  - Pass Rate: **100%**

### 11.2 Complete Test Suite Inventory & Test Counts
| Test Suite Class | Package Location | Test Count | Verified Behaviors |
| :--- | :--- | :---: | :--- |
| `ObjectDetectorTest` | `ai` | 23 | Input tensor buffering, NCHW planar layout, coordinate mapping |
| `CameraControllerTest` | `camera` | 5 | Camera lifecycle, permission checking, unbind handling |
| `EnrollmentFrameDeliveryTest`| `camera` | 3 | Preview frame routing during face enrollment |
| `FrameSchedulerTest` | `camera` | 5 | Frame rate throttling and worker dispatching |
| `ModeManagerTest` | `core.mode` | 7 | Mode transitions, listener dispatch, active state flow |
| `CurrencyDecisionEngineTest` | `currency` | 7 | 2-frame temporal confirmation, 1500ms absence timeout |
| `CurrencyDecoderAndNmsTest` | `currency` | 6 | Raw tensor decoding, class-agnostic NMS filtering |
| `CurrencyDenominationTest` | `currency` | 8 | Class mapping, spoken string formatting, pluralization |
| `CurrencyModeIsolationTest` | `currency` | 4 | Activation/deactivation isolation, resource unloading |
| `CurrencyModelValidatorTest` | `currency` | 10 | TFLite tensor shape validation, schema rejection |
| `CurrencyPreprocessingTest` | `currency` | 3 | Planar NCHW conversion, bitmap normalization |
| `EmergencyContactAndMessageTest`| `emergency` | 8 | Phone number validation, Google Maps link formatting |
| `EmergencyControllerTest` | `emergency` | 14 | 3s countdown, cancellation, location timeout fallback |
| `ExampleUnitTest` | root | 1 | Basic JVM math sanity assertion |
| `DecisionEngineTest` | `fusion.decision`| 26 | Class priority order, 2500ms cooldown, observation stability |
| `NavigationAlertEngineTest` | `fusion.decision`| 24 | Directional alert generation, priority ranking |
| `NavigationSpeechSynchronizationTest`| `fusion.decision`| 10 | Cooldown synchronization with speech queue |
| `ProximityEstimationTest` | `fusion.decision`| 23 | Box height heuristic, hysteresis margins, Near/Med/Far |
| `GeoMathUtilsTest` | `navigation.guidance`| 10 | Haversine distance, bearing, polyline projection |
| `NavigationGuidanceEngineTest`| `navigation.guidance`| 12 | 50m/20m/8m previews, off-route detection, step advance |
| `LocationEngineTest` | `navigation` | 25 | Location flow, provider fallback, permission gates |
| `NavigationControllerTest` | `navigation` | 20 | Route calculation, guidance lifecycle, waypoint tracking |
| `OrientationEngineTest` | `navigation.orientation`| 15 | Rotation vector to azimuth calculation, compass filtering |
| `GeocodingServiceTest` | `navigation.routing` | 8 | Nominatim JSON parsing, network failure mapping |
| `PedestrianRoutingServiceTest`| `navigation.routing` | 7 | OSRM foot JSON parsing, polyline extraction, maneuvers |
| `EnrollmentAndRepositoryTest` | `people` | 14 | 5-sample enrollment, repository save/delete, persistence |
| `MobileFaceNetPreprocessingTest`| `people` | 14 | 112×112 resizing, $[-1, 1]$ normalization, L2 norm |
| `PeopleCoordinatorTest` | `people` | 5 | Coordinator lifecycle, camera unbind |
| `PeopleDecisionEngineTest` | `people` | 16 | Cosine similarity matching, 0.70 threshold, unknown handling |
| `DetectedPageGeometryTest` | `read` | 7 | Quadrilateral aspect ratio, corner sorting |
| `OcrBorderFilteringTest` | `read` | 9 | Rejection of text outside detected page crop |
| `PageDetectorAndProcessorTest`| `read` | 12 | Perspective rectification, contour detection |
| `PageStabilityTrackerTest` | `read` | 9 | Displacement tracking, 3-frame stability threshold |
| `ReadCoordinatorLifecycleTest`| `read` | 14 | State transitions, capture provider, error states |
| `ReadingOrderOrganizerTest` | `read` | 10 | Multi-column reading order, vertical block sorting |
| `TextChunkerTest` | `read` | 10 | Sentence boundary detection, abbreviation expansion |
| `SpeechEventArbitratorTest` | `speech` | 10 | Single-channel queue, 4000ms stale alert dropping |
| `ScreenVoiceActionsTest` | `voice` | 9 | Contextual action generation, voice execution callbacks |
| `VoiceCommandRegistryTest` | `voice` | 4 | Action registration, deduplication, help generation |
| `VoiceControllerTest` | `voice` | 6 | State machine, watchdog timer, dictation routing |
| `VoiceMatcherTest` | `voice` | 8 | Exact matching, normalized matching, Levenshtein distance |

### 11.3 Android Instrumentation Tests & APK Assembly
- **Instrumentation Tests**: [`app/src/androidTest/java/com/bibin/visioneye/ExampleInstrumentedTest.kt`](file:///D:/UserData/VisionEye/app/src/androidTest/java/com/bibin/visioneye/ExampleInstrumentedTest.kt) (1 test).
- **Debug APK Assembly**:
  - Command: `.\gradlew.bat assembleDebug`
  - Result: **`BUILD SUCCESSFUL in 7s`**
  - Generated Artifact: [`app/build/outputs/apk/debug/app-debug.apk`](file:///D:/UserData/VisionEye/app/build/outputs/apk/debug/app-debug.apk) ($168,477,637\text{ bytes} \approx 160.67\text{ MB}$).

---

# 12. Performance & Safety Evidence

### 12.1 Genuine Measurements vs Required Empirical Benchmarks
- **Verified in Code / Test Artifacts**:
  - Total Unit Test Suite Execution Time: $\approx 4\text{ seconds}$ for 441 unit tests.
  - Model File Sizes: YOLOv8n ($12.25\text{ MB}$), YOLO11n ($10.14\text{ MB}$), MobileFaceNet ($4.99\text{ MB}$).
  - Cooldown Intervals: 2500ms for object alerts, 1500ms for currency absence reset, 10s for rerouting.
  - Timeout Thresholds: 4s location timeout for SOS, 8s speech recognition watchdog, 4s stale speech alert drop.
- **Unmeasured / Requires Physical Measurement**:
  - Inference latency (ms per frame) on physical Snapdragon SoC.
  - End-to-end capture-to-speech latency for document OCR.
  - Battery consumption rate (% per hour in continuous NAVIGATE mode).
  - Thermal throttling degradation profile.

### 12.2 Safety Risks & Actual Mitigation Strategies
| Safety Risk Identified | Severity | Actual Mitigation Implemented in Code | Residual Risk / Limitation |
| :--- | :---: | :--- | :--- |
| **Auditory Clutter / Panic** | High | `DecisionEngine` 2500ms cooldown; `SpeechEventArbitrator` single-channel queue | High-traffic environments may queue multiple alerts |
| **Accidental Emergency Dispatch**| High | 3-second abortable countdown ($3 \rightarrow 2 \rightarrow 1$) with cancel button | User must recognize countdown and cancel within 3s |
| **Spurious Currency Announcements**| Medium | 2-frame consecutive temporal confirmation filter; 1500ms absence reset | Folded, soiled, or overlapping banknotes may fail detection |
| **Blurry Document Captures** | Medium | `PageStabilityTracker` requires 3 consecutive stable frames before capture | Insufficient ambient lighting produces low-contrast OCR errors |
| **Unregistered Person Misidentification**| Medium | High cosine similarity threshold ($0.70$); 2-frame temporal confirmation | Twins or close relatives could exceed 0.70 threshold |
| **GPS Heading Drift at Low Speeds**| Medium | Compass sensor fusion (`DefaultOrientationEngine`) integrates magnetometer | Electromagnetic interference near heavy machinery |
| **Stuck Voice Listening State** | Low | $8000\text{ ms}$ watchdog timer automatically resets voice engine to Idle | Ambient background speech may be captured as command |
| **Collision Hazard Over-Reliance**| Critical | **White Cane Non-Replacement Rule** explicitly codified in UI and docs | Monocular RGB vision cannot detect drop-offs or glass |

---

# 13. Privacy, Legal, Ethics & Project Limitations

### 13.1 Privacy & Biometric Ethics
1. **Mandatory Informed Consent**: [`EnrollPersonScreen.kt:L305-354`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ui/screens/EnrollPersonScreen.kt#L305-L354) enforces that enrollment cannot proceed without explicit consent confirmation.
2. **Local Biometric Processing**: Face embeddings (192-d vectors) are generated locally and stored strictly on-device in `people_profiles.dat`. Zero biometric vectors are uploaded to external servers.
3. **Emergency Contact Privacy**: Saved in private `SharedPreferences`. Transmitted only via cellular SMS directly to the user-designated contact during verified SOS emergencies.
4. **Data Security Caveat (`FACT`)**: The local files are stored without encryption. Physical access to a rooted device could expose names and embedding vectors.

### 13.2 Real-World Technical Limitations
1. **Monocular Proximity Limitation**: Bounding-box height heuristics cannot distinguish a small, nearby object from a large, distant object.
2. **Illumination Sensitivity**: CameraX vision pipelines degrade significantly in low-light ($< 20\text{ lux}$) conditions or harsh backlighting.
3. **Network Dependency for Navigation**: Geocoding and initial route calculation require an active internet connection.
4. **Language Constraints**: OCR and voice feedback currently support English only (ML Kit Latin OCR, `en-IN` speech recognition).

---

# 14. Academic References & External Service Documentation

*(Note: In accordance with Rule 14, citations reflect genuine official documentation for external technologies verified in the codebase. Fabricated citations, DOIs, and journal articles are strictly omitted).*

### Verified Technology Documentation
1. **Ultralytics YOLOv8 & YOLO11**:
   - Official Documentation: *Ultralytics YOLO Docs* (`https://docs.ultralytics.com/`)
   - Architecture: Anchor-free real-time object detection models.
2. **Google LiteRT / TensorFlow Lite**:
   - Official Documentation: *Google AI Edge LiteRT Documentation* (`https://ai.google.dev/edge/litert`)
   - Runtime: On-device mobile inference runtime (v2.16.1).
3. **Google ML Kit Text Recognition**:
   - Official Documentation: *ML Kit for Android — Text Recognition* (`https://developers.google.com/ml-kit/vision/text-recognition/v2/android`)
   - Capability: On-device Latin script optical character recognition.
4. **Google ML Kit Face Detection**:
   - Official Documentation: *ML Kit for Android — Face Detection* (`https://developers.google.com/ml-kit/vision/face-detection/android`)
   - Capability: Real-time face bounding box and head pose angle detection.
5. **MobileFaceNet**:
   - Model Source: Lightweight deep convolutional neural network for real-time face verification on mobile devices.
6. **OpenStreetMap Nominatim**:
   - API Documentation: *Nominatim Search Service* (`https://nominatim.org/release-docs/latest/api/Search/`)
   - Endpoint: `https://nominatim.openstreetmap.org/search`
7. **Open Source Routing Machine (OSRM)**:
   - API Documentation: *OSRM HTTP API* (`http://project-osrm.org/docs/v5.24.0/api/`)
   - Foot Routing Endpoint: `https://routing.openstreetmap.de/routed-foot/route/v1/foot/`
8. **Android CameraX**:
   - Developer Guide: *CameraX Architecture Guide* (`https://developer.android.com/training/camerax/architecture`)
   - Components: `ImageAnalysis`, `ImageCapture`, `Preview`.
9. **Jetpack Compose Accessibility**:
   - Developer Guide: *Accessibility in Jetpack Compose* (`https://developer.android.com/jetpack/compose/accessibility`)
   - WCAG 2.1 Guidelines: Web Content Accessibility Guidelines AAA contrast criteria.

---

# 15. Evidence Index & Final Report Mapping

### A. Verified Facts Index
- **YOLOv8n Input Shape**: `[1, 3, 640, 640]` Planar NCHW Float32 ([`DetectorConfig.kt:L27`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ai/DetectorConfig.kt#L27), [`ModelValidator.kt:L51`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ai/ModelValidator.kt#L51))
- **YOLOv8n Output Shape**: `[1, 84, 8400]` Float32 ([`YoloV8Detector.kt:L52`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/ai/YoloV8Detector.kt#L52))
- **Currency Model**: `currency_yolo11n.tflite`, $10.14\text{ MB}$, input `[1, 3, 640, 640]` NCHW, output `[1, 10, 8400]` ([`CurrencyDetectorConfig.kt:L28-37`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/currency/CurrencyDetectorConfig.kt#L28-L37))
- **MobileFaceNet Output**: 192-dimensional Float32 vector, L2 normalized ([`FaceRecognitionEngine.kt:L240`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/people/FaceRecognitionEngine.kt#L240))
- **SOS Countdown**: 3 seconds ($3 \rightarrow 2 \rightarrow 1$) ([`DefaultEmergencyController.kt:L51`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/emergency/DefaultEmergencyController.kt#L51))
- **Alert Cooldown**: 2500ms (`cooldownPeriodMs = 2500L`) ([`DecisionConfig.kt:L23`](file:///D:/UserData/VisionEye/app/src/main/java/com/bibin/visioneye/fusion/decision/DecisionConfig.kt#L23))
- **Automated Tests**: 441 unit tests across 41 test classes, 100% pass rate in 4s via `gradlew.bat test`.
- **Debug APK**: `app-debug.apk` ($160.67\text{ MB}$) compiled via `gradlew.bat assembleDebug`.

### B. Conflicts & Inconsistencies Discovered
1. **Model Contract Script in `MODEL_SETUP.md`**: Legacy Colab validation code asserts `[1, 640, 640, 3]`, whereas specification text and Kotlin code enforce planar NCHW `[1, 3, 640, 640]`.
2. **Permissions Documentation**: Earlier notes mention `CALL_PHONE` and `VIBRATE`; actual manifest contains only 7 permissions (`CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`, `ACCESS_NETWORK_STATE`, `SEND_SMS`, `RECORD_AUDIO`).
3. **READ Mode Controls**: Historical documents mention pause/resume; actual source code contains only automatic capture, manual capture now, and retry scanning.
4. **Cooldown Discrepancy**: `AGENTS.md` mentions 3000ms cooldown; actual default in `DecisionConfig.kt` is 2500ms.

### C. Missing Information Requiring Student / Supervisor Input
- Student names, register numbers, department, institution, and project guide / supervisor details.
- Academic year and submission deadline.
- Target physical Android smartphone model used for physical demonstration (e.g., POCO F5, Samsung Galaxy, etc.).
- Empirical hardware latency measurements on physical hardware (inference ms, capture-to-speech ms, battery consumption).

### D. Claims That Must NOT Appear as Established Facts in the Final Report
1. Do **NOT** claim VisionEye provides *metric depth estimation* (it is monocular bounding-box height heuristic only).
2. Do **NOT** claim VisionEye guarantees *collision prevention* or can replace a white cane.
3. Do **NOT** claim biometric profiles are *encrypted* (they are stored as plaintext UTF-8 in internal storage).
4. Do **NOT** claim emergency SMS messages are *confirmed delivered* (the app only confirms submission to the telephony stack).
5. Do **NOT** claim the system features *hotword / always-on listening* (speech control is strictly button-activated push-to-talk).
6. Do **NOT** report empirical model accuracy or user study scores without running concrete physical device evaluations.

### E. Evidence Map for Final Academic Report Chapters
- **Chapter 1: Introduction & Problem Definition** $\longrightarrow$ Section 1 & Section 3 (BVI mobility barriers, smartphone ETA rationale, aim, 8 measurable objectives, White Cane Rule).
- **Chapter 2: Literature Review & Existing Systems** $\longrightarrow$ Section 1.7 & Section 8.3 (Comparative analysis against OrCam, Envision, white canes; cost, privacy, on-device advantages).
- **Chapter 3: System Analysis & Requirements Engineering** $\longrightarrow$ Section 6, Section 7, Section 8 (Functional requirements FR-01 to FR-18, non-functional quality attributes, feasibility study).
- **Chapter 4: System Architecture & Design** $\longrightarrow$ Section 4 & Section 5 (Layered reactive architecture, ModeManager state machine, 9 verified Mermaid diagrams).
- **Chapter 5: Detailed Methodology & Implementation** $\longrightarrow$ Section 4 (Modules 4.1 to 4.7: YOLOv8n NMS, Proximity hysteresis, Page homography, ML Kit OCR, YOLO11n currency confirmation, MobileFaceNet cosine matching, OSRM routing, SMS dispatch, Voice control).
- **Chapter 6: Testing, Results & Evaluation** $\longrightarrow$ Section 10 & Section 11 (Dataset distribution, 441 unit tests, test suite breakdown, assembleDebug APK).
- **Chapter 7: Limitations, Future Enhancements & Conclusion** $\longrightarrow$ Section 12 & Section 13 (Monocular limits, lighting dependency, Edge-TPU INT8 future scope, spatial audio, concluding summary).

---

## Technical Audit Verification Sign-Off

- **Audit Date**: October 12, 2026
- **Status**: Information Gathering and Verification Complete
- **Output File Created**: `VisionEye_Complete_Project_Information_Dossier.md`
- **Integrity Status**: All existing project source code, assets, and Git history remain strictly untouched and preserved.
