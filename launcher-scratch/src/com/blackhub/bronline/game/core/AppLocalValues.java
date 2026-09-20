package com.blackhub.bronline.game.core;

import java.util.HashMap;

/**
 * Engine-facing local values store. The native engine resolves this class via
 * FindClass("com/blackhub/bronline/game/core/AppLocalValues") and calls
 * getInstance()/hasAppLocalValue/getAppLocalValue/setAppLocalValue.
 * JNIActivity seeds STORAGE_ROOT here before engine init.
 */
public final class AppLocalValues {
    public static final AppLocalValues instance = new AppLocalValues();

    private final HashMap<String, String> mAppLocalValues = new HashMap<>();

    private AppLocalValues() {
    }

    public static AppLocalValues getInstance() {
        return instance;
    }

    /** Kotlin Companion mirror (engine may resolve either entry point). */
    public static AppLocalValues app_siteRelease() {
        return instance;
    }

    public boolean hasAppLocalValue(String key) {
        return mAppLocalValues.containsKey(key);
    }

    public String getAppLocalValue(String key) {
        return mAppLocalValues.get(key);
    }

    public void setAppLocalValue(String key, String value) {
        if (key == null) return;
        if (value == null) mAppLocalValues.remove(key);
        else mAppLocalValues.put(key, value);
    }

    /** Helper for Java-side seeding. */
    public static void put(String key, String value) {
        instance.setAppLocalValue(key, value);
    }

    public static final class Companion {
        public AppLocalValues getInstance() {
            return instance;
        }
    }
}
