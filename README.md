<h1 align="center">
  <picture>
    <img src="assets/logo.svg" alt="CameraTools">
  </picture>
</h1>

<p align="center">
  A powerful but simple Xposed module that unlocks hidden camera features on the Xiaomi Redmi Note 12 Pro 5G (ruby).
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-13%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 13+">
  <img src="https://img.shields.io/badge/Xposed-API_93%2B-ff69b4?style=for-the-badge" alt="Xposed API 93+">
  <img src="https://img.shields.io/github/downloads/official-Arvind/CameraTools/total?style=for-the-badge&logo=github&label=Downloads&cacheSeconds=600" alt="Downloads">
</p>


## About this module

Xiaomi software restricts many camera capabilities on mid-range devices. This module bypasses these artificial restrictions by directly hooking into the Xiaomi Camera application at runtime. 

Enabling the module allows you to cleanly toggle hidden features without modifying your system files. You can turn on each feature from the CameraTools app, and a toast will confirm if the hook was successful when the Camera app opens.

> [!NOTE]
> Designed specifically for the **Redmi Note 12 Pro 5G (ruby)** running Xiaomi HyperOS or MIUI. While some features may work on other MediaTek Dimensity devices, this module targets the MT6877 camera capabilities.

*(For developers wanting to see the technical reverse-engineering notes, please check our ruby reverse engineering repository.)*

## Features

- **4K 60FPS Video Recording**: Unlocks smooth 4K video recording at 60 frames per second.
- **Ultra High Video Quality**: Improves video recording quality (150 Mbps bitrate) for clearer videos without compression artifacts.
- **50MP RAW Photos**: Enables uncompressed 50-megapixel RAW photos in Pro Mode.
- **50MP Normal Photo**: Forces the standard Photo mode to take full 50-megapixel pictures automatically instead of pixel-binning to 12MP.
- **Leica Camera Modes**: Adds Leica Authentic and Leica Vibrant camera modes and the official Leica watermark.
- **Dual-Camera Video**: Record video using the front and rear cameras at the same time.
- **Pro Mode Long Exposure**: Allows taking very long exposure photos (up to 60 seconds) in Pro Mode for astrophotography.
- **Disable Overheating Limits**: Stops the camera from closing automatically when the phone gets warm during long video recording sessions.

## Requirements

- Rooted device running Android 13+ (MIUI / HyperOS)
- LSPosed (or compatible framework like Vector) installed and active

## Install

1. Install APK from [Releases](../../releases)
2. Enable the module in your Xposed manager and ensure the **Camera** (com.android.camera) app is checked in the scope.
3. Reboot your device (or force-stop the camera if your framework supports hot reload).
4. Open the CameraTools app and toggle your desired features.
5. Tap **Restart Camera** inside the app to apply changes immediately.

## Reporting issues

If a feature fails to hook or crashes your camera, please create a [GitHub issue](https://github.com/official-Arvind/CameraTools/issues/new) and attach your LSPosed/Vector logs as well as a crash log (db logcat -b crash).

## License

[![GPL-3.0-only](https://img.shields.io/badge/LICENSE-GPL--3.0--only-%23A42E2B?style=for-the-badge&logo=gnu&logoColor=white&logoPosition=right)](LICENSE)