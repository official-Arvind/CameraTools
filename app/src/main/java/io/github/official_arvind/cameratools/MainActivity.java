package io.github.official_arvind.cameratools;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {
    private SharedPreferences prefs;

    private Switch sw4k60;
    private Switch swBitrate;
    private Switch swRaw;
    private Switch swLeica;
    private Switch swDualVideo;
    private Switch swShutter;
    private Switch swThermal;
    private Switch swHighResPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context deCtx = isDeviceProtectedStorage() ? this : createDeviceProtectedStorageContext();
        try {
            prefs = deCtx.getSharedPreferences(PrefProvider.PREFS_NAME, Context.MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            prefs = deCtx.getSharedPreferences(PrefProvider.PREFS_NAME, Context.MODE_PRIVATE);
        }

        sw4k60 = findViewById(R.id.switch_4k60);
        swBitrate = findViewById(R.id.switch_bitrate);
        swRaw = findViewById(R.id.switch_raw);
        swLeica = findViewById(R.id.switch_leica);
        swDualVideo = findViewById(R.id.switch_dual_video);
        swShutter = findViewById(R.id.switch_shutter);
        swThermal = findViewById(R.id.switch_thermal);
        swHighResPhoto = findViewById(R.id.switch_high_res_photo);

        // Ensure defaults are saved if any key is missing
        SharedPreferences.Editor edInit = prefs.edit();
        boolean changed = false;
        if (!prefs.contains(PrefProvider.KEY_4K60)) { edInit.putBoolean(PrefProvider.KEY_4K60, false); changed = true; }
        if (!prefs.contains(PrefProvider.KEY_BITRATE)) { edInit.putBoolean(PrefProvider.KEY_BITRATE, false); changed = true; }
        if (!prefs.contains(PrefProvider.KEY_RAW)) { edInit.putBoolean(PrefProvider.KEY_RAW, false); changed = true; }
        if (!prefs.contains(PrefProvider.KEY_LEICA)) { edInit.putBoolean(PrefProvider.KEY_LEICA, false); changed = true; }
        if (!prefs.contains(PrefProvider.KEY_DUAL_VIDEO)) { edInit.putBoolean(PrefProvider.KEY_DUAL_VIDEO, false); changed = true; }
        if (!prefs.contains(PrefProvider.KEY_SHUTTER)) { edInit.putBoolean(PrefProvider.KEY_SHUTTER, false); changed = true; }
        if (!prefs.contains(PrefProvider.KEY_DISABLE_THERMAL)) { edInit.putBoolean(PrefProvider.KEY_DISABLE_THERMAL, false); changed = true; }
        if (!prefs.contains(PrefProvider.KEY_HIGH_RES_PHOTO)) { edInit.putBoolean(PrefProvider.KEY_HIGH_RES_PHOTO, false); changed = true; }
        if (changed) {
            edInit.commit();
            saveToSettings();
        }

        loadPreferences();
        setupListeners();

        final Button btnRestart = findViewById(R.id.btn_restart);
        btnRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                btnRestart.setEnabled(false);
                btnRestart.setText("Restarting...");
                restartCamera(btnRestart);
            }
        });

        final Button btnEnableAll = findViewById(R.id.btn_enable_all);
        updateToggleAllButtonText(btnEnableAll);
        btnEnableAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                boolean allActive = areAllEnabled();
                setAll(!allActive);
                updateToggleAllButtonText(btnEnableAll);
            }
        });
        saveToSettings();
    }

    private boolean areAllEnabled() {
        return sw4k60.isChecked() && swBitrate.isChecked() && swRaw.isChecked()
                && swLeica.isChecked() && swDualVideo.isChecked() && swShutter.isChecked()
                && swThermal.isChecked() && swHighResPhoto.isChecked();
    }

    private void updateToggleAllButtonText(Button btn) {
        if (btn != null) {
            btn.setText(areAllEnabled() ? "Disable All" : "Enable All");
        }
    }

    private void loadPreferences() {
        sw4k60.setChecked(prefs.getBoolean(PrefProvider.KEY_4K60, false));
        swBitrate.setChecked(prefs.getBoolean(PrefProvider.KEY_BITRATE, false));
        swRaw.setChecked(prefs.getBoolean(PrefProvider.KEY_RAW, false));
        swLeica.setChecked(prefs.getBoolean(PrefProvider.KEY_LEICA, false));
        swDualVideo.setChecked(prefs.getBoolean(PrefProvider.KEY_DUAL_VIDEO, false));
        swShutter.setChecked(prefs.getBoolean(PrefProvider.KEY_SHUTTER, false));
        swThermal.setChecked(prefs.getBoolean(PrefProvider.KEY_DISABLE_THERMAL, false));
        swHighResPhoto.setChecked(prefs.getBoolean(PrefProvider.KEY_HIGH_RES_PHOTO, false));
    }

    private void setupListeners() {
        final Button btnEnableAll = findViewById(R.id.btn_enable_all);
        CompoundButtonListener listener = new CompoundButtonListener(btnEnableAll);
        sw4k60.setOnCheckedChangeListener(listener);
        swBitrate.setOnCheckedChangeListener(listener);
        swRaw.setOnCheckedChangeListener(listener);
        swLeica.setOnCheckedChangeListener(listener);
        swDualVideo.setOnCheckedChangeListener(listener);
        swShutter.setOnCheckedChangeListener(listener);
        swThermal.setOnCheckedChangeListener(listener);
        swHighResPhoto.setOnCheckedChangeListener(listener);
    }

    private class CompoundButtonListener implements android.widget.CompoundButton.OnCheckedChangeListener {
        private final Button btnToggle;

        public CompoundButtonListener(Button btnToggle) {
            this.btnToggle = btnToggle;
        }

        @Override
        public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
            buttonView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            SharedPreferences.Editor ed = prefs.edit();
            int id = buttonView.getId();
            if (id == R.id.switch_4k60) ed.putBoolean(PrefProvider.KEY_4K60, isChecked);
            else if (id == R.id.switch_bitrate) ed.putBoolean(PrefProvider.KEY_BITRATE, isChecked);
            else if (id == R.id.switch_raw) ed.putBoolean(PrefProvider.KEY_RAW, isChecked);
            else if (id == R.id.switch_leica) ed.putBoolean(PrefProvider.KEY_LEICA, isChecked);
            else if (id == R.id.switch_dual_video) ed.putBoolean(PrefProvider.KEY_DUAL_VIDEO, isChecked);
            else if (id == R.id.switch_shutter) ed.putBoolean(PrefProvider.KEY_SHUTTER, isChecked);
            else if (id == R.id.switch_thermal) ed.putBoolean(PrefProvider.KEY_DISABLE_THERMAL, isChecked);
            else if (id == R.id.switch_high_res_photo) ed.putBoolean(PrefProvider.KEY_HIGH_RES_PHOTO, isChecked);
            ed.commit();
            saveToSettings();
            updateToggleAllButtonText(btnToggle);
            Toast.makeText(MainActivity.this, isChecked ? "Feature enabled - Restart Camera to apply" : "Feature disabled - Restart Camera to apply", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToSettings() {
        try {
            String cmd = "settings put global cameratools_4k60 " + (sw4k60.isChecked() ? "1" : "0") + " ; " +
                    "settings put global cameratools_bitrate " + (swBitrate.isChecked() ? "1" : "0") + " ; " +
                    "settings put global cameratools_raw " + (swRaw.isChecked() ? "1" : "0") + " ; " +
                    "settings put global cameratools_leica " + (swLeica.isChecked() ? "1" : "0") + " ; " +
                    "settings put global cameratools_dual " + (swDualVideo.isChecked() ? "1" : "0") + " ; " +
                    "settings put global cameratools_shutter " + (swShutter.isChecked() ? "1" : "0") + " ; " +
                    "settings put global cameratools_thermal " + (swThermal.isChecked() ? "1" : "0") + " ; " +
                    "settings put global cameratools_high_res " + (swHighResPhoto.isChecked() ? "1" : "0");
            Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        } catch (Throwable ignored) {}
    }

    private void setAll(boolean state) {
        sw4k60.setChecked(state);
        swBitrate.setChecked(state);
        swRaw.setChecked(state);
        swLeica.setChecked(state);
        swDualVideo.setChecked(state);
        swShutter.setChecked(state);
        swThermal.setChecked(state);
        swHighResPhoto.setChecked(state);
        prefs.edit()
            .putBoolean(PrefProvider.KEY_4K60, state)
            .putBoolean(PrefProvider.KEY_BITRATE, state)
            .putBoolean(PrefProvider.KEY_RAW, state)
            .putBoolean(PrefProvider.KEY_LEICA, state)
            .putBoolean(PrefProvider.KEY_DUAL_VIDEO, state)
            .putBoolean(PrefProvider.KEY_SHUTTER, state)
            .putBoolean(PrefProvider.KEY_DISABLE_THERMAL, state)
            .putBoolean(PrefProvider.KEY_HIGH_RES_PHOTO, state)
            .commit();
        saveToSettings();
        Toast.makeText(this, state ? "All features activated" : "All features deactivated", Toast.LENGTH_SHORT).show();
    }

    private void restartCamera(final Button btnRestart) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "am force-stop com.android.camera"});
                    p.waitFor();
                } catch (Exception e) {
                    try {
                        Runtime.getRuntime().exec("am force-stop com.android.camera");
                    } catch (Exception ignored) {}
                }
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (btnRestart != null) {
                            btnRestart.setEnabled(true);
                            btnRestart.setText(R.string.btn_restart_camera);
                        }
                        Toast.makeText(MainActivity.this, "Camera restarted!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }
}
