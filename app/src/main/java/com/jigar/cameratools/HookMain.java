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
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook DataItemFeature.o00o0ooo failed: " + t.getMessage());
            }

            // 3. Common.o00Oo0oO() -> Device config method for dual cam support
            try {
                Class<?> commonClass = XposedHelpers.findClass("com.mi.device.Common", lpparam.classLoader);
                XposedBridge.hookAllMethods(commonClass, "o00Oo0oO", trueHook);
                XposedBridge.log("[" + TAG + "] Common.o00Oo0oO() hooked -> true");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook Common.o00Oo0oO failed: " + t.getMessage());
            }

            // 4. Ruby.o00Oo0oO() in case overridden in device class
            try {
                Class<?> rubyClass = XposedHelpers.findClass("com.mi.device.Ruby", lpparam.classLoader);
                XposedBridge.hookAllMethods(rubyClass, "o00Oo0oO", trueHook);
                XposedBridge.log("[" + TAG + "] Ruby.o00Oo0oO() hooked -> true");
            } catch (Throwable ignored) {}

            // 5. CameraCapabilitiesUtil.isDualVideoKeepCapture
            try {
                Class<?> utilClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
                XposedBridge.hookAllMethods(utilClass, "isDualVideoKeepCapture", trueHook);
                XposedBridge.log("[" + TAG + "] CameraCapabilitiesUtil.isDualVideoKeepCapture hooked -> true");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook CameraCapabilitiesUtil.isDualVideoKeepCapture failed: " + t.getMessage());
            }

            // 6. Guarantee Mode 204 (Dual Video) in DataItemGlobal.getSortModes() so it appears in More / Modes
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

            // D. Hook ActivityHandler message 3 (shows CameraExitHintDialogFragment 3-second countdown & exit)
            try {
                Class<?> handlerClass = XposedHelpers.findClass("com.android.camera.ActivityBase$ActivityHandler", lpparam.classLoader);
                XposedBridge.hookAllMethods(handlerClass, "handleMessage", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        if (param.args.length > 0 && param.args[0] instanceof android.os.Message) {
                            android.os.Message msg = (android.os.Message) param.args[0];
                            if (msg.what == 3) {
                                // Suppress thermal exit dialog
                                param.setResult(null);
                            }
                        }
                    }
                });
                XposedBridge.log("[" + TAG + "] ActivityHandler message 3 exit suppression installed");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook ActivityHandler failed: " + t.getMessage());
            }

            // E. Hook CameraExitHintDialogFragment (prevent errorType 3 from showing dialog)
            try {
                Class<?> exitDialogClass = XposedHelpers.findClass("com.android.camera.fragment.dialog.CameraExitHintDialogFragment", lpparam.classLoader);
                XposedBridge.hookAllMethods(exitDialogClass, "setErrorType", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        if (param.args.length > 0 && Integer.valueOf(3).equals(param.args[0])) {
                            param.setResult(null);
                        }
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
