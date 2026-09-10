package com.jigar.cameratools;

import android.app.Application;
import android.content.Context;
import android.util.Range;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {
    private static final String TAG = "JigarCameraTools";

    // Feature toggles (defaulting to true)
    private boolean enable4k60 = true;
    private boolean enableBitrate = true;
    private boolean enableRaw = true;
    private boolean enableLeica = true;
    private boolean enableDualVideo = true;
    private boolean enableShutter = true;
    private boolean enableDisableThermal = true;

    private XSharedPreferences xsp;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.camera".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[" + TAG + "] Hook loaded for com.android.camera on ruby (MT6877)");

        loadPreferences();

        // PrefProvider IPC sync hook
        hookContextReload(lpparam);

        // 1. 4K 60FPS Video Recording Hooks
        hook4K60FPS(lpparam);

        // 2. Cinematic Ultra Bitrate (150 Mbps)
        hookCinematicBitrate(lpparam);

        // 3. 50MP Full Bayer RAW Capture
        hookRaw50MP(lpparam);

        // 4. Leica Authentic & Vibrant Profiles + Leica Watermark
        hookLeicaProfiles(lpparam);

        // 5. Dual-Camera Concurrent Stream (Director Mode)
        hookDualVideo(lpparam);

        // 6. Extended Pro Exposure Range (60s – 1/16000s)
        hookShutterSpeed(lpparam);

        // 7. Complete Thermal Warning & Forced Shutdown Bypass
        hookThermalBypass(lpparam);
    }

    private void loadPreferences() {
        try {
            xsp = new XSharedPreferences("com.jigar.cameratools", PrefProvider.PREFS_NAME);
            xsp.makeWorldReadable();
            xsp.reload();
            enable4k60 = xsp.getBoolean(PrefProvider.KEY_4K60, true);
            enableBitrate = xsp.getBoolean(PrefProvider.KEY_BITRATE, true);
            enableRaw = xsp.getBoolean(PrefProvider.KEY_RAW, true);
            enableLeica = xsp.getBoolean(PrefProvider.KEY_LEICA, true);
            enableDualVideo = xsp.getBoolean(PrefProvider.KEY_DUAL_VIDEO, true);
            enableShutter = xsp.getBoolean(PrefProvider.KEY_SHUTTER, true);
            enableDisableThermal = xsp.getBoolean(PrefProvider.KEY_DISABLE_THERMAL, true);
            XposedBridge.log("[" + TAG + "] Preferences loaded: 4k60=" + enable4k60 + ", bitrate=" + enableBitrate +
                    ", raw=" + enableRaw + ", leica=" + enableLeica + ", dual=" + enableDualVideo + ", shutter=" + enableShutter +
                    ", thermalBypass=" + enableDisableThermal);
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Error loading preferences, using defaults: " + t.getMessage());
        }
    }

    private void hookContextReload(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> appClass = XposedHelpers.findClass("com.android.camera.CameraAppImpl", lpparam.classLoader);
            XposedBridge.hookAllMethods(appClass, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Context ctx = (Context) param.thisObject;
                        android.net.Uri uri = android.net.Uri.parse("content://" + PrefProvider.AUTHORITY);
                        android.os.Bundle b = ctx.getContentResolver().call(uri, "get_all", null, null);
                        if (b != null) {
                            enable4k60 = b.getBoolean(PrefProvider.KEY_4K60, true);
                            enableBitrate = b.getBoolean(PrefProvider.KEY_BITRATE, true);
                            enableRaw = b.getBoolean(PrefProvider.KEY_RAW, true);
                            enableLeica = b.getBoolean(PrefProvider.KEY_LEICA, true);
                            enableDualVideo = b.getBoolean(PrefProvider.KEY_DUAL_VIDEO, true);
                            enableShutter = b.getBoolean(PrefProvider.KEY_SHUTTER, true);
                            enableDisableThermal = b.getBoolean(PrefProvider.KEY_DISABLE_THERMAL, true);
                            XposedBridge.log("[" + TAG + "] Preferences updated via PrefProvider IPC: 4k60=" + enable4k60 +
                                    ", bitrate=" + enableBitrate + ", raw=" + enableRaw + ", leica=" + enableLeica +
                                    ", dual=" + enableDualVideo + ", shutter=" + enableShutter + ", thermalBypass=" + enableDisableThermal);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("[" + TAG + "] PrefProvider IPC query note: " + t.getMessage());
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    // ==========================================
    // 1. 4K 60FPS
    // ==========================================
    private void hook4K60FPS(final XC_LoadPackage.LoadPackageParam lpparam) {
        final String targetClass = "com.android.camera.data.data.config.ComponentConfigVideoQuality";
        try {
            Class<?> clazz = XposedHelpers.findClass(targetClass, lpparam.classLoader);

            XposedBridge.hookAllMethods(clazz, "isSupport60FPS", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enable4k60) return;
                    if (param.args.length >= 2 && param.args[0] instanceof Integer && param.args[1] instanceof Integer) {
                        int w = (Integer) param.args[0];
                        int h = (Integer) param.args[1];
                        if (w == 3840 && h == 2160) {
                            param.setResult(true);
                        }
                    }
                }
            });

            XposedBridge.hookAllMethods(clazz, "filterInByInitLimitationAndSpecifiedRange", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enable4k60) return;
                    if (param.args.length > 0 && param.args[0] instanceof Integer) {
                        int quality = (Integer) param.args[0];
                        if (quality == 2108) {
                            param.setResult(true);
                        }
                    }
                }
            });

            XposedBridge.hookAllMethods(clazz, "init4K", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enable4k60) return;
                    if (param.args.length >= 1 && param.args[0] instanceof List) {
                        List list = (List) param.args[0];
                        int mode = param.args.length > 1 && param.args[1] instanceof Integer ? (Integer) param.args[1] : 162;
                        boolean has4k60 = false;
                        for (Object item : list) {
                            try {
                                Object val = XposedHelpers.getObjectField(item, "mValue");
                                if ("8,60".equals(val) || (val != null && val.toString().contains("2108"))) {
                                    has4k60 = true;
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (!has4k60) {
                            try {
                                Method createMethod = null;
                                for (Method m : param.thisObject.getClass().getDeclaredMethods()) {
                                    if (m.getName().equals("createNormalQuality") && m.getParameterTypes().length >= 4) {
                                        createMethod = m;
                                        break;
                                    }
                                }
                                if (createMethod != null) {
                                    createMethod.setAccessible(true);
                                    Object newItem = createMethod.invoke(param.thisObject, 2108, mode, 162, 180);
                                    if (newItem != null) {
                                        list.add(newItem);
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[" + TAG + "] init4K injection error: " + t.getMessage());
                            }
                        }
                    }
                }
            });

            XposedBridge.hookAllMethods(clazz, "getSupportedVideoQualityList", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enable4k60) return;
                    Object res = param.getResult();
                    if (res instanceof List) {
                        List list = (List) res;
                        boolean has4k60 = false;
                        for (Object item : list) {
                            try {
                                Object val = XposedHelpers.getObjectField(item, "mValue");
                                if ("8,60".equals(val) || (val != null && val.toString().contains("2108"))) {
                                    has4k60 = true;
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (!has4k60) {
                            try {
                                Method createMethod = null;
                                for (Method m : param.thisObject.getClass().getDeclaredMethods()) {
                                    if (m.getName().equals("createNormalQuality") && m.getParameterTypes().length >= 4) {
                                        createMethod = m;
                                        break;
                                    }
                                }
                                if (createMethod != null) {
                                    createMethod.setAccessible(true);
                                    Object newItem = createMethod.invoke(param.thisObject, 2108, 162, 162, 180);
                                    if (newItem != null) list.add(newItem);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook 4K quality failed: " + t.getMessage());
        }

        // SubFPS toggle button
        try {
            Class<?> subFpsClass = XposedHelpers.findClass("com.android.camera.data.data.config.ComponentConfigVideoSubFPS", lpparam.classLoader);
            XposedBridge.hookAllMethods(subFpsClass, "generateFPSItems", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enable4k60) return;
                    if (param.args.length >= 1 && param.args[0] instanceof List) {
                        List list = (List) param.args[0];
                        boolean has60 = false;
                        for (Object item : list) {
                            try {
                                Object val = XposedHelpers.getObjectField(item, "mValue");
                                if ("60".equals(val)) {
                                    has60 = true;
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (!has60) {
                            try {
                                Method createItemMethod = null;
                                for (Method m : param.thisObject.getClass().getDeclaredMethods()) {
                                    if (m.getName().equals("createFPSItem") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == int.class) {
                                        createItemMethod = m;
                                        break;
                                    }
                                }
                                if (createItemMethod != null) {
                                    createItemMethod.setAccessible(true);
                                    Object item60 = createItemMethod.invoke(param.thisObject, 60);
                                    if (item60 != null) list.add(item60);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[" + TAG + "] SubFPS 60 insertion error: " + t.getMessage());
                            }
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook SubFPS failed: " + t.getMessage());
        }

        try {
            Class<?> dataItemConfig = XposedHelpers.findClass("com.android.camera.data.data.config.DataItemConfig", lpparam.classLoader);
            XposedBridge.hookAllMethods(dataItemConfig, "supportVideoSubFPS", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enable4k60) return;
                    param.setResult(true);
                }
            });
        } catch (Throwable ignored) {}
    }

    // ==========================================
    // 2. Cinematic Ultra Bitrate (150 Mbps)
    // ==========================================
    private void hookCinematicBitrate(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XC_MethodHook bitrateHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableBitrate) return;
                    Map<String, Integer> map = new HashMap<>();
                    map.put("3840x2160:60", 150000000); // 150 Mbps (4K 60)
                    map.put("3840x2160:30", 100000000); // 100 Mbps (4K 30)
                    map.put("1920x1080:60", 60000000);  // 60 Mbps (1080p 60)
                    map.put("1920x1080:30", 40000000);  // 40 Mbps (1080p 30)
                    map.put("1280x720:30", 25000000);
                    param.setResult(map);
                }
            };

            try {
                Class<?> commonClass = XposedHelpers.findClass("com.mi.device.Common", lpparam.classLoader);
                XposedBridge.hookAllMethods(commonClass, "o0ooOO0", bitrateHook);
            } catch (Throwable ignored) {}

            try {
                Class<?> rubyClass = XposedHelpers.findClass("com.mi.device.Ruby", lpparam.classLoader);
                XposedBridge.hookAllMethods(rubyClass, "o0ooOO0", bitrateHook);
            } catch (Throwable ignored) {}

            XposedBridge.log("[" + TAG + "] Cinematic Bitrate (150 Mbps) hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook Bitrate failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 3. 50MP Full Bayer RAW Capture
    // ==========================================
    private void hookRaw50MP(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> utilClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
            XposedBridge.hookAllMethods(utilClass, "isSupportRaw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableRaw) return;
                    param.setResult(true);
                }
            });

            XposedBridge.hookAllMethods(utilClass, "isSupportUltraPixelRaw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableRaw) return;
                    param.setResult(true);
                }
            });

            Class<?> capsClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            XposedBridge.hookAllMethods(capsClass, "isSupportRaw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableRaw) return;
                    param.setResult(true);
                }
            });

            XposedBridge.hookAllMethods(capsClass, "isSupportUltraPixelRaw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableRaw) return;
                    param.setResult(true);
                }
            });

            try {
                Class<?> rubyClass = XposedHelpers.findClass("com.mi.device.Ruby", lpparam.classLoader);
                XposedBridge.hookAllMethods(rubyClass, "OooOo00", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableRaw) return;
                        param.setResult(true);
                    }
                });
            } catch (Throwable ignored) {}

            XposedBridge.log("[" + TAG + "] 50MP Ultra RAW hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook RAW failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 4. Leica Authentic & Vibrant Profiles + Leica Watermark
    // ==========================================
    private void hookLeicaProfiles(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> utilClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
            XC_MethodHook trueHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableLeica) return;
                    param.setResult(true);
                }
            };

            XposedBridge.hookAllMethods(utilClass, "isSupportCvType", trueHook);
            XposedBridge.hookAllMethods(utilClass, "isSupportCvWatermark", trueHook);
            XposedBridge.hookAllMethods(utilClass, "isSupportCvLens", trueHook);
            XposedBridge.hookAllMethods(utilClass, "isSupportCvLensModeSession", trueHook);

            Class<?> capsClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            XposedBridge.hookAllMethods(capsClass, "isSupportCvType", trueHook);
            XposedBridge.hookAllMethods(capsClass, "isSupportCvWatermark", trueHook);

            XposedBridge.log("[" + TAG + "] Leica Profiles & Watermark hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook Leica failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 5. Dual-Camera Concurrent Stream (Director Mode / Dual Video)
    // ==========================================
    private void hookDualVideo(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XC_MethodHook trueHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableDualVideo) return;
                    param.setResult(true);
                }
            };

            // 1. DualCamModuleEntry.support() -> MUST return true for FeatureLoader to register mode 204
            try {
                Class<?> dualEntryClass = XposedHelpers.findClass("com.android.camera.features.mode.dualcam.DualCamModuleEntry", lpparam.classLoader);
                XposedBridge.hookAllMethods(dualEntryClass, "support", trueHook);
                XposedBridge.log("[" + TAG + "] DualCamModuleEntry.support() hooked -> true");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook DualCamModuleEntry.support failed: " + t.getMessage());
            }

            // 2. DataItemFeature.o00o0ooo() -> The underlying DEX method DualCamModuleEntry calls
            try {
                Class<?> dataItemFeature = XposedHelpers.findClass("o000Oo0.OooO00o", lpparam.classLoader);
                XposedBridge.hookAllMethods(dataItemFeature, "o00o0ooo", trueHook);
                XposedBridge.log("[" + TAG + "] DataItemFeature.o00o0ooo() hooked -> true");

                // 2b. o000ooo() -> Camera opening order: MUST return true so MAIN_SOURCE (Back Camera 0)
                // opens FIRST as master before SUB_SOURCE (Front Camera 1)
                XposedBridge.hookAllMethods(dataItemFeature, "o000ooo", trueHook);
                XposedBridge.log("[" + TAG + "] DataItemFeature.o000ooo() hooked -> true (Back Camera 0 opens first)");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook DataItemFeature failed: " + t.getMessage());
            }

            // 3. Common.o00Oo0oO() and Common.o000ooo() -> Device config methods
            try {
                Class<?> commonClass = XposedHelpers.findClass("com.mi.device.Common", lpparam.classLoader);
                XposedBridge.hookAllMethods(commonClass, "o00Oo0oO", trueHook);
                XposedBridge.hookAllMethods(commonClass, "o000ooo", trueHook);
                XposedBridge.log("[" + TAG + "] Common.o00Oo0oO() and o000ooo() hooked -> true");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook Common dual methods failed: " + t.getMessage());
            }

            // 4. Ruby.o00Oo0oO() and Ruby.o000ooo()
            try {
                Class<?> rubyClass = XposedHelpers.findClass("com.mi.device.Ruby", lpparam.classLoader);
                XposedBridge.hookAllMethods(rubyClass, "o00Oo0oO", trueHook);
                XposedBridge.hookAllMethods(rubyClass, "o000ooo", trueHook);
                XposedBridge.log("[" + TAG + "] Ruby.o00Oo0oO() and o000ooo() hooked -> true");
            } catch (Throwable ignored) {}

            // 5. MediaTek MTK PIP Tag Enablement: o000Oo0.OooO0O0.OooOOoo()
            // Enables setMtkPipDevices on MediaTek HAL so the hardware pipeline allows concurrent front+back streams
            try {
                Class<?> dataItemFeature2 = XposedHelpers.findClass("o000Oo0.OooO0O0", lpparam.classLoader);
                XposedBridge.hookAllMethods(dataItemFeature2, "OooOOoo", trueHook);
                XposedBridge.log("[" + TAG + "] DataItemFeature2.OooOOoo() hooked -> true (MediaTek PIP feature tag enabled)");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook DataItemFeature2.OooOOoo failed: " + t.getMessage());
            }

            // 6. CameraCapabilitiesUtil capabilities
            try {
                Class<?> utilClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
                XposedBridge.hookAllMethods(utilClass, "isDualVideoKeepCapture", trueHook);
                XposedBridge.hookAllMethods(utilClass, "isSatPipSupported", trueHook);
                XposedBridge.log("[" + TAG + "] CameraCapabilitiesUtil DualVideo capabilities hooked");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook CameraCapabilitiesUtil failed: " + t.getMessage());
            }

            try {
                Class<?> capsClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilities", lpparam.classLoader);
                XposedBridge.hookAllMethods(capsClass, "isDualVideoKeepCapture", trueHook);
                XposedBridge.hookAllMethods(capsClass, "isSatPipSupported", trueHook);
                XposedBridge.log("[" + TAG + "] CameraCapabilities DualVideo capabilities hooked");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook CameraCapabilities failed: " + t.getMessage());
            }

            // 7. Fix Session Operating Mode on MediaTek:
            // DualCamModuleDevice.getOperatingMode() and DualVideoModuleBase.getOperatingMode()
            // return proprietary Qualcomm modes 32772 (0x8004) / 32777 (0x8009) which crash MTK Camera HAL.
            // On MediaTek Dimensity 1080 (ruby), returning 0 (SESSION_REGULAR) creates standard Camera2 dual sessions.
            XC_MethodHook normalOperatingModeHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableDualVideo) return;
                    param.setResult(0); // Standard Android SESSION_REGULAR
                }
            };

            try {
                Class<?> dualCamDevice = XposedHelpers.findClass("com.android.camera.features.mode.dualcam.DualCamModuleDevice", lpparam.classLoader);
                XposedBridge.hookAllMethods(dualCamDevice, "getOperatingMode", normalOperatingModeHook);
                XposedBridge.log("[" + TAG + "] DualCamModuleDevice.getOperatingMode() hooked -> 0 (SESSION_REGULAR)");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook DualCamModuleDevice.getOperatingMode failed: " + t.getMessage());
            }

            try {
                Class<?> dualVideoBase = XposedHelpers.findClass("com.android.camera.dualvideo.DualVideoModuleBase", lpparam.classLoader);
                XposedBridge.hookAllMethods(dualVideoBase, "getOperatingMode", normalOperatingModeHook);
                XposedBridge.log("[" + TAG + "] DualVideoModuleBase.getOperatingMode() hooked -> 0 (SESSION_REGULAR)");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook DualVideoModuleBase.getOperatingMode failed: " + t.getMessage());
            }

            // 8. Sanitize CompatibilityUtils.createCaptureSessionWithSessionConfiguration:
            // If sessionType is >= 32768 (Qualcomm vendor modes 0x8004, 0x8009, 0x8010), rewrite to 0 (SESSION_REGULAR)
            try {
                Class<?> compatUtils = XposedHelpers.findClass("com.android.camera.lib.compatibility.util.CompatibilityUtils", lpparam.classLoader);
                XposedBridge.hookAllMethods(compatUtils, "createCaptureSessionWithSessionConfiguration", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDualVideo) return;
                        if (param.args.length > 1 && param.args[1] instanceof Integer) {
                            int sessionType = (Integer) param.args[1];
                            if (sessionType >= 32768) {
                                XposedBridge.log("[" + TAG + "] Sanitized vendor sessionType 0x" + Integer.toHexString(sessionType) + " -> 0 (SESSION_REGULAR)");
                                param.args[1] = 0;
                            }
                        }
                    }
                });
                XposedBridge.log("[" + TAG + "] CompatibilityUtils.createCaptureSessionWithSessionConfiguration sessionType sanitizer hooked");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook CompatibilityUtils failed: " + t.getMessage());
            }

            // 9. Protect Camera 0 and Camera 1 from mutual closure during Dual Video switching
            try {
                Class<?> closeCallableClass = XposedHelpers.findClass("com.xiaomi.camera.device.callable.CloseCameraCallable", lpparam.classLoader);
                XposedBridge.hookAllConstructors(closeCallableClass, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDualVideo) return;
                        if (param.args.length >= 3 && param.args[2] instanceof String[]) {
                            String[] cur = (String[]) param.args[2];
                            java.util.HashSet<String> set = new java.util.HashSet<>(java.util.Arrays.asList(cur));
                            set.add("0");
                            set.add("1");
                            param.args[2] = set.toArray(new String[0]);
                        }
                    }
                });
                XposedBridge.log("[" + TAG + "] CloseCameraCallable dual camera mutual protection hooked");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook CloseCameraCallable failed: " + t.getMessage());
            }

            // 10. Guarantee Mode 204 (Dual Video) in DataItemGlobal.getSortModes() so it appears in More / Modes
            try {
                Class<?> globalClass = XposedHelpers.findClass("com.android.camera.data.data.global.DataItemGlobal", lpparam.classLoader);
                XposedBridge.hookAllMethods(globalClass, "getSortModes", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDualVideo) return;
                        Object res = param.getResult();
                        if (res instanceof int[]) {
                            int[] modes = (int[]) res;
                            boolean hasDualVideo = false;
                            for (int m : modes) {
                                if (m == 204) {
                                    hasDualVideo = true;
                                    break;
                                }
                            }
                            if (!hasDualVideo) {
                                int[] newModes = new int[modes.length + 1];
                                System.arraycopy(modes, 0, newModes, 0, modes.length);
                                newModes[modes.length] = 204;
                                param.setResult(newModes);
                            }
                        }
                    }
                });
                XposedBridge.log("[" + TAG + "] DataItemGlobal.getSortModes() hooked to ensure mode 204 included");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook DataItemGlobal.getSortModes failed: " + t.getMessage());
            }


            // Hook concurrent stream support methods
            try {
                Class<?> utilClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
                try { XposedBridge.hookAllMethods(utilClass, "isConcurrentStreamSupported", trueHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(utilClass, "isConcurrentModeSupported", trueHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(utilClass, "isSupportDualVideo", trueHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(utilClass, "isSupportPIP", trueHook); } catch (Throwable ignored) {}
                XposedBridge.log("[" + TAG + "] CameraCapabilitiesUtil concurrent/dualvideo/PIP hooks installed");
            } catch (Throwable ignored) {}

            try {
                Class<?> capsClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilities", lpparam.classLoader);
                try { XposedBridge.hookAllMethods(capsClass, "isConcurrentStreamSupported", trueHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(capsClass, "isConcurrentModeSupported", trueHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(capsClass, "isSupportDualVideo", trueHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(capsClass, "isSupportPIP", trueHook); } catch (Throwable ignored) {}
                XposedBridge.log("[" + TAG + "] CameraCapabilities concurrent/dualvideo/PIP hooks installed");
            } catch (Throwable ignored) {}

            // 11. Hook DualVideoModuleUtil for camera selection and layout support
            try {
                Class<?> dualVideoUtil = XposedHelpers.findClass("com.android.camera.dualvideo.DualVideoModuleUtil", lpparam.classLoader);
                // Enable all dual video camera combinations
                try { XposedBridge.hookAllMethods(dualVideoUtil, "isSupportDualVideoCameraChoose", trueHook); } catch (Throwable ignored) {}
                XposedBridge.log("[" + TAG + "] DualVideoModuleUtil camera choose support hooked");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook DualVideoModuleUtil failed: " + t.getMessage());
            }

            // 12. Hook ComponentRunningDualVideo to ensure dual video data component is available
            try {
                Class<?> componentDualVideo = XposedHelpers.findClass("com.android.camera.data.data.runing.ComponentRunningDualVideo", lpparam.classLoader);
                // Hook to ensure the component reports dual video is supported
                XposedBridge.hookAllMethods(componentDualVideo, "isClosed", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDualVideo) return;
                        param.setResult(false);
                    }
                });
                XposedBridge.log("[" + TAG + "] ComponentRunningDualVideo hooked");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook ComponentRunningDualVideo failed: " + t.getMessage());
            }

            // 13. Hook Checker class to bypass hardware capability checks for dual video
            try {
                Class<?> checkerClass = XposedHelpers.findClass("com.android.camera.dualvideo.Checker", lpparam.classLoader);
                XC_MethodHook checkTrueHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDualVideo) return;
                        param.setResult(true);
                    }
                };
                for (java.lang.reflect.Method m : checkerClass.getDeclaredMethods()) {
                    if (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) {
                        try {
                            XposedBridge.hookAllMethods(checkerClass, m.getName(), checkTrueHook);
                        } catch (Throwable ignored) {}
                    }
                }
                XposedBridge.log("[" + TAG + "] DualVideo Checker all boolean checks bypassed");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook Checker failed: " + t.getMessage());
            }

            // 14. Hook PIPInfo to enable PIP capability for MediaTek
            try {
                Class<?> pipInfoClass = XposedHelpers.findClass("com.android.camera.PIPInfo", lpparam.classLoader);
                try { XposedBridge.hookAllMethods(pipInfoClass, "checkOpenAbility", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDualVideo) return;
                        param.setResult(0); // 0 = OK, can open
                    }
                }); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(pipInfoClass, "isSupportPIP", trueHook); } catch (Throwable ignored) {}
                XposedBridge.log("[" + TAG + "] PIPInfo hooks installed");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook PIPInfo failed: " + t.getMessage());
            }

            XposedBridge.log("[" + TAG + "] Dual-Camera Concurrent Stream (Mode 204) hooked successfully");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook Dual Video failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 6. Extended Pro Exposure Range (60s – 1/16000s)
    // ==========================================
    private void hookShutterSpeed(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XC_MethodHook rangeHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableShutter) return;
                    // 62.5 microseconds (1/16000s) to 60,000,000,000 nanoseconds (60s)
                    param.setResult(new Range<Long>(62500L, 60000000000L));
                }
            };

            Class<?> utilClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
            XposedBridge.hookAllMethods(utilClass, "getExposureTimeRange", rangeHook);

            Class<?> capsClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            XposedBridge.hookAllMethods(capsClass, "getExposureTimeRange", rangeHook);

            XposedBridge.log("[" + TAG + "] Extended Pro Exposure Range hooked (60s to 1/16000s)");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook Shutter failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 7. Complete Thermal Warning & Forced Shutdown Bypass
    // ==========================================
    private void hookThermalBypass(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // A. Hook ThermalDetector singleton methods
            try {
                Class<?> detectorClass = XposedHelpers.findClass("com.android.camera.ThermalDetector", lpparam.classLoader);

                XC_MethodHook returnFalseHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(false);
                    }
                };

                XposedBridge.hookAllMethods(detectorClass, "thermalCloseBoth", returnFalseHook);
                XposedBridge.hookAllMethods(detectorClass, "thermalCloseFront", returnFalseHook);
                XposedBridge.hookAllMethods(detectorClass, "thermalCloseFlash", returnFalseHook);
                XposedBridge.hookAllMethods(detectorClass, "thermalCloseNightAlgo", returnFalseHook);
                XposedBridge.hookAllMethods(detectorClass, "thermalConstrained", returnFalseHook);
                XposedBridge.hookAllMethods(detectorClass, "isReachTemperatureLimit", returnFalseHook);

                // Safe nominal cool temperature results
                XposedBridge.hookAllMethods(detectorClass, "getCameraHalThermalLevel", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(0);
                    }
                });

                XposedBridge.hookAllMethods(detectorClass, "getCameraHalThermalResult", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(25000); // 25.0 C nominal
                    }
                });

                // Suppress onThermalNotification dispatch
                XposedBridge.hookAllMethods(detectorClass, "onThermalNotification", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(null);
                    }
                });

                // Prevent broadcast receiver from ever registering powerkeeper temp change broadcasts
                XposedBridge.hookAllMethods(detectorClass, "registerReceiver", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(null);
                    }
                });

                XposedBridge.log("[" + TAG + "] ThermalDetector hooks installed");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook ThermalDetector failed: " + t.getMessage());
            }

            // B. Hook Camera activity dealThermal (forces finish() when temperature constrained)
            try {
                Class<?> cameraClass = XposedHelpers.findClass("com.android.camera.Camera", lpparam.classLoader);
                XposedBridge.hookAllMethods(cameraClass, "dealThermal", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(null); // Never finish activity!
                    }
                });
                XposedBridge.log("[" + TAG + "] Camera.dealThermal hook installed");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook Camera.dealThermal failed: " + t.getMessage());
            }

            // C. Hook Camera$1 listener (onThermalNotification in Camera)
            try {
                Class<?> listenerClass = XposedHelpers.findClass("com.android.camera.Camera$1", lpparam.classLoader);
                XposedBridge.hookAllMethods(listenerClass, "onThermalNotification", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(null);
                    }
                });
            } catch (Throwable ignored) {}

            // D. Hook ActivityHandler message 3 (thermal exit) and message 10 (camera error exit)
            try {
                Class<?> handlerClass = XposedHelpers.findClass("com.android.camera.ActivityBase$ActivityHandler", lpparam.classLoader);
                XposedBridge.hookAllMethods(handlerClass, "handleMessage", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof android.os.Message) {
                            android.os.Message msg = (android.os.Message) param.args[0];
                            if (msg.what == 3 && enableDisableThermal) {
                                // Suppress thermal exit dialog
                                param.setResult(null);
                            } else if (msg.what == 10 && enableDualVideo) {
                                // Suppress "Can't connect to the camera" fatal error dialog
                                XposedBridge.log("[" + TAG + "] ActivityHandler camera error msg 10 intercepted (arg1=" + msg.arg1 + "), suppressing forced shutdown dialog");
                                param.setResult(null);
                            }
                        }
                    }
                });
                XposedBridge.log("[" + TAG + "] ActivityHandler message 3 and message 10 exit suppression installed");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook ActivityHandler failed: " + t.getMessage());
            }

            // E. Hook CameraExitHintDialogFragment (prevent countdown from ever closing the camera app)
            try {
                Class<?> exitDialogClass = XposedHelpers.findClass("com.android.camera.fragment.dialog.CameraExitHintDialogFragment", lpparam.classLoader);
                XposedBridge.hookAllMethods(exitDialogClass, "onTimerFinish", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("[" + TAG + "] CameraExitHintDialogFragment.onTimerFinish() suppressed - camera app will stay open");
                        param.setResult(null);
                    }
                });
            } catch (Throwable ignored) {}

            // F. Hook ConfigChangeImpl.onThermalNotification
            try {
                Class<?> configClass = XposedHelpers.findClass("com.android.camera.module.impl.component.ConfigChangeImpl", lpparam.classLoader);
                XposedBridge.hookAllMethods(configClass, "onThermalNotification", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(false);
                    }
                });
            } catch (Throwable ignored) {}

            // G. Hook ThermalOverheatMultipleASD (video recording overheat toast/hint)
            try {
                Class<?> asdClass = XposedHelpers.findClass("com.android.camera.module.interceptor.camera.ThermalOverheatMultipleASD", lpparam.classLoader);
                XC_MethodHook falseHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(false);
                    }
                };
                XposedBridge.hookAllMethods(asdClass, "showThermalOverheatTipNeeded", falseHook);
                XposedBridge.hookAllMethods(asdClass, "getInTimeCondition", falseHook);
                XposedBridge.hookAllMethods(asdClass, "consumeResultOnMainThreadIfDataChanged", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(null);
                    }
                });
                XposedBridge.log("[" + TAG + "] ThermalOverheatMultipleASD hooks installed");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook ThermalOverheatMultipleASD failed: " + t.getMessage());
            }

            // H. Hook FragmentTopAlert.alertVideoOverheatHint
            try {
                Class<?> topAlertClass = XposedHelpers.findClass("com.android.camera.fragment.top.FragmentTopAlert", lpparam.classLoader);
                XposedBridge.hookAllMethods(topAlertClass, "alertVideoOverheatHint", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(null);
                    }
                });
            } catch (Throwable ignored) {}

            // I. Hook ThermalHelper (powerkeeper reporting and refresh rate throttling)
            try {
                Class<?> helperClass = XposedHelpers.findClass("com.android.camera.ThermalHelper", lpparam.classLoader);
                XC_MethodHook noopHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(null);
                    }
                };
                XposedBridge.hookAllMethods(helperClass, "updateDisplayFrameRate", noopHook);
                XposedBridge.hookAllMethods(helperClass, "notifyThermalRecordStart", noopHook);
                XposedBridge.hookAllMethods(helperClass, "notifyThermalRecordStop", noopHook);
                XposedBridge.log("[" + TAG + "] ThermalHelper reporting bypass installed");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook ThermalHelper failed: " + t.getMessage());
            }

            XposedBridge.log("[" + TAG + "] Full Thermal Warning & Shutdown Bypass successfully hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook Thermal Bypass overall error: " + t.getMessage());
        }
    }
}
