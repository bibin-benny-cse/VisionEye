# VisionEye — Agent & Developer Guide

## Project Overview
**VisionEye** is an Android smartphone application designed to provide real-time AI assistance for visually impaired users. It uses on-device computer vision, sensor fusion, spatial awareness, and audio feedback to enhance user mobility, independence, and safety.

---

## Non-Negotiable Architectural Principles

### 1. Mode-Based Model Execution
> **CRITICAL RULE**: Do **NOT** run all AI models continuously.
- Running simultaneous object detection, depth estimation, OCR, and facial recognition leads to severe thermal throttling, frame drops, and battery exhaustion.
- The application follows a strict **mode-based architecture** managed by `ModeManager`.
- Subsystems implement `ModeAwareComponent` and **must activate only when the active `VisionMode` demands their specific workload**. When a mode is exited, corresponding sensor captures and AI model workers must be paused or unloaded.

---

## Detailed Operational Specifications

### 1. Navigate Mode (Default)
- **Status**: Default mode on application launch.
- **Input**: Camera frames.
- **Primary Processing**:
  - Real-time Object Detection
  - Monocular/Metric Depth Estimation
  - Optional Walkable-Path Analysis
- **Output**: Short prioritized voice alerts.
- **Sample Utterances**:
  - *"Person ahead."*
  - *"Vehicle on your left."*
  - *"Obstacle close."*

---

### 2. Read Mode
- **Voice Command**: `"Read"`
- **Pipeline**:
  $$\text{Camera} \longrightarrow \text{OCR} \longrightarrow \text{Text Extraction} \longrightarrow \text{Text-to-Speech (TTS)}$$
- **Behavior**: Single-snapshot or low-frame-rate capture. Aligns and recognizes printed documents, storefront signs, and labels.

---

### 3. Currency Mode
- **Voice Command**: `"Currency"`
- **Pipeline**:
  $$\text{Camera} \longrightarrow \text{Currency Recognition Model} \longrightarrow \text{Denomination} \longrightarrow \text{Text-to-Speech (TTS)}$$
- **Behavior**: Optimized on-device classification for Indian Rupee (INR) banknotes (₹10, ₹20, ₹50, ₹100, ₹200, ₹500).

---

### 4. People Mode
- **Voice Command**: `"Who is this?"`
- **Pipeline**:
  $$\text{Camera} \longrightarrow \text{Face Detection} \longrightarrow \text{Face Embedding} \longrightarrow \text{Local Registered Comparison} \longrightarrow \text{Text-to-Speech (TTS)}$$
- **Behavior**: Local on-device vector comparison against registered family/friend face profiles.

---

### 5. Navigation Mode
- **Voice Command**: `"Go to <destination>"`
- **Pipeline**:
  $$\text{Voice Command} \longrightarrow \text{Destination Resolution} \longrightarrow \text{Route Guidance} \longrightarrow \text{Spoken Navigation}$$
- **Independence Principle**: Camera-based obstacle detection remains independent and active in parallel when safety demands.

---

### 6. SOS Mode
- **Triggers**:
  - Voice command (*"SOS"* / *"Emergency"*)
  - Long press on screen / physical button
- **Pipeline**:
  $$\text{Trigger} \longrightarrow \text{Current GPS Location} \longrightarrow \text{Emergency Message} \longrightarrow \text{Emergency Contact Alerting}$$
- **Behavior**: Initiates immediate visual/auditory countdown with quick cancel option, followed by automated SMS and contact dialing.

---

### 7. Decision Engine & Alert Prioritization

The **Decision Engine** (`fusion/decision/DecisionEngine.kt`) arbitrates sensory information, resolves conflicts between simultaneous events, and determines the voice delivery schedule.

#### Priority Hierarchy (Strict Order)
$$\textbf{Collision Warning} > \textbf{Immediate Obstacle} > \textbf{Navigation Instruction} > \textbf{General Object Information}$$

| Priority Level | Rank | Typical Event | Example Alert |
| :--- | :---: | :--- | :--- |
| **`COLLISION_WARNING`** | 4 | Imminent collision or fast-approaching vehicle | *"Stop! Vehicle approaching on left."* |
| **`IMMEDIATE_OBSTACLE`** | 3 | Direct obstruction in walking path | *"Obstacle close."*, *"Person ahead."* |
| **`NAVIGATION_INSTRUCTION`**| 2 | Route guidance instruction | *"In 20 meters, turn right."* |
| **`GENERAL_OBJECT_INFO`** | 1 | Background or non-urgent object description | *"Bench on right."* |

#### Cooldown & Suppression Logic
- **Repetition Suppression**: Identical or duplicate alerts (keyed by semantic tag) are suppressed within a configurable cooldown period (`cooldownPeriodMs`, default: 3–4 seconds).
- **Preemption**: Higher-priority alerts immediately preempt lower-priority speech.
- **Safety Bypass**: `COLLISION_WARNING` events bypass standard cooldown to ensure immediate user protection.

---

## Package Structure & Architecture

All application code resides under `com.bibin.visioneye`:

```
app/src/main/java/com/bibin/visioneye/
├── VisionEyeApplication.kt               # Central Application class; instantiates ModeManager
│
├── core/                                 # Foundational abstractions and mode state
│   ├── mode/
│   │   ├── VisionMode.kt                 # Enum containing all 6 modes, voice triggers, & pipelines
│   │   └── ModeManager.kt                # ModeManager interface & DefaultModeManager (StateFlow + listeners)
│   └── contract/
│       └── ModeAwareComponent.kt         # Contract enforcing mode-based activation/deactivation
│
├── ui/                                   # Presentation layer (Jetpack Compose + Material3)
│   ├── MainActivity.kt                   # Single Activity hosting Compose content
│   ├── VisionEyeApp.kt                   # Root Composable wiring ModeManager state
│   ├── screens/
│   │   └── MainScreen.kt                 # Accessible screen with high-contrast UI and mode switcher
│   └── theme/
│       ├── Color.kt                      # WCAG AAA accessible high-contrast color palette
│       ├── Theme.kt                      # VisionEye Dark Theme
│       └── Type.kt                       # Accessible, large typography
│
├── camera/                               # Camera acquisition pipeline (CameraX)
│   └── CameraController.kt               # Mode-aware camera lifecycle controller interface & CameraState
│
├── ai/                                   # Machine learning inference pipelines
│   └── AiModelManager.kt                 # Mode-aware model coordinator; unloads inactive models
│
├── fusion/                               # Multi-sensor fusion & arbitration
│   ├── SensorFusionCoordinator.kt        # Combines visual, depth, and motion data into PerceptionEvents
│   └── decision/
│       └── DecisionEngine.kt             # Alert prioritization engine and repetition cooldown tracker
│
├── speech/                               # Audio & Voice feedback
│   └── SpeechController.kt               # Text-to-Speech (TTS) priority queue and mode announcements
│
├── navigation/                           # GPS wayfinding
│   └── NavigationController.kt           # Mode-aware pedestrian route guidance and NavigationState
│
├── emergency/                            # SOS alert handling
│   └── EmergencyController.kt            # Mode-aware SOS pipeline, countdown, and EmergencyState
│
└── data/                                 # Data storage and preferences
    ├── model/
    │   └── UserPreferences.kt            # Accessibility and application configuration data class
    └── repository/
        └── SettingsRepository.kt         # Repository contract for persisting user preferences
```

---

## Accessibility Standards (UI & UX)

Because this app serves visually impaired users:
1. **TalkBack & Semantics**: Every interactive element must provide explicit `contentDescription` and semantic `Role`.
2. **High Contrast**: Maintain high-contrast visual elements (WCAG AAA compliant). Deep black background (`#000000`) with vivid accents (Yellow `#FFD600`, Cyan `#00E5FF`, Red `#FF1744` for SOS).
3. **Touch Targets**: Minimum touch target size must be **at least 48dp × 48dp** (current mode buttons are 72dp high with full-width tap targets).
4. **Haptic & Audio Feedback**: Mode switches and safety alerts must trigger immediate feedback (auditory announcements and vibration).

---

## Build & Development Environment

### Prerequisites
- **Language**: Kotlin 2.2+
- **UI Toolkit**: Jetpack Compose (Material3)
- **Android Gradle Plugin (AGP)**: 9.3+
- **Compile SDK**: 37 (`compileSdk = release(37)`)
- **Min SDK**: 29 (Android 10+)
- **Target SDK**: 36
- **Java**: Java 21 (uses Android Studio JBR located at `D:\UserData\AndriodStudio\jbr`)

### Gradle Commands (Windows Terminal / PowerShell)
Always ensure `JAVA_HOME` is pointed to the Android Studio JBR before running Gradle commands:

```cmd
:: Set Java Home to Android Studio JBR
set JAVA_HOME=D:\UserData\AndriodStudio\jbr

:: Build debug APK
gradlew.bat assembleDebug

:: Run unit tests
gradlew.bat test

:: Run both tests and assembly
gradlew.bat test assembleDebug
```

---

## Conventions for Future Changes
1. **No Continuous Execution**: Never start a camera analysis loop or neural network forward pass that runs independently of the active `VisionMode`.
2. **Alert Prioritization**: All audio announcements must pass through the `DecisionEngine` to ensure safety alerts preempt informational messages and prevent alert fatigue.
3. **Minimal Dependencies**: Keep dependencies focused. Do not import heavy frameworks unless strictly required for a sub-pipeline.
4. **Always Test Before Committing**: Run `gradlew.bat test assembleDebug` and verify `BUILD SUCCESSFUL` before reporting task completion.
