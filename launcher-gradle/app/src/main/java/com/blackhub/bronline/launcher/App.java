package com.blackhub.bronline.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import com.grandhorizonrp.launcher.GHRPLog;

import java.util.Locale;

/**
 * GHRP launcher Application — English language initialization (the engine
 * resolves "language" from serialized settings) + logging bootstrap.
 */
public final class App extends android.app.Application {
    private static final String[] SUPPORTED = {"en"};

    @Override
    public void onCreate() {
        super.onCreate();
        GHRPLog.init(this);
        GHRPLog.i("=== GHRP App.onCreate ===");

        initLanguageOnStartup();
    }

    /**
     * Exact original behaviour of UtilsKt.initLanguageOnStartup, narrowed to
     * the GHRP English build: detect the system locale, map it to a supported
     * language (default "en"), persist under the "language" key the engine
     * reads through the serialized settings.
     */
    private void initLanguageOnStartup() {
        try {
            SharedPreferences prefs = getSharedPreferences("launcher", Context.MODE_PRIVATE);
            String current = prefs.getString("language", null);
            if (current != null && isSupported(current)) {
                GHRPLog.i("language already set: " + current);
                return;
            }
            String detected = detectLanguage();
            prefs.edit().putString("language", detected).apply();
            GHRPLog.i("initLanguageOnStartup: detected=" + detected + " (previous=" + current + ")");
        } catch (Throwable t) {
            GHRPLog.e("initLanguageOnStartup failed", t);
        }
    }

    private boolean isSupported(String lang) {
        for (String s : SUPPORTED) {
            if (s.equals(lang)) return true;
        }
        return false;
    }

    private String detectLanguage() {
        try {
            String tag = Locale.getDefault().toLanguageTag();
            for (String supported : SUPPORTED) {
                if (tag.toLowerCase(Locale.US).startsWith(supported)) {
                    return supported;
                }
            }
        } catch (Throwable ignored) {
        }
        return "en";
    }
}
