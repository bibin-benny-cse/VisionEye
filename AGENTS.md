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

## Operational Modes (`VisionMode`)

| Mode | Default? | Purpose | Sensors & AI Required |
| :--- | :---: | :--- | :--- |
| **`NAVIGATE`** | **Yes** | Real-time obstacle detection, depth estimation, path clearance | Camera feed, Depth/Obstacle AI model, Sensor fusion |
| **`READ`** | No | Optical Character Recognition (OCR) for signs, text, and documents | Camera (snapshot/low-fps), Text recognition (OCR) |
| **`CURRENCY`** | No | Indian banknote denomination recognition | Camera (snapshot/on-demand), Currency classification model |
| **`PEOPLE`** | No | Familiar and registered face recognition | Camera feed, Face detection & feature matching model |
| **`NAVIGATION`** | No | Pedestrian outdoor wayfinding and turn-by-turn guidance | GPS, Fused Location Provider, Compass / IMU |
| **`SOS`** | No | Emergency assistance alert, location broadcast, and contact dialing | Cellular / SMS, GPS location |

`VisionMode.NAVIGATE` is the default startup mode.

---

## Package Structure & Architecture

All application code resides under `com.bibin.visioneye`:

```
app/src/main/java/com/bibin/visioneye/
├── VisionEyeApplication.kt               # Central Application class; instantiates ModeManager
│
├── core/                                 # Foundational abstractions and mode state
│   ├── mode/
│   │   ├── VisionMode.kt                 # Enum containing all 6 modes and metadata flags
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
├── fusion/                               # Multi-sensor fusion
│   └── SensorFusionCoordinator.kt        # Combines visual, depth, and motion data into PerceptionEvents
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
2. **Minimal Dependencies**: Keep dependencies focused. Do not import heavy frameworks unless strictly required for a sub-pipeline.
3. **Preserve Generated Android Files**: Do not overwrite Gradle wrappers or SDK configurations unnecessarily.
4. **Always Test Before Committing**: Run `gradlew.bat test assembleDebug` and verify `BUILD SUCCESSFUL` before reporting task completion.
