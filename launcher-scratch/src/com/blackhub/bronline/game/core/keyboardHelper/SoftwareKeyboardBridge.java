package com.blackhub.bronline.game.core.keyboardHelper;

import com.grandhorizonrp.launcher.GHRPLog;

/**
 * Software keyboard bridge — engine contract (all static natives).
 */
public final class SoftwareKeyboardBridge {
    private SoftwareKeyboardBridge() {
    }

    private static native void nativeCommitText(String text, int a, int b);

    private static native void nativeOnKeyDown(int keyCode);

    private static native void nativeOnKeyUp(int keyCode);

    private static native void nativeOnKeyboardSizeChanged(int w, int h);

    private static native void nativeOnSetVisible(boolean visible);

    // ---- Java-side API used by the input host -------------------------

    public static void commitText(String text, int a, int b) {
        try {
            nativeCommitText(text, a, b);
        } catch (Throwable t) {
            GHRPLog.e("SoftwareKeyboardBridge.commitText failed", t);
        }
    }

    public static void onKeyDown(int keyCode) {
        try {
            nativeOnKeyDown(keyCode);
        } catch (Throwable t) {
            GHRPLog.e("SoftwareKeyboardBridge.onKeyDown failed", t);
        }
    }

    public static void onKeyUp(int keyCode) {
        try {
            nativeOnKeyUp(keyCode);
        } catch (Throwable t) {
            GHRPLog.e("SoftwareKeyboardBridge.onKeyUp failed", t);
        }
    }

    public static void onKeyboardSizeChanged(int w, int h) {
        try {
            nativeOnKeyboardSizeChanged(w, h);
        } catch (Throwable t) {
            GHRPLog.e("SoftwareKeyboardBridge.onKeyboardSizeChanged failed", t);
        }
    }

    public static void onSetVisible(boolean visible) {
        try {
            nativeOnSetVisible(visible);
        } catch (Throwable t) {
            GHRPLog.e("SoftwareKeyboardBridge.onSetVisible failed", t);
        }
    }
}
