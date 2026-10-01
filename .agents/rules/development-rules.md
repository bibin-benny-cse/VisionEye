# VisionEye Development Rules & Guidelines

## Project Purpose
VisionEye is a smartphone-based AI assistance system for visually impaired users.

## Primary Architecture
The system uses a mode-based architecture.

- **Default mode**: `NAVIGATE`
- **Available modes**:
  - `NAVIGATE`
  - `READ`
  - `CURRENCY`
  - `PEOPLE`
  - `NAVIGATION`
  - `SOS`

## Development Principles
- Use Kotlin.
- Use Jetpack Compose for UI.
- Keep camera processing separate from UI.
- Keep AI models behind interfaces.
- Do not run every AI model continuously.
- Heavy inference must not run on the UI thread.
- Prefer on-device processing where practical.
- Keep modules independent.
- Avoid unnecessary dependencies.
- Test every feature on a physical Android device.
- Build after significant changes.
- Do not claim functionality works without testing.

## Safety & Ethics
- VisionEye is an assistive prototype.
- It is not a replacement for a white cane and must not be described as a guaranteed safety system.
- Face recognition must require consent during enrollment.
- Face information should be stored locally where possible.
