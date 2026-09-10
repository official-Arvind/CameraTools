# CameraTools (com.jigar.cameratools)

[![GitHub Release](https://img.shields.io/badge/release-v1.1.0-blue.svg)](https://github.com/official-Arvind/CameraTools/releases)
[![Target](https://img.shields.io/badge/device-Redmi%20Note%2012%20Pro%205G%20(ruby)-green.svg)](https://github.com/official-Arvind/CameraTools)
[![Platform](https://img.shields.io/badge/framework-LSPosed%20%7C%20Vector%20Framework-purple.svg)](https://github.com/official-Arvind/CameraTools)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**CameraTools** (`com.jigar.cameratools`) is an all-in-one Camera & ISP Subsystem enhancement module and configuration manager engineered specifically for the **Xiaomi Redmi Note 12 Pro 5G** (`ruby`, MediaTek Dimensity 1080 / MT6877, Sony IMX766 50MP OIS sensor).

It unlocks hardware ISP capabilities, removes software gating in MIUI / HyperOS Camera (`com.android.camera`), and gives users full control via a modern dark-mode Material UI dashboard.

---

## ⚡ Features & Subsystems

| Feature | Subsystem Hook | Description |
|---|---|---|
| ⚡ **4K 60FPS Video Unlock** | `ComponentConfigVideoQuality`, `ComponentConfigVideoSubFPS` | Bypasses MediaTek HAL streaming resolution limits and adds the native 60 FPS toggle switch for 3840×2160 video recording. |
| 🎬 **Cinematic Ultra Bitrate** | `com.mi.device.Ruby`, `com.mi.device.Common.o0ooOO0` | Unlocks 150 Mbps master-grade HEVC encoding for 4K video (default: ~42 Mbps) and 60 Mbps for 1080p, eliminating compression artifacts and macroblocking. |
| 📸 **50MP Ultra RAW (DNG)** | `CameraCapabilitiesUtil.isSupportUltraPixelRaw`, `Ruby.OooOo00` | Enables full 50-megapixel uncompressed RAW (.dng) sensor dumps in Pro Mode directly from the Sony IMX766 Quad-Bayer array. |
| 🔴 **Leica Color Profiles & Watermark** | `CameraCapabilitiesUtil.isSupportCvType`, `isSupportCvWatermark` | Activates Leica Authentic and Leica Vibrant color science tuning profiles, Leica optics simulation, and the branded Leica watermark. |
| 🎥 **Dual-Camera Concurrent Stream** | `DataItemFeature.isSupportDualVideo`, `CameraCapabilitiesUtil` | Unlocks Director Mode: simultaneous front camera (OmniVision OV16A1Q) and rear camera (Sony IMX766) multi-stream recording. |
| ⏱️ **Extended Pro Exposure Range** | `CameraCapabilitiesUtil.getExposureTimeRange` | Expands exposure range up to 60 seconds for astrophotography and up to 1/16000s ultra-fast electronic shutter. |
| ❄️ **Bypass Thermal Warning & Shutdown** | `ThermalDetector`, `ThermalOverheatMultipleASD`, `Camera.dealThermal` | Completely bypasses MIUI / HyperOS thermal alerts, video recording overheat toasts, and forced camera shutdown. Camera never forcefully closes due to high temperatures. |

---

## 📱 Application Dashboard

The companion app provides interactive toggle cards for each individual feature, real-time preference synchronization via `ContentProvider` IPC across Android 14 process boundaries, and a single-click **Restart Camera** root action button.

```
┌────────────────────────────────────────────────────────┐
│             CameraTools Settings UI                    │
│           (com.jigar.cameratools)                      │
├────────────────────────────────────────────────────────┤
│  ⚡ 4K 60FPS Video Recording          [ON/OFF]         │
│  🎬 Cinematic Ultra Bitrate (150Mbps) [ON/OFF]         │
│  📸 50MP Bayer RAW Capture            [ON/OFF]         │
│  🔴 Leica Authentic / Vibrant & Mark  [ON/OFF]         │
│  🎥 Dual-Camera Concurrent Stream     [ON/OFF]         │
│  ⏱️ Extended Shutter (60s-1/16000s)   [ON/OFF]         │
│  ❄️ Bypass Thermal Warning & Shutdown  [ON/OFF]         │
└────────────────────────────────────────────────────────┘
```

---

## 🛠️ Architecture

- **Runtime Hooking**: Injects into `com.android.camera` at Zygote startup via LSPosed / Vector Framework.
- **IPC Architecture**: Uses `PrefProvider` (`content://com.jigar.cameratools.prefs`) combined with `XSharedPreferences` fallback to ensure zero permission friction under Android 14 SELinux enforcing rules.
- **Safety**: Fully defensive hooking pattern — each sub-hook is isolated with exception boundaries to prevent system or camera app instability.

---

## 📦 Installation & Setup

1. **Install APK**:
   ```bash
   adb install -r release/com.jigar.cameratools-v1.0.0.apk
   ```
2. **Activate in LSPosed / Vector**:
   - Open LSPosed / Vector Manager.
   - Enable `CameraTools`.
   - Ensure the scope includes **Camera** (`com.android.camera`).
3. **Configure**:
   - Open **CameraTools** from the app drawer.
   - Toggle desired camera enhancements.
   - Tap **Restart Camera** to apply changes instantly.

---

## 🔨 Building from Source

### Prerequisites
- JDK 17+
- Android SDK Build-Tools 34.0.0+

### Standalone Build Script
Run the automated build script (handles AAPT2 resource compilation, DEX transformation, and signature verification):
```powershell
powershell -ExecutionPolicy Bypass -File .\build_apk.ps1
```

Signed release APK is output to: `release/com.jigar.cameratools-v1.0.0.apk`.

---

## 📄 License
This project is open-source under the [MIT License](LICENSE).
