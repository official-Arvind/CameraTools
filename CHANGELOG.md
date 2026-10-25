# 📜 Changelog

All notable changes to the **CameraTools** project are documented in this file.

---

## [v1.3.2] - 2026-10-25 (AOSP Crash Fix, Leica True Tuning & Feature Polish)

### 🐛 Critical Bug Fixes & System Stability
* **AOSP ROM Missing Resource Crash Fix (Issue #7)**:
  * Resolved fatal `android.content.res.Resources$NotFoundException: String resource ID #0x7f130379` when opening Camera Settings on Evolution X, LineageOS, crDroid, and other custom AOSP ROMs lacking MIUI framework resource overlays.
  * Implemented resilient dynamic fallback mapping in `hookAospResourceFixes`: safely intercepts `Resources.getString(int)` and `getText(int)` for missing Leica string IDs (such as `0x7f130379` -> `"Leica"`), ensuring camera settings opens seamlessly on any custom ROM without crashing.

### 🎙️ Audio Subsystem Polish & State Synchronization
* **Audio Zoom Toggle & Active Indicator Glow Fix**:
  * Resolved issue where tapping the Directional Audio Zoom button in camera UI failed to toggle state or display active visual glow.
  * Eliminated method collision where `isTeleX` hook in `hookFocusPeakingZebra` was inadvertently intercepting audio zoom feature checks.
  * Explicitly initialized `AiAudioParameterManager.sSupported = true` via reflection and hooked `ComponentRunningAiAudioNew.isSwitchOn(int mode)` and `ComponentRunningAiAudioNew.getResIcon(boolean isNormal)` so the audio zoom toggle updates cleanly and displays the active yellow glow indicator.

### 🔴 Leica Color Science & Tuning Realism
* **True Leica Authentic vs. Leica Vibrant Separation**:
  * Fixed bug where switching between Leica Authentic and Leica Vibrant produced identical images due to `CameraSettings.getCvType()` clamping custom cvType values to `0`.
  * Hooked `CameraSettings.getCvType()` to return the actual user-selected Leica tuning mode (`1` for Leica Authentic, `2` for Leica Vibrant).
  * Enables MIUI Camera's GL rendering pipeline to dynamically bind distinct color grading matrices, contrast S-curves, and peripheral vignette shaders for authentic Leica character.

### 🛡️ Strict Mode Conflict Isolation & Guard Architecture
* **Movie Frame (MiMovie 2.39:1) Isolation**:
  * Strictly prevented 2.39:1 anamorphic crop from injecting into incompatible modes: blocked in Portrait (171), Night (173), Panorama (166), Slow Motion (168/172), and Document (186) modes. Eliminates sensor aspect ratio stretching, crop distortion, and preview freezes.
* **50MP Normal Photo Guarding**:
  * Strictly isolated 50MP hardware remosaicing to standard Photo mode (163) on Camera ID `0` (main rear sensor), completely preventing bokeh depth corruption or green tinting in Portrait mode.
* **Pro Shutter & Video LOG Mode Isolation**:
  * Confined extended shutter speed (up to 60s) strictly to Pro Photo (167) and Pro Video (180).
  * Confined flat LOG color profile strictly to Pro Video (180).

### 🎞️ Pro RAW Bayer Sensor Hardware Documentation
* **Dual-File Capture Architecture**:
  * Clarified why shooting RAW in Pro mode produces two files: a `.jpg` (ISP-processed preview with color tuning) and a `.dng` (uncompressed raw sensor data with full dynamic range) per the Android Camera2 specification.
* **12.5MP Native Quad-Bayer Readout**:
  * Documented that on the MediaTek Dimensity 1080 (MT6877 / Sony IMX766), the MediaTek Camera HAL exposes RAW at the native 12.5MP 4-in-1 binned Bayer resolution (`RAW_SENSOR` 32). Recommended Adobe Lightroom / Photoshop with custom color profiles for optimal DNG post-processing.

---

## [v1.3.1] - 2026-10-22 (Security & Code Quality Release)

### 🛡️ Code Scanning, Security & Code Quality Hardening
* **CodeQL Relative Command Execution Hardening (CWE-78 / Alerts #7 & #8)**:
  * Implemented secure absolute binary path resolution (`getSuPath()`) querying trusted root binary paths (`/system/bin/su`, `/system/xbin/su`, `/sbin/su`, `/data/adb/ksu/bin/su`, `/data/adb/ap/bin/su`), completely resolving PATH environment variable hijacking risks.
* **Android Application Backup Security Hardening (Alert #4)**:
  * Enforced `android:allowBackup="false"` in `AndroidManifest.xml` to prevent unauthorized ADB data backup extraction of camera system preferences.
* **GitHub Actions Supply Chain Hardening (Alert #1)**:
  * Pinned `softprops/action-gh-release` in `.github/workflows/mirror-release.yml` to immutable commit SHA `c95fe1489396fe8a9eb87c0abf8aa5b2ef267fda` (`v2.2.1`), resolving unpinned action warnings.
* **Dependabot & Transitive Build Dependency Audit**:
  * Cleared and dismissed all legacy build-time transitive alerts; updated Android Gradle Plugin to `8.7.3` and verified zero runtime attack surface in release APK.
* **Gradle Build Tooling Modernization**:
  * Upgraded Android Gradle Plugin to `8.7.3` in `build.gradle.kts` and synced `app/build.gradle.kts` to version `1.3.1` (code `131`).

---

## [v1.3.0] - 2026-10-22 (Official Release)

### 🐛 Critical Bug Fixes & Independent Audit Verification
* **Portrait Mode Green Output Fix**:
  * Mode isolation in `alwaysUseRemosaicSize`, `spoofBackendHook` (`isUltraPixelOn`), and `sizeListHook`.
  * Mode 171 (Portrait Mode) is strictly excluded from 50MP hardware remosaicing, ensuring ArcSoft dual-camera bokeh engine receives clean 12.5MP ($4096 \times 3072$) frames without chroma corruption (eliminating the neon green tint).
* **Pro RAW (DNG) Native Bayer Capture**:
  * Clarified and verified that the MediaTek MT6877 Dimensity 1080 HAL exposes a native uncompressed 12.5MP ($4096 \times 3072$) Bayer RAW stream (`RAW_SENSOR` format 32).
  * Prevented camera daemon crash on launch by isolating 50MP stream injection strictly to JPEG hardware remosaic (`format 256`), ensuring rock-solid stability when shooting RAW.
* **Leica Authentic/Vibrant Profiles & Watermark Fix**:
  * Resolved missing Leica button in top toolbar by hooking `o0O0OOoo` and `o000OOoO` on `DataItemFeature` and properly initializing `MiThemeCompatCV`.
  * Hooked `ComponentConfigCvType` ensuring Leica Authentic and Leica Vibrant options are always present in the top bar and settings.
* **Zoom Slider Glitch Resolution**:
  * Fixed method collision on `DataItemFeature` where `o000OO00(int mode)` (`float[]` optical zoom ratios) was incorrectly receiving a String return value, causing `0 0 0 0` and integer overflow buttons in the preview slider.
  * Added strict reflection return-type safety guards on all method hooks, restoring clean, centered `0.6X`, `1.0X`, and `2.0X` optical zoom buttons.
* **Modern Flagship App Logo**:
  * Redesigned application launcher and adaptive icon with precision-engineered mechanical aperture blades, multi-coated optic glass reflections, cyan neon accent ring, and signature Leica ruby badge.

### 🌟 New Features & Hardware Unlocks (15 Total)
* **Front Camera 4K Video (30FPS & 60FPS)**:
  * Injected 4K 30FPS (quality 8) and 4K 60FPS (quality 2108) for the 16MP front selfie camera in `init4K` and `getSupportedVideoQualityList`.
  * Injected $3840 \times 2160$ output sizes into `StreamConfigurationMap.getOutputSizes` for `MediaRecorder` and `SurfaceTexture`.
* **Pro Video LOG Profile**:
  * Unlocked flat LOG color profile in Pro Video mode (`isSupportedVideoLogFormat`) for maximum dynamic range color grading.
* **Cinematic MiMovie Mode (2.39:1)**:
  * Unlocked 2.39:1 anamorphic movie aspect ratio and cinematic letterbox guidelines (`isSupportedVideoMiMovie` & `isSupportedCinematicVideo`).
* **Video Teleprompter**:
  * Unlocked native on-screen floating teleprompter script tool (`o0O0o0o` / `videoPrompterSupported`).
* **Focus Peaking & Zebra Exposure Assist**:
  * Unlocked real-time colored focus peaking outlines on in-focus edges and zebra stripes on overexposed highlights in Pro mode (`o0O0ooOO` / `isSupportDepthFocusForMTK` / `isTeleX`).
* **AI Motion Tracking Focus**:
  * Unlocked continuous AI tracking focus for human eyes, faces, and pets (`isSupportTrackEye`, `isSupportTrackFocus`, `isTrackFocusDefined`) across photo and video modes.
* **Document Scanner Mode**:
  * Unlocked native document and ID scanner mode with auto-perspective deskew and contrast enhancement (`DocModuleEntry.support`, `o00o0oO0`, Mode 186 injected into `DataItemGlobal.getSortModes()`).
* **Directional Audio Zoom & Bluetooth Headset Mic**:
  * Unlocked Bluetooth earbud microphone recording (`isSupportBluetoothHeadsetMic`) and 3-mic directional audio zoom (`isSupport3MicEnable`, `isSupportAiAudioNew`, `o00o0000`).

### 📱 Polished User Interface Redesign
* Completely updated UI with 15 user-friendly cards featuring simple, clear, non-technical descriptions for everyday users.
* Added individual toggles for all 15 capabilities, dynamic status badges, and single-tap "Enable All / Disable All" and "Restart Camera" buttons.
* Seamless background preference synchronization across LSPosed API 93, ContentProvider IPC, and Root sync.

### 🌐 Universal AOSP ROM & Multi-Camera Compatibility
* **Universal Android Framework (`android.hardware.camera2` & `android.media`) Hooks**:
  * Added `hookFrameworkLevel`: hooks `CameraCharacteristics.get` for exposure range and 4K60 FPS ranges.
  * Added `PowerManager.getCurrentThermalStatus` hook returning `THERMAL_STATUS_NONE` (0) across all camera apps.
  * Added universal `MediaRecorder.setVideoEncodingBitRate` hook (150 Mbps) for AOSP Camera2 and Open Camera.
  * Added `CamcorderProfile.hasProfile` hook for 4K 60FPS.
* **Multi-Camera Package Scope**:
  * Expanded scope from `com.android.camera` to 13 camera applications including GCam ports (`com.google.android.GoogleCamera`, `MGC`, `AGC`, `Shamim`), `org.lineageos.aperture`, `app.grapheneos.camera`, and `net.sourceforge.opencamera`.
* **Dynamic ROM Environment Detection**:
  * Implemented `RomDetector` to inspect Android system properties (`ro.miui.ui.version.name`, `ro.mi.os.version.name`, `miui.os.Build`) and apply OEM vs Framework hooks conditionally.
* **50MP Normal Photo Fixes**:
  * Replaced stack trace inspection with cleaner caller verification to avoid performance overhead in camera preview loop.
  * Fixed immutable `List` crash when injecting 50MP resolutions into `CameraSize` lists.
* **Modern Material UI Redesign**:
  * Redesigned `MainActivity` layout with dark aesthetic, status indicators, and edge-to-edge window insets.

---

## [v1.0.0] - 2026-09-09

### 🎯 Initial Release for Redmi Note 12 Pro 5G (`ruby` / MT6877)
* **4K 60FPS Video Recording**: Unlocked 60 FPS recording at 3840x2160 resolution on the Sony IMX766 sensor.
* **150 Mbps Cinematic Bitrate**: High-bitrate video capture for 4K60 and 4K30 recording.
* **50MP Bayer RAW Capture**: DNG RAW capture from the native un-binned 50MP sensor layout.
* **Leica Authentic & Vibrant Profiles**: Unlocked Leica color profiles and watermarks.
* **Director Mode (Dual-Camera Stream)**: Concurrent front and rear video recording.
* **Extended Pro Exposure Range**: Manual exposure range from 60 seconds to 1/16000s.
* **Thermal Warning & Shutdown Bypass**: Complete bypass of artificial high-temperature camera shutdown dialogs.
