package com.grandhorizonrp.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * GHRP launcher Application — English language initialization + logging bootstrap.
 */
public final class GHRPApp extends android.app.Application {
    private static final String[] SUPPORTED = {"en"};

    @Override
    public void onCreate() {
        super.onCreate();
        GHRPLog.init(this);
        GHRPLog.i("=== GHRP App.onCreate (engine-from-scratch build) ===");
        initLanguageOnStartup();
    }

    private void initLanguageOnStartup() {
        try {
            SharedPreferences prefs = getSharedPreferences("launcher", Context.MODE_PRIVATE);
            String current = prefs.getString("language", null);
            if (current != null && isSupported(current)) return;
            prefs.edit().putString("language", "en").apply();
        } catch (Throwable t) {
            GHRPLog.e("initLanguageOnStartup failed", t);
        }
    }

    private boolean isSupported(String lang) {
        for (String s : SUPPORTED) if (s.equals(lang)) return true;
        return false;
    }
}
