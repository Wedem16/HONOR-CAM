# Honor Cam

**Honor Cam** is a full-featured HONOR-inspired Android camera application built with Kotlin, Jetpack Compose, Material 3, CameraX (with Extensions and Camera2 Interop), ML Kit Document Scanner, and OpenAI image enhancement.

## Features

- **HONOR-Style Camera UI**: Portrait-only dark theme with `#FFA800` accent color, top quick-toggle bar, translucent quick-settings panel, tap-to-focus ring, pinch-to-zoom, supported zoom pills (`0.6x`, `1x`, `1.5x`, `2x`, `5x`), rule-of-thirds grid, accelerometer level indicator, countdown overlay, recording timer, mode strip, MORE modes drawer, latest media thumbnail viewer, and optional draggable floating shutter (+ hardware volume button shutter).
- **Capture Modes**:
  - **NIGHT / PORTRAIT / PHOTO (with HDR)**: Powered by CameraX Extensions (`ExtensionMode.NIGHT`, `ExtensionMode.BOKEH`, `ExtensionMode.HDR`) with automatic fallback and user notice if unsupported on the device. Saves to `DCIM/AiCam`.
  - **VIDEO**: `Recorder` + `VideoCapture` saving to `Movies/AiCam` with cycling specs (`720p·30`, `1080p·30`, `1080p·60`, `4K·30`, `4K·60`), Camera2 target FPS range, low-light Auto FPS (`24..fps`), AWB Lock, Mute, adjustable bitrate (`0–200 Mbps`), and **Standard / HDR (HLG 10-bit) / Log (flat custom contrast curve)** profiles with optional Log preview assist.
  - **PRO (Photo & Video switch)**: Live Camera2 controls via `Camera2CameraControl` without rebinding — log-scale ISO slider (`sensorMin` up to `sensorMax * boostMax`, capped at `409600`), Shutter speed (`1/8000s` to `1s` for photo, capped at `1/60s` with pinned frame duration for video), WB presets (Auto, Incandescent, Fluorescent, Daylight, Cloudy), Manual focus distance, and per-control Auto chips.
  - **MORE Sub-Modes**:
    - **UNDERWATER**: Applies a red-channel boost & water color compensation `ColorMatrix` after capture.
    - **SUPER MACRO**: Locks autofocus off (`CONTROL_AF_MODE_OFF`) and pins focus distance to `LENS_INFO_MINIMUM_FOCUS_DISTANCE`.
    - **HIGH-RES**: Uses `ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY` with `PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE`.
    - **SCAN DOCUMENT**: Launches Google ML Kit `GmsDocumentScanning` (`JPEG` + `PDF`) and saves scanned PDFs to `Documents/AiCam` and page JPEGs to `DCIM/AiCam`.
- **Pro Luma Assists (`Assist.kt`)**: Runs on `ImageAnalysis` Y-plane every 3rd frame (respecting `cropRect` and `rotationDegrees`) to compute a 64-bin histogram, zebra stripes (`luma >= 240`), and focus peaking (luma Sobel gradient) rendered as a live overlay bitmap.
- **Post-Processing & AI Enhance (`Post.kt`)**:
  - EXIF-aware photo filters (`Original`, `Vivid`, `Mono`, `Warm`, `Cool`, `Sepia`) plus Underwater color restoration.
  - Optional OpenAI `/v1/images/edits` multipart enhancement (`gpt-image-1` default, downscaled to `2048px`, `output_format=jpeg`) saved as `*_AI.jpg` in `DCIM/AiCam` with "Keep original photo with AI Enhance" support.

## Building the Project

### Option 1: Android Studio
1. Open the root project folder in **Android Studio**.
2. Ensure **JDK 21** is selected in **Settings → Build, Execution, Deployment → Build Tools → Gradle**.
3. Wait for Gradle Sync (`Gradle 9.3.1`) to complete.
4. (Optional) Configure `OPENAI_API_KEY` in `.env` (or in the in-app **Settings → AI Camera** screen).
5. Click **Run** (`Shift+F10`) to install on a physical Android 10+ (API 29+) device, or run:
   ```bash
   gradle assembleDebug
   ```
   The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Option 2: GitHub Actions
1. Push the repository to GitHub on the `main` or `master` branch (or trigger the workflow manually from the **Actions** tab).
2. The workflow in `.github/workflows/build.yml` automatically provisions **JDK 21** and **Gradle 9.3.1**, creates a standard `debug.keystore` for CI signing if not present, executes `gradle assembleDebug`, and uploads `app-debug.apk` as a downloadable build artifact.
