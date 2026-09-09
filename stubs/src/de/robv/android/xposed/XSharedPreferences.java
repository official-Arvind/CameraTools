package de.robv.android.xposed;

import android.content.SharedPreferences;
import java.io.File;
import java.util.Map;
import java.util.Set;

public class XSharedPreferences implements SharedPreferences {
    public XSharedPreferences(String packageName) {}
    public XSharedPreferences(String packageName, String prefFileName) {}
    public XSharedPreferences(File prefFile) {}
    public boolean makeWorldReadable() { return true; }
    public void reload() {}
    public boolean hasFileChanged() { return false; }
    public Map<String, ?> getAll() { return null; }
    public String getString(String key, String defValue) { return defValue; }
    public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
    public int getInt(String key, int defValue) { return defValue; }
    public long getLong(String key, long defValue) { return defValue; }
    public float getFloat(String key, float defValue) { return defValue; }
    public boolean getBoolean(String key, boolean defValue) { return defValue; }
    public boolean contains(String key) { return false; }
    public Editor edit() { return null; }
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
}
