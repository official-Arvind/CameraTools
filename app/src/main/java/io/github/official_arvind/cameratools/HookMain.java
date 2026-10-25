package io.github.official_arvind.cameratools;

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
    private static final String TAG = "CameraTools";

    // Feature toggles (defaulting to false as requested)
    private volatile boolean enable4k60 = false;
    private volatile boolean enableBitrate = false;
    private volatile boolean enableRaw = false;
    private volatile boolean enableLeica = false;
    private volatile boolean enableDualVideo = false;
    private volatile boolean enableShutter = false;
    private volatile boolean enableDisableThermal = false;
    private volatile boolean enableHighResPhoto = false; // opt-in — default OFF (requires camera restart to take effect)
    private volatile boolean enableLogFormat = false;
    private volatile boolean enableMiMovie = false;
    private volatile boolean enablePrompter = false;
    private volatile boolean enableFocusPeak = false;
    private volatile boolean enableTrackFocus = false;
    private volatile boolean enableDocMode = false;
    private volatile boolean enableAudioMic = false;

    private XSharedPreferences xsp;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        java.util.Set<String> SUPPORTED_PACKAGES = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.android.camera",
            "com.miui.camera",
            "com.google.android.GoogleCamera",
            "com.google.android.GoogleCameraEng",
            "com.android.MGC",
            "com.MGC",
            "com.agc.cam",
            "com.shamim.cam",
            "org.codeaurora.snapcam",
            "com.android.camera2",
            "org.lineageos.aperture",
            "app.grapheneos.camera",
            "net.sourceforge.opencamera"
        ));
        if (!SUPPORTED_PACKAGES.contains(lpparam.packageName)) {
            return;
        }

        boolean isXiaomiCamera = "com.android.camera".equals(lpparam.packageName) || "com.miui.camera".equals(lpparam.packageName);
        boolean isGCam = lpparam.packageName.contains("GoogleCamera") || lpparam.packageName.contains("MGC") || lpparam.packageName.contains("agc") || lpparam.packageName.contains("shamim");

        XposedBridge.log("[" + TAG + "] Hook loaded for " + lpparam.packageName + " (isXiaomi=" + isXiaomiCamera + ", isGCam=" + isGCam + ", romIsMiui=" + RomDetector.isMiuiOrHyperOs() + ")");

        loadPreferences();


        // PrefProvider IPC sync hook
        hookContextReload(lpparam);

        // AOSP / Custom ROM Missing Resources & FragmentTopConfig Crash Guard
        hookAospResourceFixes(lpparam);

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

        // 8. 50MP Normal Photo — MediaTek ISP remosaic in standard photo mode
        hookHighResPhoto(lpparam);

        // 9. Pro Video LOG Profile
        hookVideoLog(lpparam);

        // 10. Cinematic MiMovie Widescreen (2.39:1)
        hookMiMovie(lpparam);

        // 11. Video Teleprompter
        hookVideoPrompter(lpparam);

        // 12. Focus Peaking & Zebra Exposure Assist
        hookFocusPeakingZebra(lpparam);

        // 13. Motion Tracking Focus (AI Eye & Subject Auto-Tracking)
        hookTrackFocus(lpparam);

        // 14. Document Scanner Mode (Mode 186)
        hookDocumentMode(lpparam);

        // 15. Directional Audio Zoom & Bluetooth Headset Mic
        hookAudioMic(lpparam);

        // 16. Framework-level hooks (work across ALL camera apps on AOSP)
        hookFrameworkLevel(lpparam);
        if (isGCam) hookGCamSpecific(lpparam);
    }

    private void loadPreferences() {
        boolean loaded = false;

        // Tier 1: Modern LSPosed Remote Preferences (API 93+)
        try {
            if (xsp == null) {
                xsp = new XSharedPreferences("io.github.official_arvind.cameratools", PrefProvider.PREFS_NAME);
            }
            xsp.reload();
            Map<String, ?> all = xsp.getAll();
            if (all != null && !all.isEmpty()) {
                enable4k60 = xsp.getBoolean(PrefProvider.KEY_4K60, false);
                enableBitrate = xsp.getBoolean(PrefProvider.KEY_BITRATE, false);
                enableRaw = xsp.getBoolean(PrefProvider.KEY_RAW, false);
                enableLeica = xsp.getBoolean(PrefProvider.KEY_LEICA, false);
                enableDualVideo = xsp.getBoolean(PrefProvider.KEY_DUAL_VIDEO, false);
                enableShutter = xsp.getBoolean(PrefProvider.KEY_SHUTTER, false);
                enableDisableThermal = xsp.getBoolean(PrefProvider.KEY_DISABLE_THERMAL, false);
                enableHighResPhoto = xsp.getBoolean(PrefProvider.KEY_HIGH_RES_PHOTO, false);
                enableLogFormat = xsp.getBoolean(PrefProvider.KEY_LOG_FORMAT, false);
                enableMiMovie = xsp.getBoolean(PrefProvider.KEY_MIMOVIE, false);
                enablePrompter = xsp.getBoolean(PrefProvider.KEY_PROMPTER, false);
                enableFocusPeak = xsp.getBoolean(PrefProvider.KEY_FOCUS_PEAK, false);
                enableTrackFocus = xsp.getBoolean(PrefProvider.KEY_TRACK_FOCUS, xsp.getBoolean(PrefProvider.KEY_LEICA_LENSES, false));
                enableDocMode = xsp.getBoolean(PrefProvider.KEY_DOC_MODE, false);
                enableAudioMic = xsp.getBoolean(PrefProvider.KEY_AUDIO_MIC, false);
                loaded = true;
                XposedBridge.log("[" + TAG + "] Preferences successfully loaded via LSPosed RemotePreferences (API 93)");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] LSPosed RemotePreferences notice: " + t.getMessage());
        }

        // Tier 2: Direct XML File Fallback (Root synced or world-readable storage)
        if (!loaded) {
            loaded = reloadPrefsDirect();
        }

        XposedBridge.log("[" + TAG + "] Final Preferences applied: 4k60=" + enable4k60 + ", bitrate=" + enableBitrate +
                ", raw=" + enableRaw + ", leica=" + enableLeica + ", dual=" + enableDualVideo + ", shutter=" + enableShutter +
                ", thermalBypass=" + enableDisableThermal + ", 50MP=" + enableHighResPhoto +
                ", log=" + enableLogFormat + ", mimovie=" + enableMiMovie + ", prompter=" + enablePrompter +
                ", focusPeak=" + enableFocusPeak + ", trackFocus=" + enableTrackFocus + ", docMode=" + enableDocMode +
                ", audioMic=" + enableAudioMic);
    }

    private boolean reloadPrefsDirect() {
        java.io.File[] candidateFiles = new java.io.File[] {
            new java.io.File("/data/data/com.android.camera/shared_prefs/cameratools_prefs.xml"),
            new java.io.File("/data/user/0/com.android.camera/shared_prefs/cameratools_prefs.xml"),
            new java.io.File("/data/data/com.miui.camera/shared_prefs/cameratools_prefs.xml"),
            new java.io.File("/data/user/0/com.miui.camera/shared_prefs/cameratools_prefs.xml"),
            new java.io.File("/data/user_de/0/io.github.official_arvind.cameratools/shared_prefs/io.github.official_arvind.cameratools_preferences.xml"),
            new java.io.File("/data/data/io.github.official_arvind.cameratools/shared_prefs/io.github.official_arvind.cameratools_preferences.xml"),
            new java.io.File("/data/user_de/0/io.github.official_arvind.cameratools/shared_prefs/camera_tools_prefs.xml"),
            new java.io.File("/data/data/io.github.official_arvind.cameratools/shared_prefs/camera_tools_prefs.xml")
        };
        for (java.io.File f : candidateFiles) {
            XposedBridge.log("[" + TAG + "] Checking prefs file: " + f.getPath() + " exists=" + f.exists() + " canRead=" + f.canRead());
            if (f.exists() && f.canRead()) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                    org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
                    parser.setInput(fis, "utf-8");
                    int eventType = parser.getEventType();
                    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && "boolean".equals(parser.getName())) {
                            String name = parser.getAttributeValue(null, "name");
                            boolean val = Boolean.parseBoolean(parser.getAttributeValue(null, "value"));
                            if (PrefProvider.KEY_4K60.equals(name)) enable4k60 = val;
                            else if (PrefProvider.KEY_BITRATE.equals(name)) enableBitrate = val;
                            else if (PrefProvider.KEY_RAW.equals(name)) enableRaw = val;
                            else if (PrefProvider.KEY_LEICA.equals(name)) enableLeica = val;
                            else if (PrefProvider.KEY_DUAL_VIDEO.equals(name)) enableDualVideo = val;
                            else if (PrefProvider.KEY_SHUTTER.equals(name)) enableShutter = val;
                            else if (PrefProvider.KEY_DISABLE_THERMAL.equals(name)) enableDisableThermal = val;
                            else if (PrefProvider.KEY_HIGH_RES_PHOTO.equals(name)) enableHighResPhoto = val;
                            else if (PrefProvider.KEY_LOG_FORMAT.equals(name)) enableLogFormat = val;
                            else if (PrefProvider.KEY_MIMOVIE.equals(name)) enableMiMovie = val;
                            else if (PrefProvider.KEY_PROMPTER.equals(name)) enablePrompter = val;
                            else if (PrefProvider.KEY_FOCUS_PEAK.equals(name)) enableFocusPeak = val;
                            else if (PrefProvider.KEY_TRACK_FOCUS.equals(name) || PrefProvider.KEY_LEICA_LENSES.equals(name)) enableTrackFocus = val;
                            else if (PrefProvider.KEY_DOC_MODE.equals(name)) enableDocMode = val;
                            else if (PrefProvider.KEY_AUDIO_MIC.equals(name)) enableAudioMic = val;
                        }
                        eventType = parser.next();
                    }
                    XposedBridge.log("[" + TAG + "] Direct XML prefs successfully parsed from " + f.getPath() + ": 4k60=" + enable4k60 + ", 50MP=" + enableHighResPhoto);
                    return true;
                } catch (Throwable t) {
                    XposedBridge.log("[" + TAG + "] Error parsing " + f.getPath() + ": " + t.getMessage());
                }
            }
        }
        return false;
    }

    private void reloadAllPreferences(Context ctx) {
        // Try LSPosed reload
        try {
            if (xsp != null) {
                xsp.reload();
                Map<String, ?> all = xsp.getAll();
                if (all != null && !all.isEmpty()) {
                    enable4k60 = xsp.getBoolean(PrefProvider.KEY_4K60, enable4k60);
                    enableBitrate = xsp.getBoolean(PrefProvider.KEY_BITRATE, enableBitrate);
                    enableRaw = xsp.getBoolean(PrefProvider.KEY_RAW, enableRaw);
                    enableLeica = xsp.getBoolean(PrefProvider.KEY_LEICA, enableLeica);
                    enableDualVideo = xsp.getBoolean(PrefProvider.KEY_DUAL_VIDEO, enableDualVideo);
                    enableShutter = xsp.getBoolean(PrefProvider.KEY_SHUTTER, enableShutter);
                    enableDisableThermal = xsp.getBoolean(PrefProvider.KEY_DISABLE_THERMAL, enableDisableThermal);
                    enableHighResPhoto = xsp.getBoolean(PrefProvider.KEY_HIGH_RES_PHOTO, enableHighResPhoto);
                    enableLogFormat = xsp.getBoolean(PrefProvider.KEY_LOG_FORMAT, enableLogFormat);
                    enableMiMovie = xsp.getBoolean(PrefProvider.KEY_MIMOVIE, enableMiMovie);
                    enablePrompter = xsp.getBoolean(PrefProvider.KEY_PROMPTER, enablePrompter);
                    enableFocusPeak = xsp.getBoolean(PrefProvider.KEY_FOCUS_PEAK, enableFocusPeak);
                    enableTrackFocus = xsp.getBoolean(PrefProvider.KEY_TRACK_FOCUS, xsp.getBoolean(PrefProvider.KEY_LEICA_LENSES, enableTrackFocus));
                    enableDocMode = xsp.getBoolean(PrefProvider.KEY_DOC_MODE, enableDocMode);
                    enableAudioMic = xsp.getBoolean(PrefProvider.KEY_AUDIO_MIC, enableAudioMic);
                }
            }
        } catch (Throwable ignored) {}

        // Try direct XML reload
        reloadPrefsDirect();

        // Try ContentProvider reload if Context is available
        if (ctx != null) {
            try {
                android.content.ContentResolver cr = ctx.getContentResolver();
                if (cr != null) {
                    android.net.Uri uri = android.net.Uri.parse("content://io.github.official_arvind.cameratools.prefs");
                    android.os.Bundle b = cr.call(uri, "get_all", null, null);
                    if (b != null) {
                        enable4k60 = b.getBoolean(PrefProvider.KEY_4K60, enable4k60);
                        enableBitrate = b.getBoolean(PrefProvider.KEY_BITRATE, enableBitrate);
                        enableRaw = b.getBoolean(PrefProvider.KEY_RAW, enableRaw);
                        enableLeica = b.getBoolean(PrefProvider.KEY_LEICA, enableLeica);
                        enableDualVideo = b.getBoolean(PrefProvider.KEY_DUAL_VIDEO, enableDualVideo);
                        enableShutter = b.getBoolean(PrefProvider.KEY_SHUTTER, enableShutter);
                        enableDisableThermal = b.getBoolean(PrefProvider.KEY_DISABLE_THERMAL, enableDisableThermal);
                        enableHighResPhoto = b.getBoolean(PrefProvider.KEY_HIGH_RES_PHOTO, enableHighResPhoto);
                        enableLogFormat = b.getBoolean(PrefProvider.KEY_LOG_FORMAT, enableLogFormat);
                        enableMiMovie = b.getBoolean(PrefProvider.KEY_MIMOVIE, enableMiMovie);
                        enablePrompter = b.getBoolean(PrefProvider.KEY_PROMPTER, enablePrompter);
                        enableFocusPeak = b.getBoolean(PrefProvider.KEY_FOCUS_PEAK, enableFocusPeak);
                        enableTrackFocus = b.getBoolean(PrefProvider.KEY_TRACK_FOCUS, b.getBoolean(PrefProvider.KEY_LEICA_LENSES, enableTrackFocus));
                        enableDocMode = b.getBoolean(PrefProvider.KEY_DOC_MODE, enableDocMode);
                        enableAudioMic = b.getBoolean(PrefProvider.KEY_AUDIO_MIC, enableAudioMic);
                        XposedBridge.log("[" + TAG + "] Preferences updated via ContentProvider: 4k60=" + enable4k60 + ", 50MP=" + enableHighResPhoto);
                    }
                }
            } catch (Throwable cpErr) {
                XposedBridge.log("[" + TAG + "] ContentProvider sync attempt notice: " + cpErr.getMessage());
            }
        }
    }

    private void hookContextReload(final XC_LoadPackage.LoadPackageParam lpparam) {
        // 1. Hook ContextWrapper.attachBaseContext
        try {
            XposedHelpers.findAndHookMethod(android.content.ContextWrapper.class, "attachBaseContext", android.content.Context.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Context ctx = (Context) param.args[0];
                    reloadAllPreferences(ctx);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook attachBaseContext failed: " + t.getMessage());
        }

        // 2. Hook Application.onCreate
        try {
            XposedHelpers.findAndHookMethod(android.app.Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Context ctx = (Context) param.thisObject;
                    reloadAllPreferences(ctx);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook Application.onCreate failed: " + t.getMessage());
        }

        // 3. Hook Activity.onResume for live switch updates
        try {
            XposedHelpers.findAndHookMethod(android.app.Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Context ctx = (Context) param.thisObject;
                    reloadAllPreferences(ctx);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook Activity.onResume failed: " + t.getMessage());
        }
    }

    // ==========================================
    // AOSP / Custom ROM Missing Resources & FragmentTopConfig Crash Guard
    // Resolves Resources$NotFoundException on AOSP ports (e.g. Evolution X)
    // for missing vendor string IDs (such as 0x7f130379 for Leica Vibrant & 0x7f130378 for Leica Authentic)
    // and guards FragmentTopConfig top bar initialization.
    // ==========================================
    private void hookAospResourceFixes(final XC_LoadPackage.LoadPackageParam lpparam) {
        // 1. Hook android.content.res.Resources (getText & getString)
        try {
            Class<?> resourcesClass = XposedHelpers.findClassIfExists("android.content.res.Resources", lpparam.classLoader);
            if (resourcesClass == null) {
                resourcesClass = android.content.res.Resources.class;
            }

            XC_MethodHook resourceStringHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0 && param.args[0] instanceof Integer) {
                        int id = (Integer) param.args[0];
                        switch (id) {
                            case 0x7f130379:
                                param.setResult("Leica Vibrant");
                                break;
                            case 0x7f130378:
                                param.setResult("Leica Authentic");
                                break;
                            case 0x7f13036c:
                                param.setResult("Leica");
                                break;
                            case 0x7f13036b:
                                param.setResult("LEICA");
                                break;
                        }
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.hasThrowable() && param.getThrowable() instanceof android.content.res.Resources.NotFoundException) {
                        if (param.args.length > 1 && param.args[1] instanceof CharSequence) {
                            param.setResult(param.args[1]);
                        } else {
                            param.setResult("");
                        }
                    }
                }
            };

            XposedBridge.hookAllMethods(resourcesClass, "getText", resourceStringHook);
            XposedBridge.hookAllMethods(resourcesClass, "getString", resourceStringHook);
            XposedBridge.log("[" + TAG + "] Resources string missing ID & Leica fallback hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook Resources string failed: " + t.getMessage());
        }

        // 2. Hook androidx.fragment.app.Fragment.getString
        try {
            Class<?> fragmentClass = XposedHelpers.findClassIfExists("androidx.fragment.app.Fragment", lpparam.classLoader);
            if (fragmentClass != null) {
                XposedBridge.hookAllMethods(fragmentClass, "getString", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof Integer) {
                            int id = (Integer) param.args[0];
                            switch (id) {
                                case 0x7f130379:
                                    param.setResult("Leica Vibrant");
                                    break;
                                case 0x7f130378:
                                    param.setResult("Leica Authentic");
                                    break;
                                case 0x7f13036c:
                                    param.setResult("Leica");
                                    break;
                                case 0x7f13036b:
                                    param.setResult("LEICA");
                                    break;
                            }
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.hasThrowable() && param.getThrowable() instanceof android.content.res.Resources.NotFoundException) {
                            XposedBridge.log("[" + TAG + "] Handled missing string in Fragment.getString for ID: 0x" + Integer.toHexString((Integer) param.args[0]));
                            param.setResult("");
                        }
                    }
                });
                XposedBridge.log("[" + TAG + "] Fragment.getString missing ID hooks installed");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook Fragment.getString failed: " + t.getMessage());
        }

        // 3. Crash Guard on com.android.camera.fragment.top.FragmentTopConfig
        try {
            Class<?> topConfigClass = XposedHelpers.findClassIfExists("com.android.camera.fragment.top.FragmentTopConfig", lpparam.classLoader);
            if (topConfigClass != null) {
                XC_MethodHook safeFragmentHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.hasThrowable()) {
                            XposedBridge.log("[" + TAG + "] Caught and suppressed crash in FragmentTopConfig." + param.method.getName() + ": " + param.getThrowable());
                            param.setResult(null);
                        }
                    }
                };
                XposedBridge.hookAllMethods(topConfigClass, "setTopImageResource", safeFragmentHook);
                XposedBridge.hookAllMethods(topConfigClass, "provideAnimateElement", safeFragmentHook);
                XposedBridge.hookAllMethods(topConfigClass, "initView", safeFragmentHook);
                XposedBridge.log("[" + TAG + "] FragmentTopConfig crash guard hooks installed");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook FragmentTopConfig crash guard failed: " + t.getMessage());
        }
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
                        // Allow 4K 60FPS (2108), 4K 30FPS (2106), and standard 4K (8) for both rear and front
                        if (quality == 2108 || quality == 2106 || quality == 8) {
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
                        boolean has4k30 = false;
                        for (Object item : list) {
                            try {
                                Object val = XposedHelpers.getObjectField(item, "mValue");
                                if ("8,60".equals(val) || (val != null && val.toString().contains("2108"))) {
                                    has4k60 = true;
                                }
                                if ("8".equals(val) || "8,30".equals(val) || (val != null && val.toString().contains("2106"))) {
                                    has4k30 = true;
                                }
                            } catch (Throwable ignored) {}
                        }
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
                                if (!has4k30) {
                                    Object item30 = createMethod.invoke(param.thisObject, 8, mode, 162, 180);
                                    if (item30 != null) list.add(0, item30);
                                }
                                if (!has4k60) {
                                    Object item60 = createMethod.invoke(param.thisObject, 2108, mode, 162, 180);
                                    if (item60 != null) list.add(item60);
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[" + TAG + "] init4K injection error: " + t.getMessage());
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
                        boolean has4k30 = false;
                        for (Object item : list) {
                            try {
                                Object val = XposedHelpers.getObjectField(item, "mValue");
                                if ("8,60".equals(val) || (val != null && val.toString().contains("2108"))) {
                                    has4k60 = true;
                                }
                                if ("8".equals(val) || "8,30".equals(val) || (val != null && val.toString().contains("2106"))) {
                                    has4k30 = true;
                                }
                            } catch (Throwable ignored) {}
                        }
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
                                // Inject 4K 30FPS and 4K 60FPS for both rear and front cameras
                                if (!has4k30) {
                                    Object item30 = createMethod.invoke(param.thisObject, 8, 162, 162, 180);
                                    if (item30 != null) list.add(0, item30);
                                }
                                if (!has4k60) {
                                    Object item60 = createMethod.invoke(param.thisObject, 2108, 162, 162, 180);
                                    if (item60 != null) list.add(item60);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook 4K quality failed: " + t.getMessage());
        }

        // CameraCapabilities & CameraCapabilitiesUtil 4K quality unlock (unlocks front camera 4K support)
        try {
            XC_MethodHook videoQualitySupportHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enable4k60) return;
                    if (param.args.length > 0 && param.args[0] instanceof Integer) {
                        int q = (Integer) param.args[0];
                        if (q == 8 || q == 2108 || q == 2106) {
                            param.setResult(true);
                        }
                    }
                }
            };
            Class<?> capsClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            if (capsClass != null) {
                XposedBridge.hookAllMethods(capsClass, "isSupportVideoQuality", videoQualitySupportHook);
            }
            Class<?> capsUtilClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
            if (capsUtilClass != null) {
                XposedBridge.hookAllMethods(capsUtilClass, "isSupportVideoQuality", videoQualitySupportHook);
            }
        } catch (Throwable ignored) {}

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

        XposedBridge.log("[" + TAG + "] 4K 60FPS Video Recording hooked successfully");
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
                    Object existing = param.getResult();
                    Map<String, Integer> map;
                    if (existing instanceof Map) {
                        map = new HashMap<>((Map<String, Integer>) existing);
                    } else {
                        map = new HashMap<>();
                    }
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
    // Camera Mode Helper
    // ==========================================
    private static int getCurrentCameraMode(ClassLoader cl) {
        try {
            Class<?> dr = XposedHelpers.findClassIfExists("com.android.camera.data.DataRepository", cl);
            if (dr != null) {
                Method mDataGlobal = dr.getMethod("dataItemGlobal");
                Object global = mDataGlobal.invoke(null);
                if (global != null) {
                    Method mMode = global.getClass().getMethod("getCurrentMode");
                    Object modeObj = mMode.invoke(global);
                    if (modeObj instanceof Integer) {
                        return (Integer) modeObj;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return 163; // Default: Photo mode (163)
    }

    // ==========================================
    // 3. Pro RAW Bayer Capture (Native 12.5MP Bayer stream - Hardware/HAL limit for MT6877)
    // ==========================================
    private void hookRaw50MP(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XC_MethodHook trueHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableRaw) return;
                    param.setResult(true);
                }
            };

            Class<?> utilClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
            if (utilClass != null) {
                XposedBridge.hookAllMethods(utilClass, "isSupportRaw", trueHook);
                XposedBridge.hookAllMethods(utilClass, "isSupportUltraPixelRaw", trueHook);
                XposedBridge.hookAllMethods(utilClass, "isUltraPixelRawPhotographySupported", trueHook);
            }

            Class<?> capsClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            if (capsClass != null) {
                XposedBridge.hookAllMethods(capsClass, "isSupportRaw", trueHook);
                XposedBridge.hookAllMethods(capsClass, "isSupportUltraPixelRaw", trueHook);
                XposedBridge.hookAllMethods(capsClass, "isUltraPixelRawPhotographySupported", trueHook);
            }

            // HardwareCapabilities wide ultra pixel raw support
            Class<?> hwCaps = XposedHelpers.findClassIfExists("com.android.camera2.HardwareCapabilities", lpparam.classLoader);
            if (hwCaps != null) {
                XposedBridge.hookAllMethods(hwCaps, "isUltraPixelRawSupportedByWide", trueHook);
            }

            // CameraSettings.isUltraPixelRawOn() -> MUST ONLY return true in Pro mode (167)
            // Strictly exclude Photo mode (163) to prevent HAL stream configuration failure on startup
            Class<?> cameraSettings = XposedHelpers.findClassIfExists("com.android.camera.CameraSettings", lpparam.classLoader);
            if (cameraSettings != null) {
                XposedBridge.hookAllMethods(cameraSettings, "isUltraPixelRawOn", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableRaw) return;
                        int mode = getCurrentCameraMode(lpparam.classLoader);
                        if (mode == 167) { // Strictly Pro mode ONLY
                            param.setResult(true);
                        }
                    }
                });
            }

            try {
                Class<?> rubyClass = XposedHelpers.findClassIfExists("com.mi.device.Ruby", lpparam.classLoader);
                if (rubyClass != null) {
                    XposedBridge.hookAllMethods(rubyClass, "OooOo00", trueHook);
                }
            } catch (Throwable ignored) {}

            XposedBridge.log("[" + TAG + "] RAW capture hooked safely (native 12.5MP Bayer stream)");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook RAW failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 4. Leica Authentic & Vibrant Profiles + Leica Watermark
    // ==========================================
    private void hookLeicaProfiles(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XC_MethodHook trueHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableLeica) return;
                    if (param.method instanceof java.lang.reflect.Method) {
                        java.lang.reflect.Method m = (java.lang.reflect.Method) param.method;
                        if (m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) return;
                    }
                    param.setResult(true);
                }
            };

            XC_MethodHook cvThemeHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableLeica) return;
                    if (param.method instanceof java.lang.reflect.Method) {
                        java.lang.reflect.Method m = (java.lang.reflect.Method) param.method;
                        if (m.getReturnType() != String.class) return;
                    }
                    param.setResult("cv.MiThemeCompatCV");
                }
            };

            // 1. Hook DataItemFeature (o000Oo0.OooO00o)
            try {
                Class<?> dataItemFeature = XposedHelpers.findClassIfExists("o000Oo0.OooO00o", lpparam.classLoader);
                if (dataItemFeature != null) {
                    XposedBridge.hookAllMethods(dataItemFeature, "o0O0OOoo", trueHook); // Custom theme enabled flag
                    XposedBridge.hookAllMethods(dataItemFeature, "o000OOoO", cvThemeHook); // Theme name
                    XposedBridge.hookAllMethods(dataItemFeature, "o00o0Ooo", trueHook); // CV top bar button support
                }
            } catch (Throwable ignored) {}

            // 2. Hook Common & Ruby device feature providers
            try {
                Class<?> commonClass = XposedHelpers.findClassIfExists("com.mi.device.Common", lpparam.classLoader);
                if (commonClass != null) {
                    XposedBridge.hookAllMethods(commonClass, "o000OO00", cvThemeHook);
                    XposedBridge.hookAllMethods(commonClass, "o000OOoO", cvThemeHook);
                    XposedBridge.hookAllMethods(commonClass, "o00oo00O", trueHook);
                    XposedBridge.hookAllMethods(commonClass, "o00Oo000", trueHook);
                    XposedBridge.hookAllMethods(commonClass, "o00o0Ooo", trueHook);
                }
            } catch (Throwable ignored) {}

            try {
                Class<?> rubyClass = XposedHelpers.findClassIfExists("com.mi.device.Ruby", lpparam.classLoader);
                if (rubyClass != null) {
                    XposedBridge.hookAllMethods(rubyClass, "o000OO00", cvThemeHook);
                    XposedBridge.hookAllMethods(rubyClass, "o000OOoO", cvThemeHook);
                    XposedBridge.hookAllMethods(rubyClass, "o00oo00O", trueHook);
                    XposedBridge.hookAllMethods(rubyClass, "o00Oo000", trueHook);
                    XposedBridge.hookAllMethods(rubyClass, "o00o0Ooo", trueHook);
                }
            } catch (Throwable ignored) {}

            // 3. Force MiThemeCompat.getImpl() to return MiThemeCompatCV
            try {
                Class<?> miThemeCompat = XposedHelpers.findClassIfExists("com.android.camera2.compat.theme.MiThemeCompat", lpparam.classLoader);
                if (miThemeCompat != null) {
                    XposedBridge.hookAllMethods(miThemeCompat, "getImpl", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableLeica) return;
                            try {
                                Class<?> cvClass = XposedHelpers.findClassIfExists("com.android.camera2.compat.theme.custom.cv.MiThemeCompatCV", lpparam.classLoader);
                                if (cvClass != null) {
                                    param.setResult(cvClass.newInstance());
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            } catch (Throwable ignored) {}

            // 4. Ensure ComponentConfigCvType is populated and never reports empty
            try {
                Class<?> cvTypeClass = XposedHelpers.findClassIfExists("com.android.camera.data.data.config.ComponentConfigCvType", lpparam.classLoader);
                if (cvTypeClass != null) {
                    XposedBridge.hookAllMethods(cvTypeClass, "isEmpty", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableLeica) return;
                            param.setResult(false);
                        }
                    });
                    XposedBridge.hookAllMethods(cvTypeClass, "reInit", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableLeica) return;
                            try {
                                List items = (List) XposedHelpers.getObjectField(param.thisObject, "mItems");
                                if (items == null || items.isEmpty()) {
                                    if (items == null) {
                                        items = new java.util.ArrayList();
                                        XposedHelpers.setObjectField(param.thisObject, "mItems", items);
                                    }
                                    Class<?> cdiClass = XposedHelpers.findClass("com.android.camera.data.data.ComponentDataItem", lpparam.classLoader);
                                    java.lang.reflect.Constructor<?> ctor = cdiClass.getConstructor(int.class, int.class, int.class, int.class, String.class);
                                    items.add(ctor.newInstance(0x7f080414, 0x7f080416, 0x7f080414, 0x7f130379, "0")); // Leica Vibrant
                                    items.add(ctor.newInstance(0x7f080413, 0x7f080415, 0x7f080413, 0x7f130378, "1")); // Leica Authentic
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            } catch (Throwable ignored) {}

            // 5. Hook CameraCapabilitiesUtil and CameraCapabilities (strictly CvType & Watermark, NO CvLens)
            Class<?> utilClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
            if (utilClass != null) {
                XposedBridge.hookAllMethods(utilClass, "isSupportCvType", trueHook);
                XposedBridge.hookAllMethods(utilClass, "isSupportCvWatermark", trueHook);
            }

            Class<?> capsClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            if (capsClass != null) {
                XposedBridge.hookAllMethods(capsClass, "isSupportCvType", trueHook);
                XposedBridge.hookAllMethods(capsClass, "isSupportCvWatermark", trueHook);
            }

            // 6. Hook CameraSettings for CV Watermark & true Leica profile resolution (0 = Vibrant, 1 = Authentic)
            Class<?> cameraSettings = XposedHelpers.findClassIfExists("com.android.camera.CameraSettings", lpparam.classLoader);
            if (cameraSettings != null) {
                XposedBridge.hookAllMethods(cameraSettings, "isSupportCvWatermark", trueHook);
                try {
                    XposedHelpers.findAndHookMethod(cameraSettings, "getCvType", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableLeica) return;
                            try {
                                Class<?> dr = XposedHelpers.findClassIfExists("com.android.camera.data.DataRepository", lpparam.classLoader);
                                if (dr != null) {
                                    Object config = XposedHelpers.callStaticMethod(dr, "dataItemConfig");
                                    if (config != null) {
                                        Object compCv = XposedHelpers.callMethod(config, "getComponentConfigCvType");
                                        if (compCv != null) {
                                            String val = (String) XposedHelpers.callMethod(compCv, "getComponentValue", 160);
                                            if (val != null) {
                                                param.setResult(Integer.parseInt(val));
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}
            }

            // 7. Hook CVWatermark / ICvWatermark
            try {
                Class<?> cvWatermark = XposedHelpers.findClassIfExists("com.android.camera2.compat.theme.custom.cv.cvwatermark.CVWatermark", lpparam.classLoader);
                if (cvWatermark != null) {
                    XposedBridge.hookAllMethods(cvWatermark, "isSupport", trueHook);
                }
            } catch (Throwable ignored) {}

            try {
                Class<?> icvWatermark = XposedHelpers.findClassIfExists("com.android.camera2.compat.theme.custom.cv.cvwatermark.ICvWatermark", lpparam.classLoader);
                if (icvWatermark != null) {
                    XposedBridge.hookAllMethods(icvWatermark, "isSupport", trueHook);
                }
            } catch (Throwable ignored) {}

            XposedBridge.log("[" + TAG + "] Leica Profiles, Theme & Watermark hooked successfully");
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
                Class<?> dataItemFeature = XposedHelpers.findClassIfExists("o000Oo0.OooO00o", lpparam.classLoader);
                if (dataItemFeature != null) {
                    XposedBridge.hookAllMethods(dataItemFeature, "o00o0ooo", trueHook);
                    XposedBridge.log("[" + TAG + "] DataItemFeature.o00o0ooo() hooked -> true");

                    // 2b. o000ooo() -> Camera opening order: MUST return true so MAIN_SOURCE (Back Camera 0)
                    // opens FIRST as master before SUB_SOURCE (Front Camera 1)
                    XposedBridge.hookAllMethods(dataItemFeature, "o000ooo", trueHook);
                    XposedBridge.log("[" + TAG + "] DataItemFeature.o000ooo() hooked -> true (Back Camera 0 opens first)");
                }
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
            // CRITICAL: Only sanitize sessionType in Dual Video Mode (204)! Do NOT sanitize in Pro mode (0x8003) or Super Night (0x9005)!
            try {
                Class<?> compatUtils = XposedHelpers.findClassIfExists("com.android.camera.lib.compatibility.util.CompatibilityUtils", lpparam.classLoader);
                if (compatUtils != null) {
                    XposedBridge.hookAllMethods(compatUtils, "createCaptureSessionWithSessionConfiguration", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableDualVideo) return;
                            int currentMode = getCurrentCameraMode(lpparam.classLoader);
                            if (currentMode != 204) return; // Strictly isolate to Dual Video mode
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
                }
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook CompatibilityUtils failed: " + t.getMessage());
            }

            // 9. Protect Camera 0 and Camera 1 from mutual closure during Dual Video switching
            try {
                Class<?> closeCallableClass = XposedHelpers.findClassIfExists("com.xiaomi.camera.device.callable.CloseCameraCallable", lpparam.classLoader);
                if (closeCallableClass != null) {
                    XposedBridge.hookAllConstructors(closeCallableClass, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableDualVideo) return;
                            int currentMode = getCurrentCameraMode(lpparam.classLoader);
                            if (currentMode != 204) return; // Strictly isolate to Dual Video mode
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
                }
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
                // Only hook specific support/capability check methods, NOT error/state check methods
                String[] dualVideoChecks = {"isDualVideoSupported", "isDualCameraAllowed", "isConcurrentSupported", "isSupported", "canOpenDualCamera", "isSupportPIP"};
                for (String methodName : dualVideoChecks) {
                    try { XposedBridge.hookAllMethods(checkerClass, methodName, checkTrueHook); } catch (Throwable ignored) {}
                }
                // Also hook any method that has "support" or "allow" or "enable" in its name
                for (java.lang.reflect.Method m : checkerClass.getDeclaredMethods()) {
                    if (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) {
                        String mn = m.getName().toLowerCase();
                        if (mn.contains("support") || mn.contains("allow") || mn.contains("enable") || mn.contains("capable")) {
                            try { XposedBridge.hookAllMethods(checkerClass, m.getName(), checkTrueHook); } catch (Throwable ignored) {}
                        }
                    }
                }
                XposedBridge.log("[" + TAG + "] DualVideo Checker all boolean checks bypassed");
            } catch (Throwable t) {
                XposedBridge.log("[" + TAG + "] Hook Checker failed: " + t.getMessage());
            }

            // 14. Hook PIPInfo to enable PIP capability for MediaTek (legacy support)
            try {
                Class<?> pipInfoClass = XposedHelpers.findClassIfExists("com.android.camera.PIPInfo", lpparam.classLoader);
                if (pipInfoClass != null) {
                    try { XposedBridge.hookAllMethods(pipInfoClass, "checkOpenAbility", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableDualVideo) return;
                            param.setResult(0); // 0 = OK, can open
                        }
                    }); } catch (Throwable ignored) {}
                    try { XposedBridge.hookAllMethods(pipInfoClass, "isSupportPIP", trueHook); } catch (Throwable ignored) {}
                    XposedBridge.log("[" + TAG + "] PIPInfo hooks installed");
                }
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
                    int mode = getCurrentCameraMode(lpparam.classLoader);
                    // Shutter speed manual slider is strictly for Pro Photo (167) and Pro Video (180)
                    if (mode == 167 || mode == 180) {
                        // 62.5 microseconds (1/16000s) to 60,000,000,000 nanoseconds (60s)
                        param.setResult(new Range<Long>(62500L, 60000000000L));
                    }
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
                            } else if (msg.what == 10 && enableDualVideo && getCurrentCameraMode(lpparam.classLoader) == 204) {
                                // Suppress "Can't connect to the camera" fatal error dialog strictly in Dual Video mode
                                XposedBridge.log("[" + TAG + "] ActivityHandler camera error msg 10 intercepted in Dual Video mode (arg1=" + msg.arg1 + "), suppressing forced shutdown dialog");
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

    // ==========================================
    // 8. 50MP Normal Photo — MediaTek ISP remosaic
    // ==========================================
    private void hookHighResPhoto(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook 1: MiCamera2.alwaysUseRemosaicSize() -> forces wide remote surface
            Class<?> miCamera2 = XposedHelpers.findClass("com.android.camera2.MiCamera2", lpparam.classLoader);
            XposedBridge.hookAllMethods(miCamera2, "alwaysUseRemosaicSize", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableHighResPhoto) return;

                    // Strictly exclude Portrait mode (171) or non-capture modes!
                    int currentMode = getCurrentCameraMode(lpparam.classLoader);
                    if (currentMode == 171 || (currentMode != 163 && currentMode != 167 && currentMode != 175)) {
                        return;
                    }

                    try {
                        Method mId = param.thisObject.getClass().getMethod("getId");
                        Object idObj = mId.invoke(param.thisObject);
                        String idStr = String.valueOf(idObj);
                        if (!"0".equals(idStr)) return; // Strictly rear main camera ONLY
                    } catch (Throwable t) {
                        try {
                            Class<?> csClass = XposedHelpers.findClass("com.android.camera.CameraSettings", lpparam.classLoader);
                            Method mFront = csClass.getDeclaredMethod("isFrontCamera");
                            mFront.setAccessible(true);
                            if ((boolean) mFront.invoke(null)) return;
                        } catch (Throwable ignored) {}
                    }
                    param.setResult(true);
                }
            });

            // Spoof CameraSettings for MiCamera2 to enable backend remosaic vendor tags,
            // while keeping UI oblivious so AI/HDR buttons stay enabled!
            Class<?> cameraSettings = XposedHelpers.findClass("com.android.camera.CameraSettings", lpparam.classLoader);
            XC_MethodHook spoofBackendHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!enableHighResPhoto) return;

                    int currentMode = getCurrentCameraMode(lpparam.classLoader);
                    if (currentMode == 171 || (currentMode != 163 && currentMode != 167 && currentMode != 175)) {
                        return;
                    }

                    boolean originalResult = false;
                    if (param.getResult() != null) {
                        originalResult = (boolean) param.getResult();
                    }
                    if (originalResult) return; // Already true, do nothing

                    // Do not spoof if it's the front camera or non-main sensor!
                    try {
                        Class<?> csClass = XposedHelpers.findClassIfExists("com.android.camera.CameraSettings", lpparam.classLoader);
                        if (csClass != null) {
                            try {
                                Method mFront = csClass.getDeclaredMethod("isFrontCamera");
                                mFront.setAccessible(true);
                                if ((boolean) mFront.invoke(null)) {
                                    return; // Strictly abort for front camera
                                }
                            } catch (Throwable ignored) {}
                            try {
                                Method mId = csClass.getDeclaredMethod("getCameraId");
                                mId.setAccessible(true);
                                Object camId = mId.invoke(null);
                                if (!"0".equals(String.valueOf(camId))) {
                                    return; // Strictly abort for ultra-wide, macro, etc.
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}

                    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                    for (StackTraceElement element : stack) {
                        String className = element.getClassName();
                        // If the call originated from the camera2 backend, spoof true!
                        if (className.startsWith("com.android.camera2.") || 
                            className.startsWith("android.hardware.camera2")) {
                            param.setResult(true);
                            return;
                        }
                    }
                }
            };
            XposedBridge.hookAllMethods(cameraSettings, "isUltraPixelOn", spoofBackendHook);
            XposedBridge.hookAllMethods(cameraSettings, "isUltraPixelRearOn", spoofBackendHook);
            XposedBridge.log("[" + TAG + "] CameraSettings backend spoof hooked!");


            XC_MethodHook sizeListHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // Strictly check that this is the main rear camera (Camera 0)
                    try {
                        Object caps = param.thisObject;
                        if (caps != null) {
                            Method mFacing = caps.getClass().getMethod("isFacingFront");
                            if ((boolean) mFacing.invoke(caps)) return; // Strictly skip front camera!
                            
                            Method mId = caps.getClass().getMethod("getCameraId");
                            Object idObj = mId.invoke(caps);
                            String idStr = String.valueOf(idObj);
                            if (!"0".equals(idStr)) return; // Strictly skip ultra-wide, macro, etc.
                        }
                    } catch (Throwable t) {
                        try {
                            Class<?> csClass = XposedHelpers.findClass("com.android.camera.CameraSettings", lpparam.classLoader);
                            Method mFront = csClass.getDeclaredMethod("isFrontCamera");
                            mFront.setAccessible(true);
                            if ((boolean) mFront.invoke(null)) return;
                        } catch (Throwable ignored) {}
                    }

                    if (param.args.length >= 1 && param.args[0] instanceof Integer) {
                        int format = (Integer) param.args[0];
                        int assignedMode = (param.args.length >= 2 && param.args[1] instanceof Integer) 
                                ? (Integer) param.args[1] 
                                : getCurrentCameraMode(lpparam.classLoader);

                        // Strictly exclude Portrait mode (171) and any video/unsupported modes
                        if (assignedMode == 171 || (assignedMode != 163 && assignedMode != 167 && assignedMode != 175)) {
                            return;
                        }

                        boolean isJpeg = (format == 256); // ImageFormat.JPEG

                        // 50MP injection is strictly for JPEG capture via MediaTek ISP remosaic
                        // Hardware HAL for MT6877 (Dimensity 1080) does not support 50MP RAW stream
                        if (!isJpeg || !enableHighResPhoto) {
                            return;
                        }

                        Object result = param.getResult();
                        if (result instanceof java.util.List) {
                            java.util.List srcList = (java.util.List) result;
                            boolean has50mp = false;
                            for (Object obj : srcList) {
                                try {
                                    int w, h;
                                    try {
                                        w = XposedHelpers.getIntField(obj, "width");
                                        h = XposedHelpers.getIntField(obj, "height");
                                    } catch (Throwable e) {
                                        w = (int) XposedHelpers.callMethod(obj, "getWidth");
                                        h = (int) XposedHelpers.callMethod(obj, "getHeight");
                                    }
                                    if ((w == 8192 && h == 6144) || (w == 6144 && h == 8192)) {
                                        has50mp = true;
                                        break;
                                    }
                                } catch (Throwable ignored) {}
                            }
                            if (!has50mp) {
                                try {
                                    Class<?> cameraSizeClass = XposedHelpers.findClassIfExists("com.android.camera.CameraSize", lpparam.classLoader);
                                    if (cameraSizeClass != null) {
                                        java.lang.reflect.Constructor<?> ctor = cameraSizeClass.getConstructor(int.class, int.class);
                                        // Build a new mutable list to avoid UnsupportedOperationException on immutable lists
                                        java.util.List<Object> newList = new java.util.ArrayList<>();
                                        // 4:3 (True 50MP Bayer resolution)
                                        newList.add(ctor.newInstance(8192, 6144));
                                        newList.add(ctor.newInstance(6144, 8192));
                                        // 16:9, 1:1, 20:9 crops only for JPEG
                                        newList.add(ctor.newInstance(8192, 4608));
                                        newList.add(ctor.newInstance(4608, 8192));
                                        newList.add(ctor.newInstance(6144, 6144));
                                        newList.add(ctor.newInstance(8192, 3692));
                                        newList.add(ctor.newInstance(3692, 8192));
                                        newList.addAll(srcList);
                                        param.setResult(newList);
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log("[" + TAG + "] Error injecting CameraSize: " + t.getMessage());
                                }
                            }
                        }
                    }
                }
            };
            
            Class<?> capsClass = XposedHelpers.findClass("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            XposedBridge.hookAllMethods(capsClass, "getSupportedOutputSizeWithAssignedMode", sizeListHook);
            XposedBridge.hookAllMethods(capsClass, "getSupportedOutputSize", sizeListHook);

            XposedBridge.log("[" + TAG + "] 50MP Normal Photo successfully hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook 50MP Normal Photo failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 9. Pro Video LOG Profile Hook
    // ==========================================
    private void hookVideoLog(final XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook logHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!enableLogFormat) return;
                int mode = getCurrentCameraMode(lpparam.classLoader);
                // Video LOG is strictly for Pro Video (180) and Normal Video (162)
                if (mode != 180 && mode != 162) {
                    param.setResult(false);
                    return;
                }
                param.setResult(true);
            }
        };

        try {
            Class<?> utilClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
            if (utilClass != null) {
                try { XposedBridge.hookAllMethods(utilClass, "isSupportedVideoLogFormat", logHook); } catch (Throwable ignored) {}
            }
            Class<?> capsClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            if (capsClass != null) {
                try { XposedBridge.hookAllMethods(capsClass, "isSupportedVideoLogFormat", logHook); } catch (Throwable ignored) {}
            }
            XposedBridge.log("[" + TAG + "] Pro Video LOG profile hooks installed with mode guards");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook VideoLog failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 10. Cinematic MiMovie (2.39:1 Widescreen) Hook
    // ==========================================
    private void hookMiMovie(final XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook movieHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!enableMiMovie) return;
                int mode = getCurrentCameraMode(lpparam.classLoader);
                // STRICT MODE GUARD:
                // Only allow MiMovie in standard video and photo modes (162, 180, 169, 176, 163, 167).
                // STRICTLY BLOCK in Portrait (171), Night (173), Panorama (166), Slow Motion (168, 172), Document (186).
                if (mode == 171 || mode == 173 || mode == 166 || mode == 168 || mode == 172 || mode == 186) {
                    param.setResult(false);
                    return;
                }
                param.setResult(true);
            }
        };

        try {
            Class<?> utilClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
            if (utilClass != null) {
                try { XposedBridge.hookAllMethods(utilClass, "isSupportedVideoMiMovie", movieHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(utilClass, "isSupportedCinematicVideo", movieHook); } catch (Throwable ignored) {}
            }
            Class<?> capsClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            if (capsClass != null) {
                try { XposedBridge.hookAllMethods(capsClass, "isSupportedVideoMiMovie", movieHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(capsClass, "isSupportedCinematicVideo", movieHook); } catch (Throwable ignored) {}
            }
            XposedBridge.log("[" + TAG + "] Cinematic MiMovie (2.39:1) hooks installed with strict mode guards");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook MiMovie failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 11. Video Teleprompter Hook
    // ==========================================
    private void hookVideoPrompter(final XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook prompterHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!enablePrompter) return;
                param.setResult(true);
            }
        };

        try {
            Class<?> dataItemFeature = XposedHelpers.findClassIfExists("o000Oo0.OooO00o", lpparam.classLoader);
            if (dataItemFeature != null) {
                try { XposedBridge.hookAllMethods(dataItemFeature, "o0O0o0o", prompterHook); } catch (Throwable ignored) {}
            }
            Class<?> commonClass = XposedHelpers.findClassIfExists("com.mi.device.Common", lpparam.classLoader);
            if (commonClass != null) {
                try { XposedBridge.hookAllMethods(commonClass, "o0O0o0o", prompterHook); } catch (Throwable ignored) {}
            }
            Class<?> rubyClass = XposedHelpers.findClassIfExists("com.mi.device.Ruby", lpparam.classLoader);
            if (rubyClass != null) {
                try { XposedBridge.hookAllMethods(rubyClass, "o0O0o0o", prompterHook); } catch (Throwable ignored) {}
            }
            Class<?> videoTrackInfo = XposedHelpers.findClassIfExists("com.android.camera.module.video.VideoTrackInfo", lpparam.classLoader);
            if (videoTrackInfo != null) {
                try { XposedBridge.hookAllMethods(videoTrackInfo, "videoPrompterSupported", prompterHook); } catch (Throwable ignored) {}
            }
            XposedBridge.log("[" + TAG + "] Video Teleprompter hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook VideoPrompter failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 12. Focus Peaking & Zebra Exposure Stripes Hook
    // ==========================================
    private void hookFocusPeakingZebra(final XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook peakHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!enableFocusPeak) return;
                int mode = getCurrentCameraMode(lpparam.classLoader);
                // Peaking and exposure zebra are manual assists for Pro Photo (167) and Pro Video (180)
                if (mode != 167 && mode != 180) {
                    param.setResult(false);
                    return;
                }
                param.setResult(true);
            }
        };

        try {
            Class<?> dataItemFeature = XposedHelpers.findClassIfExists("o000Oo0.OooO00o", lpparam.classLoader);
            if (dataItemFeature != null) {
                try { XposedBridge.hookAllMethods(dataItemFeature, "o0O0ooOO", peakHook); } catch (Throwable ignored) {}
            }
            Class<?> commonClass = XposedHelpers.findClassIfExists("com.mi.device.Common", lpparam.classLoader);
            if (commonClass != null) {
                try { XposedBridge.hookAllMethods(commonClass, "o0O0ooOO", peakHook); } catch (Throwable ignored) {}
            }
            Class<?> rubyClass = XposedHelpers.findClassIfExists("com.mi.device.Ruby", lpparam.classLoader);
            if (rubyClass != null) {
                try { XposedBridge.hookAllMethods(rubyClass, "o0O0ooOO", peakHook); } catch (Throwable ignored) {}
            }
            Class<?> utilClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
            if (utilClass != null) {
                try { XposedBridge.hookAllMethods(utilClass, "isSupportDepthFocusForMTK", peakHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(utilClass, "isSupportFocusPeak", peakHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(utilClass, "isSupportExposureFeedback", peakHook); } catch (Throwable ignored) {}
            }
            Class<?> capsClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            if (capsClass != null) {
                try { XposedBridge.hookAllMethods(capsClass, "isSupportDepthFocusForMTK", peakHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(capsClass, "isSupportFocusPeak", peakHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(capsClass, "isSupportExposureFeedback", peakHook); } catch (Throwable ignored) {}
            }
            // CRITICAL: DO NOT hook CameraSettings.isTeleX! 
            // isTeleX disables AI Audio Zoom and was conflicting with audio features.
            XposedBridge.log("[" + TAG + "] Focus Peaking & Zebra Stripes hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook FocusPeakingZebra failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 13. Motion Tracking Focus (AI Eye & Subject Auto-Tracking)
    // ==========================================
    private void hookTrackFocus(final XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook focusHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!enableTrackFocus) return;
                param.setResult(true);
            }
        };

        try {
            Class<?> capsClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            if (capsClass != null) {
                try { XposedBridge.hookAllMethods(capsClass, "isSupportTrackEye", focusHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(capsClass, "isSupportTrackFocus", focusHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(capsClass, "isTrackFocusDefined", focusHook); } catch (Throwable ignored) {}
            }
            Class<?> utilClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
            if (utilClass != null) {
                try { XposedBridge.hookAllMethods(utilClass, "isSupportTrackEye", focusHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(utilClass, "isSupportTrackFocus", focusHook); } catch (Throwable ignored) {}
            }
            Class<?> csClass = XposedHelpers.findClassIfExists("com.android.camera.CameraSettings", lpparam.classLoader);
            if (csClass != null) {
                try { XposedBridge.hookAllMethods(csClass, "isSupportTrackEye", focusHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(csClass, "isSupportTrackFocus", focusHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(csClass, "isTrackFocusOn", focusHook); } catch (Throwable ignored) {}
            }
            XposedBridge.log("[" + TAG + "] Motion Tracking Focus hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook TrackFocus failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 14. Document Scanner Mode (Mode 186) Hook
    // ==========================================
    private void hookDocumentMode(final XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook docHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!enableDocMode) return;
                param.setResult(true);
            }
        };

        try {
            Class<?> docEntry = XposedHelpers.findClassIfExists("com.android.camera.features.mode.doc.DocModuleEntry", lpparam.classLoader);
            if (docEntry != null) {
                try { XposedBridge.hookAllMethods(docEntry, "support", docHook); } catch (Throwable ignored) {}
            }
            Class<?> dataItemFeature = XposedHelpers.findClassIfExists("o000Oo0.OooO00o", lpparam.classLoader);
            if (dataItemFeature != null) {
                try { XposedBridge.hookAllMethods(dataItemFeature, "o00o0oO0", docHook); } catch (Throwable ignored) {}
            }
            Class<?> commonClass = XposedHelpers.findClassIfExists("com.mi.device.Common", lpparam.classLoader);
            if (commonClass != null) {
                try { XposedBridge.hookAllMethods(commonClass, "o00o0oO0", docHook); } catch (Throwable ignored) {}
            }
            Class<?> rubyClass = XposedHelpers.findClassIfExists("com.mi.device.Ruby", lpparam.classLoader);
            if (rubyClass != null) {
                try { XposedBridge.hookAllMethods(rubyClass, "o00o0oO0", docHook); } catch (Throwable ignored) {}
            }
            Class<?> globalClass = XposedHelpers.findClassIfExists("com.android.camera.data.data.global.DataItemGlobal", lpparam.classLoader);
            if (globalClass != null) {
                XposedBridge.hookAllMethods(globalClass, "getSortModes", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDocMode) return;
                        Object res = param.getResult();
                        if (res instanceof int[]) {
                            int[] modes = (int[]) res;
                            boolean hasDoc = false;
                            for (int m : modes) {
                                if (m == 186) {
                                    hasDoc = true;
                                    break;
                                }
                            }
                            if (!hasDoc) {
                                int[] newModes = new int[modes.length + 1];
                                System.arraycopy(modes, 0, newModes, 0, modes.length);
                                newModes[modes.length] = 186; // Mode 186 = Document Mode
                                param.setResult(newModes);
                            }
                        }
                    }
                });
            }
            XposedBridge.log("[" + TAG + "] Document Scanner Mode (Mode 186) hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook DocumentMode failed: " + t.getMessage());
        }
    }

    // ==========================================
    // 15. Directional Audio Zoom & Bluetooth Headset Mic Hook
    // ==========================================
    private void hookAudioMic(final XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook audioHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!enableAudioMic) return;
                param.setResult(true);
            }
        };

        try {
            // 1. Hook AiAudioParameterManager (the foundational capability provider for Audio Zoom on MIUI)
            Class<?> audioManagerClass = XposedHelpers.findClassIfExists("com.android.camera.aiaudio.AiAudioParameterManager", lpparam.classLoader);
            if (audioManagerClass != null) {
                try {
                    java.lang.reflect.Field f = audioManagerClass.getDeclaredField("sSupported");
                    f.setAccessible(true);
                    f.set(null, Boolean.TRUE);
                } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(audioManagerClass, "isSupportAiAudioNew", audioHook); } catch (Throwable ignored) {}
            }

            // 2. Hook ComponentRunningAiAudioNew (Top menu toggle & state evaluator)
            Class<?> runningNewClass = XposedHelpers.findClassIfExists("com.android.camera.data.data.runing.ComponentRunningAiAudioNew", lpparam.classLoader);
            if (runningNewClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(runningNewClass, "isSwitchOn", int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableAudioMic) return;
                            int mode = (Integer) param.args[0];
                            if (mode == 162 || mode == 180) { // Video or Pro Video
                                String val = (String) XposedHelpers.callMethod(param.thisObject, "getComponentValue", mode);
                                param.setResult("ON".equals(val));
                            } else {
                                param.setResult(false);
                            }
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(runningNewClass, "setEnabled", int.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableAudioMic) return;
                            int mode = (Integer) param.args[0];
                            boolean enabled = (Boolean) param.args[1];
                            if (mode == 162 || mode == 180) {
                                XposedHelpers.callMethod(param.thisObject, "setComponentValue", mode, enabled ? "ON" : "OFF");
                                param.setResult(null);
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }

            // 3. Hook ComponentRunningAiAudioSingle
            Class<?> runningSingleClass = XposedHelpers.findClassIfExists("com.android.camera.data.data.runing.ComponentRunningAiAudioSingle", lpparam.classLoader);
            if (runningSingleClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(runningSingleClass, "isSwitchOn", int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableAudioMic) return;
                            int mode = (Integer) param.args[0];
                            if (mode == 162 || mode == 180) {
                                String val = (String) XposedHelpers.callMethod(param.thisObject, "getComponentValue", mode);
                                param.setResult("ON".equals(val));
                            } else {
                                param.setResult(false);
                            }
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(runningSingleClass, "setEnabled", int.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableAudioMic) return;
                            int mode = (Integer) param.args[0];
                            boolean enabled = (Boolean) param.args[1];
                            if (mode == 162 || mode == 180) {
                                XposedHelpers.callMethod(param.thisObject, "setComponentValue", mode, enabled ? "ON" : "OFF");
                                param.setResult(null);
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }

            // 4. Hook ComponentRunningAiAudio (Extra popup options: normal, audio zoom, 3d record)
            Class<?> runningAudioClass = XposedHelpers.findClassIfExists("com.android.camera.data.data.runing.ComponentRunningAiAudio", lpparam.classLoader);
            if (runningAudioClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(runningAudioClass, "isEmpty", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableAudioMic) return;
                            param.setResult(false);
                        }
                    });
                } catch (Throwable ignored) {}
            }

            // 5. Hook CameraSettings
            Class<?> csClass = XposedHelpers.findClassIfExists("com.android.camera.CameraSettings", lpparam.classLoader);
            if (csClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(csClass, "isAiAudioOn", int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!enableAudioMic) return;
                            int mode = (Integer) param.args[0];
                            if (mode == 162 || mode == 180) {
                                try {
                                    Class<?> dr = XposedHelpers.findClassIfExists("com.android.camera.data.DataRepository", lpparam.classLoader);
                                    if (dr != null) {
                                        Object running = XposedHelpers.callStaticMethod(dr, "dataItemRunning");
                                        if (running != null) {
                                            Object compNew = XposedHelpers.callMethod(running, "getComponentRunningAiAudioNew");
                                            if (compNew != null) {
                                                boolean on = (Boolean) XposedHelpers.callMethod(compNew, "isSwitchOn", mode);
                                                if (on) {
                                                    param.setResult(true);
                                                }
                                            }
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }

            // 6. Hook Common & Ruby device classes
            Class<?> commonClass = XposedHelpers.findClassIfExists("com.mi.device.Common", lpparam.classLoader);
            if (commonClass != null) {
                try { XposedBridge.hookAllMethods(commonClass, "o00o0000", audioHook); } catch (Throwable ignored) {}
            }
            Class<?> rubyClass = XposedHelpers.findClassIfExists("com.mi.device.Ruby", lpparam.classLoader);
            if (rubyClass != null) {
                try { XposedBridge.hookAllMethods(rubyClass, "o00o0000", audioHook); } catch (Throwable ignored) {}
            }

            // 7. Hook CameraCapabilitiesUtil & CameraCapabilities
            Class<?> utilClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilitiesUtil", lpparam.classLoader);
            if (utilClass != null) {
                try { XposedBridge.hookAllMethods(utilClass, "isSupport3MicEnable", audioHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(utilClass, "isSupportBluetoothHeadsetMic", audioHook); } catch (Throwable ignored) {}
            }
            Class<?> capsClass = XposedHelpers.findClassIfExists("com.android.camera2.CameraCapabilities", lpparam.classLoader);
            if (capsClass != null) {
                try { XposedBridge.hookAllMethods(capsClass, "isSupport3MicEnable", audioHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(capsClass, "isSupportBluetoothHeadsetMic", audioHook); } catch (Throwable ignored) {}
            }
            XposedBridge.log("[" + TAG + "] Directional Audio Zoom & Bluetooth Headset Mic hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook AudioMic failed: " + t.getMessage());
        }
    }
    // ==========================================
    // 9. Framework-Level Hooks (AOSP / GCam / Open Camera compatible)
    // ==========================================
    private void hookFrameworkLevel(final XC_LoadPackage.LoadPackageParam lpparam) {
        // A. CameraCharacteristics universal hooks
        try {
            Class<?> charClass = XposedHelpers.findClassIfExists("android.hardware.camera2.CameraCharacteristics", lpparam.classLoader);
            if (charClass != null) {
                XposedBridge.hookAllMethods(charClass, "get", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length == 0 || param.args[0] == null) return;
                        Object key = param.args[0];
                        String keyName = key.toString();

                        // 60s Shutter Speed Range
                        if (enableShutter && keyName.contains("SENSOR_INFO_EXPOSURE_TIME_RANGE")) {
                            param.setResult(new Range<Long>(62500L, 60000000000L));
                        }
                        // 4K 60FPS AE Target FPS Ranges
                        else if (enable4k60 && keyName.contains("CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES")) {
                            Object result = param.getResult();
                            if (result instanceof Range[]) {
                                Range<Integer>[] ranges = (Range<Integer>[]) result;
                                boolean has60 = false;
                                for (Range<Integer> r : ranges) {
                                    if (r.getUpper() >= 60) { has60 = true; break; }
                                }
                                if (!has60) {
                                    Range<Integer>[] newRanges = new Range[ranges.length + 2];
                                    System.arraycopy(ranges, 0, newRanges, 0, ranges.length);
                                    newRanges[ranges.length] = new Range<>(60, 60);
                                    newRanges[ranges.length + 1] = new Range<>(30, 60);
                                    param.setResult(newRanges);
                                }
                            }
                        }
                    }
                });
                XposedBridge.log("[" + TAG + "] CameraCharacteristics framework hooks installed");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Framework CameraCharacteristics hook failed: " + t.getMessage());
        }

        // B. PowerManager thermal status bypass (AOSP / GCam)
        try {
            Class<?> powerClass = XposedHelpers.findClassIfExists("android.os.PowerManager", lpparam.classLoader);
            if (powerClass != null) {
                XposedBridge.hookAllMethods(powerClass, "getCurrentThermalStatus", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableDisableThermal) return;
                        param.setResult(0); // THERMAL_STATUS_NONE
                    }
                });
                XposedBridge.log("[" + TAG + "] PowerManager thermal bypass installed");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Framework PowerManager hook failed: " + t.getMessage());
        }

        // C. MediaRecorder bitrate for AOSP Camera2 / Open Camera
        try {
            Class<?> recorderClass = XposedHelpers.findClassIfExists("android.media.MediaRecorder", lpparam.classLoader);
            if (recorderClass != null) {
                XposedBridge.hookAllMethods(recorderClass, "setVideoEncodingBitRate", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableBitrate) return;
                        param.args[0] = 150000000; // 150 Mbps
                    }
                });
                XposedBridge.log("[" + TAG + "] MediaRecorder bitrate hook installed");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Framework MediaRecorder hook failed: " + t.getMessage());
        }

        // D. CamcorderProfile 4K 60FPS
        try {
            final Class<?> profileClass = XposedHelpers.findClassIfExists("android.media.CamcorderProfile", lpparam.classLoader);
            if (profileClass != null) {
                XposedBridge.hookAllMethods(profileClass, "hasProfile", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enable4k60) return;
                        if (param.args.length >= 2 && (Integer) param.args[1] == 8 /* QUALITY_2160P */) {
                            param.setResult(true);
                        }
                    }
                });
                XposedBridge.hookAllMethods(profileClass, "get", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.getThrowable() != null && enable4k60) {
                            try {
                                Method mGet = profileClass.getMethod("get", int.class, int.class);
                                Object fallback = mGet.invoke(null, param.args[0], 1 /* QUALITY_HIGH */);
                                param.setResult(fallback);
                            } catch (Throwable ignored) {}
                        }
                    }
                });
                XposedBridge.log("[" + TAG + "] CamcorderProfile 4K60 hook installed");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Framework CamcorderProfile hook failed: " + t.getMessage());
        }

        // E. StreamConfigurationMap 4K output size injection (enables 4K for front camera on AOSP/HAL)
        try {
            Class<?> mapClass = XposedHelpers.findClassIfExists("android.hardware.camera2.params.StreamConfigurationMap", lpparam.classLoader);
            if (mapClass != null) {
                XposedBridge.hookAllMethods(mapClass, "getOutputSizes", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enable4k60) return;
                        Object res = param.getResult();
                        if (res instanceof android.util.Size[]) {
                            android.util.Size[] sizes = (android.util.Size[]) res;
                            boolean has4k = false;
                            for (android.util.Size s : sizes) {
                                if (s.getWidth() == 3840 && s.getHeight() == 2160) {
                                    has4k = true;
                                    break;
                                }
                            }
                            if (!has4k && param.args.length > 0) {
                                Object arg = param.args[0];
                                boolean shouldInject = false;
                                if (arg instanceof Class) {
                                    Class<?> c = (Class<?>) arg;
                                    String cName = c.getName();
                                    if (cName.contains("MediaRecorder") || cName.contains("SurfaceTexture") || cName.contains("ImageReader")) {
                                        shouldInject = true;
                                    }
                                } else if (arg instanceof Integer) {
                                    int fmt = (Integer) arg;
                                    if (fmt == 34 /* PRIVATE */ || fmt == 35 /* YUV_420_888 */ || fmt == 256 /* JPEG */) {
                                        shouldInject = true;
                                    }
                                }
                                if (shouldInject) {
                                    android.util.Size[] newSizes = new android.util.Size[sizes.length + 1];
                                    newSizes[0] = new android.util.Size(3840, 2160);
                                    System.arraycopy(sizes, 0, newSizes, 1, sizes.length);
                                    param.setResult(newSizes);
                                }
                            }
                        }
                    }
                });
                XposedBridge.log("[" + TAG + "] StreamConfigurationMap 4K injection hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Hook StreamConfigurationMap failed: " + t.getMessage());
        }

        XposedBridge.log("[" + TAG + "] Framework-level universal hooks done");
    }

    // ==========================================
    // 10. GCam-Specific Hooks (MediaFormat / MediaCodec bitrate)
    // ==========================================
    private void hookGCamSpecific(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> formatClass = XposedHelpers.findClassIfExists("android.media.MediaFormat", lpparam.classLoader);
            if (formatClass != null) {
                XposedBridge.hookAllMethods(formatClass, "setInteger", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!enableBitrate) return;
                        if (param.args.length >= 2 && param.args[0] instanceof String) {
                            String name = (String) param.args[0];
                            if ("bitrate".equalsIgnoreCase(name) || "android._bitrate".equalsIgnoreCase(name)) {
                                int original = (Integer) param.args[1];
                                if (original > 5000000) { // Only override actual video bitrates (>5Mbps)
                                    param.args[1] = 150000000; // 150 Mbps
                                    XposedBridge.log("[" + TAG + "] GCam MediaFormat bitrate overridden: " + original + " -> 150Mbps");
                                }
                            }
                        }
                    }
                });
                XposedBridge.log("[" + TAG + "] GCam MediaFormat bitrate hook installed");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] GCam MediaFormat hook failed: " + t.getMessage());
        }
    }
}
