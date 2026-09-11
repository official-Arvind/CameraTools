package io.github.official_arvind.cameratools;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class PrefProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.official_arvind.cameratools.prefs";
    public static final String PREFS_NAME = "camera_tools_prefs";

    public static final String KEY_4K60 = "pref_4k60";
    public static final String KEY_BITRATE = "pref_bitrate";
    public static final String KEY_RAW = "pref_raw";
    public static final String KEY_LEICA = "pref_leica";
    public static final String KEY_DUAL_VIDEO = "pref_dual_video";
    public static final String KEY_SHUTTER = "pref_shutter";
    public static final String KEY_DISABLE_THERMAL = "pref_disable_thermal";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context ctx = getContext();
        if (ctx == null) return null;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if ("get_all".equals(method)) {
            Bundle b = new Bundle();
            b.putBoolean(KEY_4K60, sp.getBoolean(KEY_4K60, true));
            b.putBoolean(KEY_BITRATE, sp.getBoolean(KEY_BITRATE, true));
            b.putBoolean(KEY_RAW, sp.getBoolean(KEY_RAW, true));
            b.putBoolean(KEY_LEICA, sp.getBoolean(KEY_LEICA, true));
            b.putBoolean(KEY_DUAL_VIDEO, sp.getBoolean(KEY_DUAL_VIDEO, true));
            b.putBoolean(KEY_SHUTTER, sp.getBoolean(KEY_SHUTTER, true));
            b.putBoolean(KEY_DISABLE_THERMAL, sp.getBoolean(KEY_DISABLE_THERMAL, true));
            return b;
        } else if ("get_boolean".equals(method)) {
            Bundle b = new Bundle();
            b.putBoolean("value", sp.getBoolean(arg, true));
            return b;
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.io.github.official_arvind.cameratools.pref";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
