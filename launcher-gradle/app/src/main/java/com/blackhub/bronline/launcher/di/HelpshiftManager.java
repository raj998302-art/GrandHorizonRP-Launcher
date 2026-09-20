package com.blackhub.bronline.launcher.di;

/**
 * Engine contract class (HelpshiftManager.nativeInit is exported by
 * libblackrussia-client.so). The original used it to init the Helpshift SDK;
 * GHRP ships no Helpshift — the native declaration is kept so linkage parity
 * is preserved, but init() is intentionally not invoked by our launcher.
 */
public final class HelpshiftManager {
    private native void nativeInit();

    public void init() {
        // Intentionally not calling nativeInit(): no Helpshift in GHRP build.
        // Kept for JNI contract completeness.
    }
}
